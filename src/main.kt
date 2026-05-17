import korlibs.korge.*
import korlibs.korge.input.*
import korlibs.korge.service.storage.storage
import korlibs.korge.view.*
import korlibs.korge.view.align.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.bitmap.*
import korlibs.image.format.*
import korlibs.image.text.TextAlignment
import korlibs.io.async.ObservableProperty
import korlibs.io.async.launchImmediately
import korlibs.io.file.std.*
import kotlinx.coroutines.delay
import korlibs.time.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import kotlin.properties.Delegates
import kotlin.random.Random

val score = ObservableProperty(0)
val best = ObservableProperty(0)

var gridColumns: Int = 7
var gridRows: Int = 7

val random27ID = Random.nextInt(0, gridRows * gridColumns - 1)

var cellIndentSize: Int = 8
var cellSize: Int = 0
var fieldWidth: Int = 0
var fieldHeight: Int = 0
var leftIndent: Int = 0
var topIndent: Int = 0
var nextBlockId = 0

var fieldSize: Double = 0.0

var isPressed = false
var isBombPressed = false

var font: BitmapFont by Delegates.notNull()

var blocksMap: MutableMap<Position, Block> = mutableMapOf()

var hoveredPositions: MutableList<Position> = mutableListOf()
var hoveredBombPositions: MutableList<Position> = mutableListOf()

var isAnimating: Boolean = false

fun startAnimating() {
    isAnimating = true
}

fun stopAnimating() {
    isAnimating = false
}

// Idle hint: when the board has no available moves but the player still holds a
// bomb or rocket, the held power-up(s) jiggle after idleHintDelay seconds without
// a move to signal that a power-up must be used to keep playing. The timer resets
// on every board touch and re-fires every idleHintDelay seconds while still stuck.
const val idleHintDelay = 6.0
var idleTime = 0.0
var nextIdleHintTime = idleHintDelay

fun resetIdleTimer() {
    idleTime = 0.0
    nextIdleHintTime = idleHintDelay
}

var showingRestart: Boolean = false
var restartPopupContainer: Container = Container()

const val startingBombCount = 1
const val maxBombCount = 5
var bombsLoadedCount = ObservableProperty(startingBombCount)
var bombSelected = false
var bombContainer: Container = Container()

const val startingRocketCount = 1
const val maxRocketCount = 5
const val rocketPowerUpLength = 8
var rocketsLoadedCount = ObservableProperty(startingRocketCount)
var rocketSelection = RocketSelection()
var rocketContainer: Container = Container()

var bombScaleNormal = 0.0
var bombScaleSelected = 0.0
var rocketScaleNormal = 0.0
var rocketScaleSelected = 0.0

var blockScaleNormal = 0.0
var blockScaleSelected = 0.0

var gameField = RoundRect(Size(0, 0), RectCorners(0))

const val smallSelectionSize = 3
const val mediumSelectionSize = 6
const val largeSelectionSize = 18

// One inventory cartridge slot: a white-bordered rounded rect rendered with
// fastRoundRect. fastRoundRect is a GPU SDF shape, so it stays crisp at any
// scale or sub-pixel offset, unlike roundRect which rasterizes to a texture
// that blurs when the scene is upscaled to the device's native resolution.
// Note: fastRoundRect corners are a ratio of the size (0..1), not pixels.
fun Container.cartridgeSlot(fill: RGBA): Container =
    container {
        val w = cellSize * 0.4
        val h = cellSize * 0.4
        val border = cellSize / 24.0
        val cornerRatio = 0.3
        fastRoundRect(Size(w, h), RectCorners(cornerRatio), color = cartridgeBorderColor)
        fastRoundRect(Size(w - border * 2, h - border * 2), RectCorners(cornerRatio), color = fill) {
            xy(border, border)
        }
    }

