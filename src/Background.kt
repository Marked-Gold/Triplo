import korlibs.image.color.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import korlibs.time.*
import kotlin.math.*
import kotlin.random.Random

// --- Low-poly triangle background --------------------------------------------
//
// The board sits on a procedurally generated low-poly field: a jittered point
// grid split into ~270 triangles of varied size and orientation. Each triangle
// is drawn once as a plain white shape; its real colour is applied through
// colorMul, so it can be re-tinted every frame for free (no shape rebuilds).
//
// Idle, a slow brightness shimmer sways across the field. When a high-tier block
// (81+) is forged, triggerBackgroundPulse lights a colour wave that propagates
// facet-to-facet through the mesh from the triangle the block landed on: the
// wave spreads along triangle adjacency (Dijkstra over shared edges), so it
// genuinely travels through the triangles rather than sweeping the screen. The
// colour also weakens the further it travels, so the edges glow only faintly.

private class Tri(
    val view: View,
    val baseColor: RGBA,
    val cx: Double,
    val cy: Double,
    val shimmerPhase: Double,
    val vertexIds: IntArray,
) {
    // Indices (into `tris`) of the triangles sharing an edge with this one.
    val neighbors = mutableListOf<Int>()
    // Graph distance from the current pulse's origin facet, in pixels.
    var pulseDist = 0.0
}

private val tris = mutableListOf<Tri>()
private var elapsed = 0.0

private var pulseActive = false
private var pulseStart = 0.0
private var pulseColor = Colors.WHITE
private var pulseMaxDist = 0.0

private const val waveSpeed = 650.0    // px/sec the colour wave travels through the mesh
private const val pulseRise = 0.32     // sec a facet takes to reach full colour
private const val pulseFall = 1.0      // sec it takes to settle back afterward
private const val pulseStrength = 0.9  // how far toward the pulse colour a facet goes
private const val edgeFadeMin = 0.45   // colour strength left once the wave reaches the edge
private const val shimmerAmp = 0.05    // ambient brightness sway (fraction)

// Vertical gradient the facet base colours are sampled from: a calm dusty blue
// at the top easing through warm cream into a soft terracotta at the bottom.
private val gradTop = RGBA(150, 178, 196)
private val gradMid = RGBA(244, 230, 212)
private val gradBot = RGBA(230, 165, 138)

private fun RGBA.scaledRGB(f: Double): RGBA =
    RGBA(
        (r * f).roundToInt().coerceIn(0, 255),
        (g * f).roundToInt().coerceIn(0, 255),
        (b * f).roundToInt().coerceIn(0, 255),
        255,
    )

private fun lerpColor(a: RGBA, b: RGBA, t: Double): RGBA {
    val u = t.coerceIn(0.0, 1.0)
    return RGBA(
        (a.r + (b.r - a.r) * u).roundToInt(),
        (a.g + (b.g - a.g) * u).roundToInt(),
        (a.b + (b.b - a.b) * u).roundToInt(),
        255,
    )
}

private fun baseColorAt(ny: Double, rng: Random): RGBA {
    val g =
        if (ny < 0.5) lerpColor(gradTop, gradMid, ny * 2.0)
        else lerpColor(gradMid, gradBot, (ny - 0.5) * 2.0)
    // Per-facet brightness jitter is what gives the low-poly faceted look.
    return g.scaledRGB(1.0 + (rng.nextDouble() * 2.0 - 1.0) * 0.14)
}

private fun pulseEnvelope(lp: Double): Double =
    when {
        lp <= 0.0 -> 0.0
        lp < pulseRise -> lp / pulseRise
        lp < pulseRise + pulseFall -> 1.0 - (lp - pulseRise) / pulseFall
        else -> 0.0
    }

