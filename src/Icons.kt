import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector-drawn UI icons (pause, restart, share).
 *
 * Every icon is built from KorGE vector shapes so it stays crisp at any resolution and shares a
 * single, consistent visual language (flat fills, rounded ends, no external image assets).
 *
 * Each builder returns a [Container] sized exactly `s x s` (an invisible bounds rect keeps the box
 * square) so callers can `centerOn` / `align*` it like a plain image.
 */

private val iconInk = Colors["#12291b"]

private fun Container.iconBox(
    s: Double,
    draw: Container.() -> Unit,
): Container =
    container {
        // Transparent rect pins the container bounds to a square s x s box.
        solidRect(s, s, RGBA(0, 0, 0, 0))
        draw()
    }

/** Pause / menu icon — two rounded vertical bars. */
fun Container.pauseIcon(
    s: Double,
    color: RGBA = iconInk,
): Container =
    iconBox(s) {
        graphics {
            fill(color) {
                val barW = s * 0.24
                val barH = s * 0.72
                val top = (s - barH) / 2.0
                val gap = s * 0.16
                val r = barW * 0.5
                roundRect(s / 2.0 - gap / 2.0 - barW, top, barW, barH, r, r)
                roundRect(s / 2.0 + gap / 2.0, top, barW, barH, r, r)
            }
        }
    }

/** Restart icon — a circular arrow (open ring with an arrowhead). */
fun Container.restartIcon(
    s: Double,
    color: RGBA = iconInk,
): Container =
    iconBox(s) {
        graphics {
            val cx = s / 2.0
            val cy = s / 2.0
            val r = s * 0.30
            val lw = s * 0.155
            // Ring sweeping clockwise, leaving a gap at the top for the arrowhead.
            stroke(color, lineWidth = lw, lineCap = LineCap.ROUND) {
                arc(Point(cx, cy), r, (-58).degrees, 232.degrees, false)
            }
            // Arrowhead at the end of the arc, pointing along the direction of travel.
            val a = 232.0 * PI / 180.0
            val ex = cx + r * cos(a)
            val ey = cy + r * sin(a)
            val tx = -sin(a) // clockwise tangent
            val ty = cos(a)
            val px = -ty // perpendicular
            val py = tx
            val hl = s * 0.21
            val hw = s * 0.16
            fill(color) {
                moveTo(ex + tx * hl, ey + ty * hl)
                lineTo(ex - tx * hl * 0.5 + px * hw, ey - ty * hl * 0.5 + py * hw)
                lineTo(ex - tx * hl * 0.5 - px * hw, ey - ty * hl * 0.5 - py * hw)
                close()
            }
        }
    }

/** Share icon — three connected nodes. */
fun Container.shareIcon(
    s: Double,
    color: RGBA = iconInk,
): Container =
    iconBox(s) {
        graphics {
            val dotR = s * 0.15
            val left = Point(s * 0.27, s * 0.50)
            val topRight = Point(s * 0.73, s * 0.23)
            val botRight = Point(s * 0.73, s * 0.77)
            stroke(color, lineWidth = s * 0.085, lineCap = LineCap.ROUND) {
                moveTo(left)
                lineTo(topRight)
                moveTo(left)
                lineTo(botRight)
            }
            fill(color) {
                circle(left, dotR)
                circle(topRight, dotR)
                circle(botRight, dotR)
            }
        }
    }

