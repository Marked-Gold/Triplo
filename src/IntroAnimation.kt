import korlibs.audio.sound.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.image.text.TextAlignment
import korlibs.io.file.std.*
import korlibs.korge.animate.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.math.interpolation.*
import korlibs.time.*
import kotlinx.coroutines.delay

/**
 * Three-second studio intro played once at boot. A meat-textured A drops in,
 * triplicates to three vertically-aligned A's, then the rest of the letters
 * fade in to spell ALL / MEAT / GAMES while a "moo" plays. The function
 * suspends until the animation completes; the game has not been drawn yet,
 * so the screen below the intro is just the Korge background colour. The
 * intro is not skippable: nothing on the screen reacts to input.
 */
suspend fun Stage.playIntroAnimation() {
    val meatBitmap = resourcesVfs["meat-A.png"].readBitmap()
    val backgroundBitmap = resourcesVfs["background.png"].readBitmap()
    val moo = resourcesVfs["moo.wav"].readSound()
    // Intro-only display font. The game UI keeps clear_sans (a bitmap font);
    // the studio splash gets a chunkier TTF loaded just for this animation.
    val introFont = resourcesVfs["Slackey-Regular.ttf"].readTtfFont()

    val vw = views.virtualWidth.toDouble()
    val vh = views.virtualHeight.toDouble()

    // Sit on top of everything. main() builds the game *after* this returns,
    // so right now the stage is empty; the bare Korge background colour
    // (RGBA(253, 247, 240)) fills any letterboxed area around the intro.
    val root = container { }

    // Studio backdrop: scale background.png to cover the full visible screen
    // (actualVirtualBounds, which extends past the 360x640 virtual area under
    // CENTER_NO_CLIP). "Cover" semantics — fill the screen, crop overflow —
    // matches what setupBackground() does for the game and avoids letterboxing.
    val screen = views.actualVirtualBounds
    val coverScale = maxOf(
        screen.width / backgroundBitmap.width.toDouble(),
        screen.height / backgroundBitmap.height.toDouble(),
    )
    root.image(backgroundBitmap, Anchor2D.MIDDLE_CENTER) {
        scale = coverScale
        xy(screen.left + screen.width / 2.0, screen.top + screen.height / 2.0)
    }

    // Sizing: letterSize sets the text height; meatSize sets the rendered
    // meat-A height (taller than the text so the A's pop as the focal glyph).
    // Both rows use the same vertical span, driven by the larger of the two.
    val letterSize = vw * 0.12
    val meatSize = letterSize * 1.5
    val rowGap = letterSize * 0.30
    val rowHeight = meatSize + rowGap

    val cx = vw / 2
    val cyMiddle = vh / 2

    val yALL = cyMiddle - rowHeight
    val yMEAT = cyMiddle
    val yGAMES = cyMiddle + rowHeight

    // Scale the bitmap so the rendered A is `meatSize` tall.
    val meatScale = meatSize / meatBitmap.height.toDouble()
    val meatHalfWidth = meatBitmap.width * meatScale / 2
    // The meat-A.png has transparent padding around the actual glyph, so the
    // image edge is wider than the visible A. Pull the adjacent letters in
    // toward the glyph silhouette so the words read as one tight title.
    val aGlyphHalfWidth = meatHalfWidth * 0.78

    // White fill stamped over an 8-direction near-black purple border so the
    // letters read like outlined block lettering rather than a flat colour.
    val fillColor = Colors.WHITE
    val borderColor = Colors["#0e041a"]

    fun Container.placeA(): Image =
        image(meatBitmap, Anchor2D.MIDDLE_CENTER) {
            scale = meatScale
            xy(cx, yMEAT)
        }

    // The middle A flies in along the z-axis: it starts much larger and
    // shrinks to its resting size, as if dropping toward the screen and
    // settling. The top and bottom A's start hidden under the middle A and
    // slide out during the triplicate step.
    val aMid = root.placeA()
    val aTop = root.placeA().also { it.visible = false }
    val aBot = root.placeA().also { it.visible = false }

    // Non-A letters per row. Prefixes sit to the left of the A and suffixes
    // to the right. They start fully transparent and fade in during phase 3.
    // The text size is bumped above the A height so the capital cap-height
    // (roughly 70% of line height) matches the rendered meat-A height — the
    // meat A glyph fills its bitmap, while text glyphs leave room for descenders.
    //
    // clear_sans.fnt is single-weight, so the outlined-block look comes from
    // stamping the string in the dark border colour at eight surrounding offsets
    // (4 cardinal + 4 diagonal) and once in the light fill colour at centre.
    // The border halo extends `borderOffset` past the fill on every side, which
    // the position math compensates for so the halo (not the fill) is what
    // butts up against the A.
    val textSize = letterSize * 1.4
    val borderOffset = textSize / 45.0
    val borderOffsets = listOf(
        -borderOffset to 0.0,
        borderOffset to 0.0,
        0.0 to -borderOffset,
        0.0 to borderOffset,
        -borderOffset to -borderOffset,
        borderOffset to -borderOffset,
        -borderOffset to borderOffset,
        borderOffset to borderOffset,
    )
    fun Container.placeLetters(value: String, rowCy: Double, putToLeftOfA: Boolean): Container {
        return container {
            alpha = 0.0
            for ((dx, dy) in borderOffsets) {
                text(value, textSize, borderColor, introFont) {
                    alignment = TextAlignment.TOP_LEFT
                    x = dx
                    y = dy
                }
            }
            val fillT = text(value, textSize, fillColor, introFont) {
                alignment = TextAlignment.TOP_LEFT
            }
            val kern = letterSize * 0.04
            y = rowCy - fillT.height / 2
            x = if (putToLeftOfA) cx - aGlyphHalfWidth - kern - fillT.width - borderOffset
                else cx + aGlyphHalfWidth + kern + borderOffset
        }
    }

    val allSuffix = root.placeLetters("LL", yALL, putToLeftOfA = false)
    val meatPrefix = root.placeLetters("ME", yMEAT, putToLeftOfA = true)
    val meatSuffix = root.placeLetters("T", yMEAT, putToLeftOfA = false)
    val gamesPrefix = root.placeLetters("G", yGAMES, putToLeftOfA = true)
    val gamesSuffix = root.placeLetters("MES", yGAMES, putToLeftOfA = false)

    // --- Phase 1: meat A flies in along the z-axis — starts large and shrinks
    // to its resting size, as though falling toward the screen (~0.7s).
    val dropScale = meatScale * 4.0
    aMid.scale = dropScale
    aMid.alpha = 0.0
    animate {
        parallel {
            tween(aMid::scaleX[meatScale], time = 0.7.seconds, easing = Easing.EASE_IN_QUAD)
            tween(aMid::scaleY[meatScale], time = 0.7.seconds, easing = Easing.EASE_IN_QUAD)
            alpha(aMid, 1.0, 0.25.seconds, Easing.EASE_OUT)
        }
    }.awaitComplete()

    // Moo fires at the moment of impact, in sync with the landing squash below.
    moo.play(coroutineContext, PlaybackParameters.DEFAULT)

    // Quick squash-and-settle so the landing has some weight (~0.2s).
    animate {
        sequence {
            parallel {
                tween(aMid::scaleX[meatScale * 1.1], time = 0.08.seconds, easing = Easing.EASE_OUT)
                tween(aMid::scaleY[meatScale * 0.85], time = 0.08.seconds, easing = Easing.EASE_OUT)
            }
            parallel {
                tween(aMid::scaleX[meatScale], time = 0.12.seconds, easing = Easing.EASE_OUT)
                tween(aMid::scaleY[meatScale], time = 0.12.seconds, easing = Easing.EASE_OUT)
            }
        }
    }.awaitComplete()

    // --- Phase 2: triplicate — slide a copy up and a copy down (~0.5s) ---------
    aTop.visible = true
    aBot.visible = true
    animate {
        parallel {
            tween(aTop::y[yALL], time = 0.5.seconds, easing = Easing.EASE_OUT)
            tween(aBot::y[yGAMES], time = 0.5.seconds, easing = Easing.EASE_OUT)
        }
    }.awaitComplete()

    // --- Phase 3: fade in remaining letters (~0.8s) ----------------------------
    animate {
        parallel {
            for (t in listOf(allSuffix, meatPrefix, meatSuffix, gamesPrefix, gamesSuffix)) {
                alpha(t, 1.0, 0.8.seconds, Easing.EASE_OUT)
            }
        }
    }.awaitComplete()

    // --- Phase 4: hold the studio name, then fade everything out (~0.8s) -------
    delay(400L)
    animate {
        alpha(root, 0.0, 0.4.seconds, Easing.EASE_IN)
    }.awaitComplete()

    root.removeFromParent()
}
