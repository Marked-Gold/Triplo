import korlibs.time.*
import korlibs.korge.animate.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.io.async.launchImmediately
import korlibs.math.interpolation.*
import korlibs.math.geom.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun Stage.animateMerge(mergeMap: MutableMap<Position, Pair<Number, List<Position>>>) =
    launchImmediately {
        startAnimating()
        // One bomb is awarded for every block of 243 (tier FIVE) or higher created
        // by this merge, regardless of whether that tier has been reached before.
        var bombsEarned = 0
        // The highest-tier block (81 / tier FOUR or above) forged by this merge,
        // and where it landed: once the merge settles its colour ripples out
        // across the background from that spot.
        var topTier: Number? = null
        var topHead: Position? = null
        animate {
            parallel {
                Napier.v("Animating the blocks merging together")
                mergeMap.forEach { (headPosition, valueAndMergePositions) ->
                    val mergePositions = valueAndMergePositions.second
                    mergePositions.forEach { position ->
                        Napier.d("Moving block from ${position.log()} to new block")
                        moveTo(
                            blocksMap[position]!!,
                            getXFromPosition(headPosition) + cellSize / 2,
                            getYFromPosition(headPosition) + cellSize / 2,
                            0.15.seconds,
                            Easing.LINEAR,
                        )
                        scaleTo(blocksMap[position]!!, 0, 0, 0.15.seconds, Easing.LINEAR)
                    }
                }
            }
            block {
                Napier.v("Animating deletion of previous blocks and adding new upgraded block")
                mergeMap.forEach { (headPosition, valueAndMergePositions) ->
                    valueAndMergePositions.second.forEach { position -> deleteBlock(blocksMap[position]!!) }
                    val value = valueAndMergePositions.first
                    if (value.ordinal >= Number.FIVE.ordinal) bombsEarned++
                    if (value.ordinal >= Number.FOUR.ordinal &&
                        (topTier == null || value.ordinal > topTier!!.ordinal)
                    ) {
                        topTier = value
                        topHead = headPosition
                    }
                    val newBlock = blocksMap[headPosition]!!.updateNumber(value).unselect().copy()
                    deleteBlock(blocksMap[headPosition]!!)
                    blocksMap[headPosition] = newBlock
                    drawBlock(newBlock, headPosition)
                }
            }
            sequenceLazy {
                val newPositionBlocks = generateBlocksForEmptyPositions()
                Napier.d(
                    "Generating new blocks ${newPositionBlocks.map {
                            (position, block) ->
                        "${block.number.value} at (${position.log()}\n"
                    }}",
                )
                blocksMap.putAll(newPositionBlocks)

                parallel {
                    newPositionBlocks
                        .forEach { (position, block) ->

                            val x = getXFromPosition(position)
                            val y = getYFromPosition(position)
                            val scale = block.scale

                            val newBlock = addBlock(block)
                            newBlock.position(x + cellSize / 2, y + cellSize / 2)
                            newBlock.scale = 0.0

                            tween(
                                newBlock::x[x],
                                newBlock::y[y],
                                newBlock::scale[scale],
                                time = 0.3.seconds,
                                easing = Easing.EASE_SINE,
                            )
                        }

                    mergeMap.forEach { (headPosition, _) ->
                        if (blocksMap[headPosition] != null) {
                            animateConsumption(blocksMap[headPosition]!!)
                        } else {
                            Napier.w("No block found for consumption at ${headPosition.log()}")
                        }
                    }
                }
            }
            block {
                stopAnimating()
                if (bombsEarned > 0) {
                    Napier.d("Merge created $bombsEarned block(s) of 243+, awarding $bombsEarned bomb(s)")
                    tryAddBombs(bombsEarned)
                }
                val tier = topTier
                val head = topHead
                if (tier != null && head != null) {
                    triggerBackgroundPulse(
                        tier,
                        getXFromPosition(head) + cellSize / 2.0,
                        getYFromPosition(head) + cellSize / 2.0,
                    )
                }
                checkGameOver()
                onTutorialMerge()
            }
        }
    }

// Ends the game when the board is fully stuck: no available moves and no
// power-ups left to break the deadlock. Must run after every board-changing
// animation (merge, bomb, rocket) — any of them can leave the board dead.
fun Stage.checkGameOver() {
    // The scripted tutorial drains power-ups on purpose; never end the game during it.
    if (tutorialActive) return
    if (!hasAvailableMoves() && bombsLoadedCount.value == 0 && rocketsLoadedCount.value == 0) {
        Napier.d("Game Over!")
        launchImmediately {
            // First the board shakes to signal the dead end, then the screen staggers in.
            animateBoardShake()
            showRestart(isGameOver = true) {
                // Only show the interstitial once the player chooses to restart.
                launchImmediately {
                    Ads.showInterstitial()
                    restart()
                }
            }
        }
    }
}