suspend fun main() =
    Korge(
        windowSize = Size(360, 640),
        title = "Trillium",
        backgroundColor = RGBA(253, 247, 240),
        // CENTER_NO_CLIP keeps the game letterboxed but does not clip the borders, so the
        // background can bleed past the virtual area to fill any phone aspect ratio (issue 5).
        displayMode = KorgeDisplayMode.CENTER_NO_CLIP,
    ) {
        Napier.base(DebugAntilog())

        // Wire up interstitial ads (real on Android, no-op elsewhere) and start preloading one.
        views.installPlatformAds()

        // Triangle art plus slowly drifting glow orbs; also wires up the colour
        // wash that fires when a high-tier block is forged. See Background.kt.
        setupBackground()

        val storage = views.storage
        best.update(storage.getOrNull("best")?.toInt() ?: 0)

        score.observe {
            if (it > best.value) best.update(it)
        }
        best.observe {
            storage["best"] = it.toString()
        }

        font = resourcesVfs["clear_sans.fnt"].readBitmapFont()

        cellSize = views.virtualWidth / (gridColumns + 2)
        Napier.d("Cell size = $cellSize")
        fieldWidth = (cellIndentSize * (gridColumns + 1)) + gridColumns * cellSize
        Napier.d("Field width = $fieldWidth")
        fieldHeight = (cellIndentSize * (gridRows + 1)) + gridRows * cellSize
        Napier.d("Field height = $fieldHeight")
        leftIndent = (views.virtualWidth - fieldWidth) / 2
        Napier.d("Left indent = $leftIndent")
        topIndent = 128

        // The grid is fixed in place; the score row and the power-up row are centred in
        // the gaps above and below it. CENTER_NO_CLIP lets the visible screen extend past
        // the 360x640 virtual area, so use actualVirtualBounds (the real screen, expressed
        // in virtual coordinates) rather than the virtual height to find the screen edges.
        val screenTop = views.actualVirtualBounds.top
        val screenBottom = views.actualVirtualBounds.bottom
        val fieldBottom = topIndent + fieldHeight

        // Score row: the pause button, SCORE and BEST boxes share a vertical centre.
        // The boxes (cellSize * 1.5 tall) are the tallest element and set the row height.
        val scoreRowCenterY = (screenTop + topIndent) / 2.0
        val scoreRowTop = scoreRowCenterY - (cellSize * 1.5) / 2.0

        // Power-up row: bomb/rocket containers (cellSize * 2.5 tall) stacked above their
        // cartridge strip (cellSize * 0.5 tall) with a 2px gap between the two.
        val powerUpRowHeight = cellSize * 3.0 + 2.0
        val powerUpRowTop = (fieldBottom + screenBottom) / 2.0 - powerUpRowHeight / 2.0

        gameField =
            roundRect(Size(fieldWidth, fieldHeight), RectCorners(5), fill = gameFieldColor) {
                this.position(leftIndent, topIndent)

                touch {
                    start { handleDown(it.global) }
                    move { handleHover(it.global) }
                }
            }

        graphics {
            fill(cellColor) {
                for (i in 0 until gridColumns) {
                    for (j in 0 until gridRows) {
                        roundRect(
                            (cellIndentSize + (cellIndentSize + cellSize) * i).toDouble(),
                            (cellIndentSize + (cellIndentSize + cellSize) * j).toDouble(),
                            cellSize.toDouble(),
                            cellSize.toDouble(),
                            5.0,
                        )
                    }
                }
            }
        }.xy(leftIndent, topIndent)

        val btnSize = cellSize * 1.0
        val restartBlock =
            container {
                val backgroundBlock = roundRect(Size(btnSize, btnSize), RectCorners(5), fill = restartAndScoreColor)
                pauseIcon(btnSize * 0.6).centerOn(backgroundBlock)
                alignLeftToLeftOf(gameField, cellSize * 0.5)
                // Centre the (shorter) pause button on the score row's vertical centre.
                y = scoreRowCenterY - btnSize / 2.0
                onClick {
                    if (!tutorialAllowsPause()) return@onClick
                    if (!showingRestart) {
                        unselectAllPowerUps()
                        restartPopupContainer = this@Korge.showRestart {
                            // Show an interstitial when the player restarts from the pause menu.
                            this@Korge.launchImmediately {
                                Ads.showInterstitial()
                                this@Korge.restart()
                            }
                        }
                        Napier.d("Restart Button Clicked")
                    } else
                        {
                            Napier.d("Restart Button Clicked when already showing restart")
                            showingRestart = false
                            restartPopupContainer.removeFromParent()
                        }
                }
            }

        val bgScore =
            roundRect(Size(cellSize * 2.5, cellSize * 1.5), RectCorners(5), fill = restartAndScoreColor) {
                alignLeftToRightOf(restartBlock, cellSize)
                y = scoreRowTop
            }
        text("SCORE", cellSize * 0.5, scoreTextColor, font) {
            centerXOn(bgScore)
            alignTopToTopOf(bgScore, 3.0)
        }

        text(score.value.toString(), cellSize * 1.0, scoreTextColor, font) {
            setTextBounds(Rectangle(0.0, 0.0, bgScore.width, cellSize * 0.5))
            alignment = TextAlignment.MIDDLE_CENTER
            centerXOn(bgScore)
            alignTopToTopOf(bgScore, 5.0 + (cellSize * 0.5) + 5.0)
            score.observe {
                text = it.toString()
            }
        }

        val bgBest =
            roundRect(Size(cellSize * 2.5, cellSize * 1.5), RectCorners(5), fill = restartAndScoreColor) {
                alignRightToRightOf(gameField, 12.0)
                y = scoreRowTop
            }
        text("BEST", cellSize * 0.5, scoreTextColor, font) {
            centerXOn(bgBest)
            alignTopToTopOf(bgBest, 3.0)
        }
        text(best.value.toString(), cellSize * 1.0, scoreTextColor, font) {
            setTextBounds(Rectangle(0.0, 0.0, bgBest.width, cellSize * 0.5))
            alignment = TextAlignment.MIDDLE_CENTER
            alignTopToTopOf(bgBest, 5.0 + (cellSize * 0.5) + 5.0)
            centerXOn(bgBest)
            best.observe {
                text = it.toString()
            }
        }

        val emptyBombImg = resourcesVfs["emptyBomb.png"].readBitmap()
        val loadedBombImg = resourcesVfs["bomb.png"].readBitmap()
        val emptyRocketImg = resourcesVfs["emptyRocket.png"].readBitmap()
        val loadedRocketImg = resourcesVfs["rocket.png"].readBitmap()

        bombContainer =
            container {
                val bombBackground = roundRect(Size(cellSize * 2.5, cellSize * 2.5), RectCorners(10), fill = bombContainerColor)
                y = powerUpRowTop
                alignLeftToLeftOf(gameField, fieldWidth / 8)
                bombIcon(cellSize * 2.2, bombsLoadedCount.value > 0) { bombSelected }
                    .centerOn(bombBackground)
                onClick {
                    if (bombsLoadedCount.value > 0 && !showingRestart && tutorialAllowsBombTap()) {
                        bombSelected = !bombSelected
                        // Selecting the bomb cancels an in-progress rocket selection.
                        if (bombSelected && rocketSelection.selected) {
                            animatePowerUpSelection(rocketContainer, false)
                            removeRocketSelection()
                        }
                        animatePowerUpSelection(this, bombSelected)
                    }
                }
                bombsLoadedCount.observe {
                    this.removeChildrenIf { index, _ -> index == 1 }
                    bombIcon(cellSize * 2.2, bombsLoadedCount.value > 0) { bombSelected }
                        .centerOn(bombBackground)
                }
            }

        container {
            fun drawCartridges() {
                var prev: View? = null
                for (i in 0 until maxBombCount) {
                    val fill = if (bombsLoadedCount.value > i) loadedBombCartridgeColor else emptyCartridgeColor
                    val slot = cartridgeSlot(fill)
                    prev?.let { slot.alignLeftToRightOf(it, cellSize * 0.16) }
                    prev = slot
                }
            }

            drawCartridges()

            // Position the row only after the cartridges exist: aligning an empty
            // container yields NaN coordinates and the row never renders.
            centerXOn(bombContainer)
            alignTopToBottomOf(bombContainer, 2)

            bombsLoadedCount.observe {
                removeChildren()
                drawCartridges()
            }
        }

        rocketContainer =
            container {
                val rocketBackground = roundRect(Size(cellSize * 2.5, cellSize * 2.5), RectCorners(10), fill = rocketContainerColor)
                y = powerUpRowTop
                alignRightToRightOf(gameField, fieldWidth / 8)
                rocketIcon(cellSize * 2.3, rocketsLoadedCount.value > 0) { rocketSelection.selected }
                    .centerOn(rocketBackground)
                onClick {
                    if (rocketsLoadedCount.value > 0 && !showingRestart && tutorialAllowsRocketTap()) {
                        rocketSelection.toggleSelect()
                        // Selecting the rocket cancels a selected bomb.
                        if (rocketSelection.selected && bombSelected) {
                            bombSelected = false
                            animatePowerUpSelection(bombContainer, false)
                        }
                        animatePowerUpSelection(this, rocketSelection.selected)
                        if (!rocketSelection.selected) removeRocketSelection()
                    }
                }
                rocketsLoadedCount.observe {
                    this.removeChildrenIf { index, _ -> index == 1 }
                    rocketIcon(cellSize * 2.3, rocketsLoadedCount.value > 0) { rocketSelection.selected }
                        .centerOn(rocketBackground)
                }
            }

        container {
            fun drawCartridges() {
                var prev: View? = null
                for (i in 0 until maxRocketCount) {
                    val fill = if (rocketsLoadedCount.value > i) loadedRocketCartridgeColor else emptyCartridgeColor
                    val slot = cartridgeSlot(fill)
                    prev?.let { slot.alignLeftToRightOf(it, cellSize * 0.16) }
                    prev = slot
                }
            }

            drawCartridges()

            // Position the row only after the cartridges exist: aligning an empty
            // container yields NaN coordinates and the row never renders.
            centerXOn(rocketContainer)
            alignTopToBottomOf(rocketContainer, 2)

            rocketsLoadedCount.observe {
                removeChildren()
                drawCartridges()
            }
        }

        bombScaleNormal = bombContainer.scale
        bombScaleSelected = bombScaleNormal * 1.2
        rocketScaleNormal = rocketContainer.scale
        rocketScaleSelected = rocketScaleNormal * 1.2

        Napier.d("UI Initialized")

        // On first launch (no "tutorialSeen" flag yet) the interactive tutorial runs, scripting
        // its own cells onto this board step by step.
        val isFirstLaunch = storage.getOrNull(tutorialSeenKey) == null
        blocksMap = initializeRandomBlocksMap()
        drawAllBlocks()

        blockScaleNormal = blocksMap[Position(0, 0)]!!.scale
        blockScaleSelected = blockScaleNormal * 1.2

        touch {
            end { handleUp(it.global) }
        }

        // Idle hint loop: count up time since the last board touch and, once the
        // board is stuck with no available moves, jiggle any held power-up.
        addUpdater { dt ->
            if (isAnimating || showingRestart || tutorialActive) return@addUpdater
            idleTime += dt.seconds
            if (idleTime >= nextIdleHintTime) {
                nextIdleHintTime += idleHintDelay
                checkIdleHint()
            }
        }

        // On the very first launch, walk the player through a scripted merge, bomb and rocket,
        // then mark the tutorial seen and deal a fresh random board.
        if (isFirstLaunch) {
            startInteractiveTutorial {
                storage[tutorialSeenKey] = "true"
                restart()
            }
        }
}