fun Stage.setupBackground() {
    val vw = views.virtualWidth.toDouble()
    val vh = views.virtualHeight.toDouble()

    // Cover 2x the virtual area, centred, so the letterbox margins outside the
    // 360x640 virtual area are filled on every phone aspect ratio (issue 5).
    val fieldW = vw * 2.0
    val fieldH = vh * 2.0
    val originX = (vw - fieldW) / 2.0
    val originY = (vh - fieldH) / 2.0

    val cols = 9
    val rows = 15
    val cellW = fieldW / cols
    val cellH = fieldH / rows
    val rng = Random(20260517L)

    // Jittered point grid. Interior points are nudged so the triangles vary in
    // size and shape; edge points stay put so the field border has no gaps.
    val pts = Array(rows + 1) { r ->
        Array(cols + 1) { c ->
            val edge = r == 0 || c == 0 || r == rows || c == cols
            val jx = if (edge) 0.0 else (rng.nextDouble() * 2.0 - 1.0) * cellW * 0.40
            val jy = if (edge) 0.0 else (rng.nextDouble() * 2.0 - 1.0) * cellH * 0.40
            Point(originX + c * cellW + jx, originY + r * cellH + jy)
        }
    }

    val triLayer = container { }
    val vStride = cols + 1
    fun vertexPoint(id: Int): Point = pts[id / vStride][id % vStride]

    fun addTri(i0: Int, i1: Int, i2: Int) {
        val a = vertexPoint(i0)
        val b = vertexPoint(i1)
        val c = vertexPoint(i2)
        val gx = (a.x + b.x + c.x) / 3.0
        val gy = (a.y + b.y + c.y) / 3.0
        // Inflate each vertex ~1px outward from the centroid so neighbouring
        // triangles overlap a hair and anti-aliased edges leave no seams.
        fun push(p: Point): Point {
            val dx = p.x - gx
            val dy = p.y - gy
            val len = hypot(dx, dy).coerceAtLeast(0.0001)
            val f = (len + 1.0) / len
            return Point(gx + dx * f, gy + dy * f)
        }
        val pa = push(a)
        val pb = push(b)
        val pc = push(c)
        val minX = minOf(pa.x, pb.x, pc.x).toDouble()
        val minY = minOf(pa.y, pb.y, pc.y).toDouble()
        // Drawn white in local coords; the colour lives in colorMul so it can be
        // tweened every frame without rebuilding the shape.
        val view =
            triLayer.graphics {
                fill(Colors.WHITE) {
                    moveTo(Point(pa.x - minX, pa.y - minY))
                    lineTo(Point(pb.x - minX, pb.y - minY))
                    lineTo(Point(pc.x - minX, pc.y - minY))
                    close()
                }
            }.xy(minX, minY)

        val ny = ((gy - originY) / fieldH).coerceIn(0.0, 1.0)
        val base = baseColorAt(ny, rng)
        view.colorMul = base
        tris += Tri(view, base, gx, gy, rng.nextDouble() * PI * 2.0, intArrayOf(i0, i1, i2))
    }

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val tl = r * vStride + c
            val tr = r * vStride + c + 1
            val bl = (r + 1) * vStride + c
            val br = (r + 1) * vStride + c + 1
            // Alternate the split diagonal so facet orientation varies too.
            if ((r + c) % 2 == 0) {
                addTri(tl, tr, br)
                addTri(tl, br, bl)
            } else {
                addTri(tl, tr, bl)
                addTri(tr, br, bl)
            }
        }
    }

    buildAdjacency()

    addUpdater { dt ->
        elapsed += dt.seconds
        val pulseT = elapsed - pulseStart
        if (pulseActive && pulseT > pulseMaxDist / waveSpeed + pulseRise + pulseFall) {
            pulseActive = false
        }
        for (t in tris) {
            // Slow ambient shimmer: a gentle brightness sway across the field.
            val shimmer = 1.0 + sin(elapsed * 0.5 + t.shimmerPhase) * shimmerAmp
            var color = t.baseColor.scaledRGB(shimmer)
            if (pulseActive) {
                val env = pulseEnvelope(pulseT - t.pulseDist / waveSpeed)
                if (env > 0.0) {
                    // The wave loses colour the further it has travelled, so the
                    // facets near the merge glow brightest and the edges only faintly.
                    val travelled = if (pulseMaxDist > 0.0) t.pulseDist / pulseMaxDist else 0.0
                    val fade = 1.0 - travelled * (1.0 - edgeFadeMin)
                    color = lerpColor(color, pulseColor, env * pulseStrength * fade)
                }
            }
            t.view.colorMul = color
        }
    }
}

// Links every triangle to the ones it shares an edge with. An edge belongs to
// exactly two triangles inside the mesh and one on the border; the interior
// edges are what the colour wave hops across.
private fun buildAdjacency() {
    val edgeMap = mutableMapOf<Long, MutableList<Int>>()
    fun edgeKey(a: Int, b: Int): Long {
        val lo = minOf(a, b).toLong()
        val hi = maxOf(a, b).toLong()
        return lo * 100_000L + hi
    }
    tris.forEachIndexed { ti, t ->
        val v = t.vertexIds
        for (e in 0 until 3) {
            edgeMap.getOrPut(edgeKey(v[e], v[(e + 1) % 3])) { mutableListOf() }.add(ti)
        }
    }
    for (sharing in edgeMap.values) {
        if (sharing.size == 2) {
            val x = sharing[0]
            val y = sharing[1]
            tris[x].neighbors.add(y)
            tris[y].neighbors.add(x)
        }
    }
}

// Called when a merge forges an 81-tier (or higher) block. Lights a colour wave
// that travels facet-to-facet through the mesh, starting from the triangle
// nearest (centerX, centerY) — the spot the block was made.
fun triggerBackgroundPulse(color: RGBA, centerX: Double, centerY: Double) {
    if (tris.isEmpty()) return
    pulseColor = color
    pulseStart = elapsed
    pulseActive = true

    // The wave's origin facet: the triangle closest to where the block landed.
    var source = 0
    var bestDist = Double.MAX_VALUE
    tris.forEachIndexed { i, t ->
        val d = hypot(t.cx - centerX, t.cy - centerY)
        if (d < bestDist) {
            bestDist = d
            source = i
        }
    }

    // Dijkstra over the facet adjacency graph (edge weight = centroid spacing)
    // gives each triangle its distance along the mesh from the origin facet.
    val n = tris.size
    val dist = DoubleArray(n) { Double.MAX_VALUE }
    val settled = BooleanArray(n)
    dist[source] = 0.0
    for (step in 0 until n) {
        var u = -1
        var ud = Double.MAX_VALUE
        for (i in 0 until n) {
            if (!settled[i] && dist[i] < ud) {
                ud = dist[i]
                u = i
            }
        }
        if (u < 0) break
        settled[u] = true
        val tu = tris[u]
        for (v in tu.neighbors) {
            if (settled[v]) continue
            val w = hypot(tu.cx - tris[v].cx, tu.cy - tris[v].cy)
            if (dist[u] + w < dist[v]) dist[v] = dist[u] + w
        }
    }

    var maxDist = 0.0
    for (i in 0 until n) {
        val d = if (dist[i] == Double.MAX_VALUE) 0.0 else dist[i]
        tris[i].pulseDist = d
        if (d > maxDist) maxDist = d
    }
    pulseMaxDist = maxDist
}