// Not used any more but left it in case of future changes
fun Animator.animateGravity() {
    parallel {
        blocksMap =
            blocksMap.mapKeys { (position, block) ->
                blocksMap.filter { (comparisonPosition, _) ->
                    position.x == comparisonPosition.x && position.y < comparisonPosition.y
                }
                    .size.let {
                        val newPosition = Position(position.x, gridRows - 1 - it)
                        if (newPosition != position)
                            {
                                moveTo(blocksMap[position]!!, getXFromPosition(newPosition), getYFromPosition(newPosition), 0.5.seconds, Easing.EASE_SINE)
                            }
                        newPosition
                    }
            }.toMutableMap()
    }
}

fun Animator.animateConsumption(block: Block) {
    val x = block.x
    val y = block.y
    val scale = block.scale
    tween(
        block::x[x - 4],
        block::y[y - 4],
        block::scale[scale + 0.1],
        time = 0.1.seconds,
        easing = Easing.LINEAR,
    )
    tween(
        block::x[x],
        block::y[y],
        block::scale[scale],
        time = 0.1.seconds,
        easing = Easing.LINEAR,
    )
}

fun Stage.animatePowerUpSelection(
    image: View,
    toggle: Boolean,
) = launchImmediately {
    animate {
        val x = image.x
        val y = image.y
        if (toggle) {
            tween(
                image::x[x - 8],
                image::y[y - 12],
                image::scale[bombScaleSelected],
                time = 0.1.seconds,
                easing = Easing.LINEAR,
            )
        } else {
            tween(
                image::x[x + 8],
                image::y[y + 12],
                image::scale[bombScaleNormal],
                time = 0.1.seconds,
                easing = Easing.LINEAR,
            )
        }
    }
}

// Shakes the whole board left and right to signal the player is out of moves.
// A decaying ~0.54s shake; suspends until it settles so the screen can follow it.
suspend fun Stage.animateBoardShake() {
    val blocks = blocksMap.values.toList()
    if (blocks.isEmpty()) return
    val homeX = blocks.associateWith { it.x }
    animate {
        for (dx in listOf(10.0, -9.0, 7.0, -5.0, 3.0, 0.0)) {
            parallel {
                blocks.forEach { block ->
                    tween(block::x[homeX.getValue(block) + dx], time = 0.09.seconds, easing = Easing.LINEAR)
                }
            }
        }
    }.awaitComplete()
}

// Staggers the game-over screen in over ~1.3s (the board shake runs first, for ~1.85s
// total): the dark overlay fades up, the heading types out one character at a time,
// then the RESTART / SHARE buttons rise into place. The glyph list holds the stacked
// faux-bold copies of the heading, typed out in lockstep.
fun View.animateGameOverIntro(
    overlay: View,
    headingGlyphs: List<Text>,
    headingText: String,
    buttons: List<View>,
) {
    val stage = stage ?: return
    overlay.alpha = 0.0
    headingGlyphs.forEach { it.text = "" }
    buttons.forEach {
        it.alpha = 0.0
        it.y += 16.0
    }
    stage.launchImmediately {
        // 1. The dark overlay fades up (~0.35s).
        animate { alpha(overlay, 1.0, 0.35.seconds, Easing.EASE_OUT) }.awaitComplete()
        // 2. The heading types out, one character at a time (~0.63s).
        for (i in 1..headingText.length) {
            val visible = headingText.substring(0, i)
            headingGlyphs.forEach { it.text = visible }
            delay(70L)
        }
        // 3. The buttons fade in and rise into place (~0.34s).
        animate {
            parallel {
                buttons.forEach { button ->
                    alpha(button, 1.0, 0.34.seconds, Easing.EASE_OUT)
                    tween(button::y[button.y - 16.0], time = 0.34.seconds, easing = Easing.EASE_OUT)
                }
            }
        }.awaitComplete()
    }
}

