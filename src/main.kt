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

const val startingHighestTierReached = 3
var highestTierReached = startingHighestTierReached

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

suspend fun main() =
    Korge(windowSize = Size(360, 640), title = "2048", backgroundColor = RGBA(253, 247, 240)) {
        Napier.base(DebugAntilog())

        // Wire up interstitial ads (real on Android, no-op elsewhere) and start preloading one.
        views.installPlatformAds()

        val backgroundImg = resourcesVfs["background_triangles.png"].readBitmap()

        val background =
            container {
                image(backgroundImg) {
                    size(views.virtualWidth, views.virtualHeight)
                }
                alignTopToTopOf(this)
                alignRightToRightOf(this)
            }

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

        val pauseImg = resourcesVfs["pause.png"].readBitmap()
        val restartImg = resourcesVfs["restart.png"].readBitmap()
        val shareImg = resourcesVfs["share.png"].readBitmap()

        val btnSize = cellSize * 1.0
        val restartBlock =
            container {
                val backgroundBlock = roundRect(Size(btnSize, btnSize), RectCorners(5), fill = restartAndScoreColor)
                image(pauseImg) {
                    size(btnSize * 0.8, btnSize * 0.8)
                    centerOn(backgroundBlock)
                }
                alignLeftToLeftOf(gameField, cellSize * 0.5)
                alignBottomToTopOf(gameField, cellSize * 0.75)
                onClick {
                    if (!showingRestart) {
                        unselectAllPowerUps()
                        restartPopupContainer = this@Korge.showRestart({
                            // Show an interstitial when the player restarts from the pause menu.
                            this@Korge.launchImmediately {
                                Ads.showInterstitial()
                                this@Korge.restart()
                            }
                        }, restartImg, shareImg)
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
                alignBottomToTopOf(gameField, cellSize * 0.5)
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
                alignBottomToTopOf(gameField, cellSize * 0.5)
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
                alignTopToBottomOf(gameField, 18)
                alignLeftToLeftOf(gameField, fieldWidth / 8)
                image(if (bombsLoadedCount.value > 0) loadedBombImg else emptyBombImg) {
                    size(80, 80)
                    centerOn(bombBackground)
                }
                onClick {
                    if (bombsLoadedCount.value > 0 && !showingRestart) {
                        bombSelected = !bombSelected
                        animatePowerUpSelection(this, bombSelected)
                    }
                }
                bombsLoadedCount.observe {
                    this.removeChildrenIf { index, _ -> index == 1 }
                    image(if (bombsLoadedCount.value > 0) loadedBombImg else emptyBombImg) {
                        size(76, 76)
                        centerOn(bombBackground)
                    }
                }
            }

        container {
            alignTopToBottomOf(bombContainer, 2)
            alignRightToRightOf(bombContainer)
            alignLeftToLeftOf(bombContainer)

            val emptyFill = emptyCartridgeColor
            val loadedFill = loadedBombCartridgeColor

            var cart1Fill = emptyFill
            var cart2Fill = emptyFill
            var cart3Fill = emptyFill
            var cart4Fill = emptyFill
            var cart5Fill = emptyFill

            fun fillByBombCount() {
                cart1Fill = if (bombsLoadedCount.value > 0) loadedFill else emptyFill
                cart2Fill = if (bombsLoadedCount.value > 1) loadedFill else emptyFill
                cart3Fill = if (bombsLoadedCount.value > 2) loadedFill else emptyFill
                cart4Fill = if (bombsLoadedCount.value > 3) loadedFill else emptyFill
                cart5Fill = if (bombsLoadedCount.value > 4) loadedFill else emptyFill
            }

            val strokeThickness = 1.5

            fun drawCartridges() {
                val cart1 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart1Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToLeftOf(this)
                    }
                val cart2 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart2Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart1)
                    }
                val cart3 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart3Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart2)
                    }
                val cart4 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart4Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart3)
                    }
                val cart5 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart5Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart4)
                    }
            }

            fillByBombCount()
            drawCartridges()

            bombsLoadedCount.observe {
                this.removeChildren()
                fillByBombCount()
                drawCartridges()
            }
        }

        rocketContainer =
            container {
                val rocketBackground = roundRect(Size(cellSize * 2.5, cellSize * 2.5), RectCorners(10), fill = bombContainerColor)
                val rocketWidth = 96
                val rocketHeight = 96
                alignTopToBottomOf(gameField, 18)
                alignRightToRightOf(gameField, fieldWidth / 8)
                image(if (rocketsLoadedCount.value > 0) loadedRocketImg else emptyRocketImg) {
                    size(rocketWidth, rocketHeight)
                    centerOn(rocketBackground)
                }
                onClick {
                    if (rocketsLoadedCount.value > 0 && !showingRestart) {
                        rocketSelection.toggleSelect()
                        animatePowerUpSelection(this, rocketSelection.selected)
                        if (!rocketSelection.selected) removeRocketSelection()
                    }
                }
                rocketsLoadedCount.observe {
                    this.removeChildrenIf { index, _ -> index == 1 }
                    image(if (rocketsLoadedCount.value > 0) loadedRocketImg else emptyRocketImg) {
                        size(rocketWidth, rocketHeight)
                        centerOn(rocketBackground)
                    }
                }
            }

        container {
            alignTopToBottomOf(rocketContainer, 2)
            alignRightToRightOf(rocketContainer)
            alignLeftToLeftOf(rocketContainer)

            val emptyFill = emptyCartridgeColor
            val loadedFill = loadedRocketCartridgeColor

            var cart1Fill = emptyFill
            var cart2Fill = emptyFill
            var cart3Fill = emptyFill
            var cart4Fill = emptyFill
            var cart5Fill = emptyFill

            fun fillByRocketCount() {
                cart1Fill = if (rocketsLoadedCount.value > 0) loadedFill else emptyFill
                cart2Fill = if (rocketsLoadedCount.value > 1) loadedFill else emptyFill
                cart3Fill = if (rocketsLoadedCount.value > 2) loadedFill else emptyFill
                cart4Fill = if (rocketsLoadedCount.value > 3) loadedFill else emptyFill
                cart5Fill = if (rocketsLoadedCount.value > 4) loadedFill else emptyFill
            }

            val strokeThickness = 1.5

            fun drawCartridges() {
                val cart1 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart1Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToLeftOf(this)
                    }
                val cart2 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart2Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart1)
                    }
                val cart3 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart3Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart2)
                    }
                val cart4 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart4Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart3)
                    }
                val cart5 =
                    roundRect(
                        Size(cellSize / 2.0, cellSize * 0.5),
                        RectCorners(5),
                        fill = cart5Fill,
                        stroke = Colors.WHITE,
                        strokeThickness = strokeThickness,
                    ) {
                        alignLeftToRightOf(cart4)
                    }
            }

            fillByRocketCount()
            drawCartridges()

            rocketsLoadedCount.observe {
                this.removeChildren()
                fillByRocketCount()
                drawCartridges()
            }
        }

        bombScaleNormal = bombContainer.scale
        bombScaleSelected = bombScaleNormal * 1.2
        rocketScaleNormal = rocketContainer.scale
        rocketScaleSelected = rocketScaleNormal * 1.2

        Napier.d("UI Initialized")

        blocksMap = initializeRandomBlocksMap()
        blocksMap = initializeFixedBlocksMap()
        //blocksMap = initializeOnesBlocksMap()
        drawAllBlocks()

        blockScaleNormal = blocksMap[Position(0, 0)]!!.scale
        blockScaleSelected = blockScaleNormal * 1.2

        touch {
            end { handleUp(it.global) }
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

fun Container.showGameOver(onGameOver: () -> Unit) =
    container {
        showingRestart = true
        Napier.d("Showing Restart Container...")

        fun clearPopup() {
            this@container.removeFromParent()
        }

        fun restart() {
            clearPopup()
            onGameOver()
        }

        val restartBackground =
            roundRect(Size(fieldWidth, fieldHeight), RectCorners(5), fill = grayedGameFieldColor) {
                centerXOn(gameField)
                centerYOn(gameField)
            }
        val bgRestartContainer =
            container {
                roundRect(Size(fieldWidth / 2, fieldHeight / 2), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    (restartBackground)
                }
                text("Restarto", 30.0, RGBA(0, 0, 0), font) {
                    centerXOn(restartBackground)
                    alignTopToTopOf(restartBackground, 20.0)

                    alignment = TextAlignment.MIDDLE_CENTER
                    onOver { color = RGBA(90, 90, 90) }
                    onOut { color = RGBA(0, 0, 0) }
                    onDown { color = RGBA(120, 120, 120) }
                    onUp { color = RGBA(120, 120, 120) }
                }
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
        val gameOverText =
            container {
                alignBottomToTopOf(bgRestartContainer, cellSize * 1.0)
                centerXOn(bgRestartContainer)
                text("Out of moves") {
                    alignment = TextAlignment.MIDDLE_CENTER
                    textSize = 50.0
                    color = RGBA(255, 100, 90)
                }
            }
    }



fun Container.showRestart(onRestart: () -> Unit, restartBitmap: Bitmap, shareBitmap: Bitmap) =
    container {
        showingRestart = true
        Napier.d("Showing Restart Container...")

        fun restart() {
            this@container.removeFromParent()
            onRestart()
        }

        fun copyBlocksToClipboard() {
            val allPoses = allPositions()

            Napier.d("All positions: $allPoses")

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

            Napier.d("Copying blocks to clipboard...")

            Napier.d("GRID: $gridString")

            val scoreString = score.value.toString()

            // convert score string into emojis
            var scoreEmojiString = scoreString.map {
                Napier.d("Converting $it to emoji")
                when (it) {
                    '0' -> "0⃣"
                    '1' -> "1⃣"
                    '2' -> "2⃣"
                    '3' -> "3⃣"
                    '4' -> "4⃣"
                    '5' -> "5⃣"
                    '6' -> "6⃣"
                    '7' -> "7⃣"
                    '8' -> "8⃣"
                    '9' -> "9⃣"
                    else -> ""
                }
            }.joinToString("")

            Napier.d("SCORE: $scoreString")
            Napier.d("SCORE EMOJI: $scoreEmojiString")

            val clipboardContent = scoreEmojiString + "\n" + gridString + "https://playTr.io"

            Napier.d("Clipboard Content:\n $clipboardContent")
            // val clipboard = views.clipboard
            // clipboard.setContents(gridString + "\n" + scoreEmojiString)
        }

        val restartBackground =
            roundRect(Size(fieldWidth, fieldHeight), RectCorners(5), fill = grayedGameFieldColor) {
                centerXOn(gameField)
                centerYOn(gameField)
                onClick {
                    Napier.d("Restart Button - NO Clicked")
                    showingRestart = false
                    this@container.removeFromParent()
                }
            }

        fun clearPopup () { this@container.removeFromParent() }

        val bgRestartContainer =
            container {
                val textContainer = roundRect(Size(fieldWidth * 2 / 3, fieldHeight * 1 / 5), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    alignTopToTopOf(restartBackground, fieldHeight * 0.32)
                }
                text("RESTART", 27.0, pauseScreenTextColor, font) {
                    alignLeftToLeftOf(textContainer, 15.0)
                    alignTopToTopOf(textContainer, fieldHeight * 0.07)

                    alignment = TextAlignment.MIDDLE_CENTER
                    onOver { color = pauseScreenTextHoverColor }
                    onOut { color = pauseScreenTextColor }
                    onDown { color = pauseScreenTextDownColor }
                    onUp { color = pauseScreenTextDownColor }
                }
                image(restartBitmap) {
                    size(fieldWidth * 0.13, fieldWidth * 0.13)
                    centerYOn(textContainer)
                    alignRightToRightOf(textContainer, 15.0)
                }
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
            container {
                val textContainer = roundRect(Size(fieldWidth * 2 / 3, fieldHeight * 1 / 5), RectCorners(25), fill = pauseScreenBlockColor) {
                    centerXOn(restartBackground)
                    alignBottomToBottomOf(restartBackground, fieldHeight * 0.15)
                }
                text("SHARE", 27.0, pauseScreenTextColor, font) {
                    alignLeftToLeftOf(textContainer, 15.0)
                    alignTopToTopOf(textContainer, fieldHeight * 0.07)

                    alignment = TextAlignment.MIDDLE_CENTER
                    onOver { color = pauseScreenTextHoverColor }
                    onOut { color = pauseScreenTextColor }
                    onDown { color = pauseScreenTextDownColor }
                    onUp { color = pauseScreenTextDownColor }
                }
                image(shareBitmap){
                    size(fieldWidth * 0.13, fieldWidth * 0.13)
                    centerYOn(textContainer)
                    alignRightToRightOf(textContainer, 15.0)
                }
                onUp {
                    Napier.d("Share Button - YES Clicked")
                    copyBlocksToClipboard()
                }
                onClick {
                    Napier.d("Share Button - YES Clicked")
                    copyBlocksToClipboard()
                }
            }
    }

fun Container.restart() {
    Napier.d("Running Restart Function...")
    score.update(0)
    highestTierReached = startingHighestTierReached
    bombsLoadedCount.update(startingBombCount)
    rocketsLoadedCount.update(startingRocketCount)
    blocksMap.values.forEach { it.removeFromParent() }
    blocksMap.clear()
    blocksMap = initializeRandomBlocksMap()
    drawAllBlocks()
}