fun Stage.unselectAllPowerUps() {
    if (bombSelected) {
        bombSelected = false
        animatePowerUpSelection(bombContainer, false)
    }
    if (rocketSelection.selected)
        {
            rocketSelection.unselect()
            animatePowerUpSelection(rocketContainer, false)
        }
}

fun Container.showRestart(isGameOver: Boolean = false, onRestart: () -> Unit) =
    container {
        showingRestart = true
        Napier.d("Showing Restart Container...")

        fun restart() {
            this@container.removeFromParent()
            onRestart()
        }

        fun copyBlocksToClipboard() {
            var gridString = ""
            for (j in 0 until gridRows) {
                for (i in 0 until gridColumns) {
                    val block = blocksMap[Position(i, j)]
                    if (block != null) {
                        gridString = gridString + block?.number?.emoji
                    }
                }
                gridString = gridString + "\n"
            }

            // Emoji grid first, then a plain-text score line.
            val clipboardContent = gridString + "I scored ${score.value} trillium.ing"

            Napier.d("Clipboard Content:\n $clipboardContent")
            stage?.views?.copyTextToClipboard(clipboardContent)
        }

        val restartBackground =
            roundRect(Size(fieldWidth, fieldHeight), RectCorners(5), fill = grayedGameFieldColor) {
                centerXOn(gameField)
                centerYOn(gameField)
                // The pause screen can be dismissed by tapping the backdrop; the game over
                // screen cannot, because there is no game left to return to.
                if (!isGameOver) {
                    onClick {
                        Napier.d("Restart Button - NO Clicked")
                        showingRestart = false
                        this@container.removeFromParent()
                    }
                }
            }

        fun clearPopup () { this@container.removeFromParent() }

        // Lays out each pause-menu button as [icon] [label]. The icon and label sit at fixed
        // offsets from the button edge, so they line up into tidy columns across every button.
        fun layoutButtonContent(label: View, icon: View, within: View) {
            val pad = fieldWidth * 0.085
            val gap = fieldWidth * 0.05
            icon.x = within.x + pad
            icon.centerYOn(within)
            label.x = within.x + pad + icon.width + gap
            label.centerYOn(within)
        }

        val bgRestartContainer =
            container {
                val textContainer = roundRect(Size(fieldWidth * 2 / 3, fieldHeight * 1 / 5), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    alignTopToTopOf(restartBackground, fieldHeight * 0.20)
                }
                val label = text("RESTART", 27.0, pauseScreenTextColor, font) {
                    onOver { color = pauseScreenTextHoverColor }
                    onOut { color = pauseScreenTextColor }
                    onDown { color = pauseScreenTextDownColor }
                    onUp { color = pauseScreenTextDownColor }
                }
                val icon = restartIcon(fieldWidth * 0.13, pauseScreenTextColor)
                layoutButtonContent(label, icon, textContainer)
                onUp {
                    Napier.d("Restart Button - YES Clicked")
                    showingRestart = false
                    restart()
                    clearPopup()
                }
                onClick {
                    Napier.d("Restart Button - YES Clicked")
                    showingRestart = false
                    restart()
                    clearPopup()
                }
            }
        val howToContainer =
            container {
                val textContainer = roundRect(Size(fieldWidth * 2 / 3, fieldHeight * 1 / 5), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    alignTopToTopOf(restartBackground, fieldHeight * 0.44)
                }
                val label = text("GUIDE", 27.0, pauseScreenTextColor, font) {
                    onOver { color = pauseScreenTextHoverColor }
                    onOut { color = pauseScreenTextColor }
                    onDown { color = pauseScreenTextDownColor }
                    onUp { color = pauseScreenTextDownColor }
                }
                val icon = helpIcon(fieldWidth * 0.13, pauseScreenTextColor)
                layoutButtonContent(label, icon, textContainer)
                onClick {
                    Napier.d("How To Play Button Clicked")
                    // Hide the pause popup while the guide is open so its buttons (and the
                    // board) behind the guide cannot be clicked through; restore it on close.
                    restartPopupContainer.visible = false
                    stage?.showHowToPlay { restartPopupContainer.visible = true }
                }
            }
        val shareContainer =
            container {
                val textContainer = roundRect(Size(fieldWidth * 2 / 3, fieldHeight * 1 / 5), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    alignBottomToBottomOf(restartBackground, fieldHeight * 0.12)
                }
                val label = text("SHARE", 27.0, pauseScreenTextColor, font) {
                    onOver { color = pauseScreenTextHoverColor }
                    onOut { color = pauseScreenTextColor }
                    onDown { color = pauseScreenTextDownColor }
                    onUp { color = pauseScreenTextDownColor }
                }
                val icon = shareIcon(fieldWidth * 0.13, pauseScreenTextColor)
                layoutButtonContent(label, icon, textContainer)
                // Copy the grid, then flash "COPIED" and a brighter button so the tap registers visibly.
                fun shareAndConfirm() {
                    Napier.d("Share Button - YES Clicked")
                    copyBlocksToClipboard()
                    label.text = "COPIED"
                    textContainer.fill = pauseScreenBlockCopiedColor
                    stage?.views?.launchImmediately {
                        delay(1200L)
                        label.text = "SHARE"
                        textContainer.fill = pauseScreenBlockColor
                    }
                }
                onUp { shareAndConfirm() }
                onClick { shareAndConfirm() }
            }

        // On game over, stagger the screen in and show a heading above the buttons.
        // Otherwise this is the pause screen and everything is already visible.
        if (isGameOver) {
            val headingText = "GAME OVER"
            val headingGlyphs = mutableListOf<Text>()
            container {
                // Faux-bold: clear_sans.fnt is a single-weight bitmap font, so stamp the
                // text twice with a 1px offset to thicken the strokes.
                for (offset in listOf(0.0, 1.0)) {
                    headingGlyphs += text(headingText, 20.0, RGBA(0, 0, 0), font) {
                        alignment = TextAlignment.MIDDLE_CENTER
                        x = offset
                    }
                }
                centerXOn(restartBackground)
                alignBottomToTopOf(bgRestartContainer, cellSize * 0.75)
            }
            animateGameOverIntro(
                restartBackground,
                headingGlyphs,
                headingText,
                listOf(bgRestartContainer, howToContainer, shareContainer),
            )
        }
    }

fun Container.restart() {
    Napier.d("Running Restart Function...")
    resetIdleTimer()
    score.update(0)
    bombsLoadedCount.update(startingBombCount)
    rocketsLoadedCount.update(startingRocketCount)
    blocksMap.values.forEach { it.removeFromParent() }
    blocksMap.clear()
    blocksMap = initializeRandomBlocksMap()
    drawAllBlocks()
}