fun Stage.generateNewBlocks() =
    launchImmediately {
        val newPositionBlocks = generateBlocksForEmptyPositions()
        Napier.d("Generating new blocks ${newPositionBlocks.map { (position, block) -> "${block.number.value} at (${position.log()}\n" }}")
        blocksMap.putAll(newPositionBlocks)

        animate {
            parallel {
                newPositionBlocks
                    .forEach { (position, block) ->

                        val x = getXFromPosition(position)
                        val y = getYFromPosition(position)
                        val scale = block.scale

                        val newBlock = addBlock(block)
                        newBlock.position(x + cellSize / 2, y + cellSize / 2)
                        newBlock.scale = 0.0

                        tween(
                            newBlock::x[x],
                            newBlock::y[y],
                            newBlock::scale[scale],
                            time = 0.3.seconds,
                            easing = Easing.EASE_SINE,
                        )
                    }
            }
        }
    }

fun Stage.animateBomb() =
    launchImmediately {
        startAnimating()
        Napier.v("Animating the bomb")
        val bombedPositions = hoveredBombPositions.toList()
        hoveredBombPositions.clear()
        val flyingBlocks = bombedPositions.mapNotNull { blocksMap[it] }

        // Run the long fly-off in the background so the new pieces can start
        // dropping in while the old tiles are still spinning away.
        val flyOff = launchImmediately {
            animate {
                parallel {
                    flyingBlocks.forEach { block ->
                        val random = Random.nextDouble(0.0, 2 * 3.1415)
                        val xDirection = sin(random)
                        val yDirection = cos(random)
                        Napier.d("Bombing block id ${block.id}")
                        // Re-centre the rotation pivot: a block rotates around its local
                        // origin (top-left corner), which makes it orbit awkwardly. Shift
                        // its content to be origin-centred and compensate the block's
                        // position so it stays put before the spin begins.
                        block.forEachChild { child -> child.xy(child.x - cellSize / 2, child.y - cellSize / 2) }
                        block.xy(block.x + cellSize / 2, block.y + cellSize / 2)
                        moveTo(
                            block,
                            xDirection * 1000 + cellSize / 2,
                            yDirection * 1000 + cellSize / 2,
                            2.8.seconds,
                            Easing.EASE_OUT_QUAD,
                        )
                        val spin = Random.nextDouble(3.0, 5.0) * if (Random.nextBoolean()) 1 else -1
                        rotateBy(
                            block,
                            (spin * 360).degrees,
                            2.8.seconds,
                            Easing.EASE_OUT_QUAD,
                        )
                    }
                }
                block {
                    flyingBlocks.forEach { removeBlock(it) }
                }
            }
        }

        // Free the bombed cells immediately so the new pieces can be generated,
        // then drop them in after a short head start — well before the old tiles
        // have finished flying off.
        blocksMap = blocksMap.filter { (_, block) -> flyingBlocks.none { it.id == block.id } }.toMutableMap()
        delay(500L)
        generateNewBlocks()
        // The board is logically complete and playable here, so re-enable input now rather
        // than after the purely cosmetic fly-off of the old tiles finishes (~2.3s later).
        stopAnimating()

        flyOff.join()
        checkGameOver()
        onTutorialBomb()
    }

fun Stage.animateRocket(selection: RocketSelection) =
    launchImmediately {
        when {
            (selection.firstPosition == null) -> Napier.e("No first position when animating rockets")
            (selection.secondPosition == null) -> Napier.e("No second position when animating rockets")
            else -> {
                startAnimating()
                val firstPosition = selection.firstPosition!!
                val secondPosition = selection.secondPosition!!
                val firstBlock = blocksMap[firstPosition]!!
                val secondBlock = blocksMap[secondPosition]!!
                Napier.d("Rocketing: swapping ${firstPosition.log()} and ${secondPosition.log()}")
                animate {
                    parallel {
                        moveTo(
                            firstBlock,
                            getXFromPosition(secondPosition),
                            getYFromPosition(secondPosition),
                            0.15.seconds,
                            Easing.LINEAR,
                        )
                        moveTo(
                            secondBlock,
                            getXFromPosition(firstPosition),
                            getYFromPosition(firstPosition),
                            0.15.seconds,
                            Easing.LINEAR,
                        )
                    }
                    block {
                        updateBlock(firstBlock.unselect(), secondPosition)
                        updateBlock(secondBlock.unselect(), firstPosition)
                    }
                }
                stopAnimating()
                checkGameOver()
                onTutorialRocket()
            }
        }
    }

// Quick decaying horizontal shake. Used to nudge the player toward an unused
// power-up when the board is stuck and one must be spent to keep playing.
// `mirror` flips the shake direction so the bomb and rocket wobble symmetrically
// inward/outward rather than in lockstep.
fun Stage.jigglePowerUp(container: Container, mirror: Boolean = false) =
    launchImmediately {
        val baseX = container.x
        val dir = if (mirror) -1.0 else 1.0
        animate {
            for (offset in listOf(-7.0, 7.0, -5.0, 5.0, -3.0, 3.0, 0.0)) {
                tween(
                    container::x[baseX + offset * dir],
                    time = 0.1.seconds,
                    easing = Easing.LINEAR,
                )
            }
        }
    }

// Called once every idleHintDelay seconds of inactivity. If the board has no
// available moves the player must spend a power-up to continue, so any held
// bomb/rocket that is not already selected jiggles to draw attention to it.
fun Stage.checkIdleHint() {
    if (isAnimating || showingRestart || hasAvailableMoves()) return
    if (bombsLoadedCount.value > 0 && !bombSelected) jigglePowerUp(bombContainer)
    if (rocketsLoadedCount.value > 0 && !rocketSelection.selected) jigglePowerUp(rocketContainer, mirror = true)
}

fun Stage.animateSelectedBlock(
    maybeBlock: Block?,
    selected: Boolean,
) = launchImmediately {
    if (maybeBlock == null)
        {
            Napier.e("Empty block passed into animateSelectedBlock")
        } else {
        val block = maybeBlock!!
        animate {
            val x = block.x
            val y = block.y
            if (selected) {
                tween(
                    block::x[x - 4],
                    block::y[y - 4],
                    block::scale[blockScaleSelected],
                    time = 0.1.seconds,
                    easing = Easing.LINEAR,
                )
            } else {
                tween(
                    block::x[x],
                    block::y[y],
                    block::scale[blockScaleNormal],
                    time = 0.1.seconds,
                    easing = Easing.LINEAR,
                )
            }
        }
    }
}
