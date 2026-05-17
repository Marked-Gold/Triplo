import korlibs.time.*
import korlibs.korge.animate.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.io.async.launchImmediately
import korlibs.math.interpolation.*
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
                checkGameOver()
            }
        }
    }

// Ends the game when the board is fully stuck: no available moves and no
// power-ups left to break the deadlock. Must run after every board-changing
// animation (merge, bomb, rocket) — any of them can leave the board dead.
fun Stage.checkGameOver() {
    if (!hasAvailableMoves() && bombsLoadedCount.value == 0 && rocketsLoadedCount.value == 0) {
        Napier.d("Game Over!")
        showRestart(isGameOver = true) {
            // Only show the interstitial once the player chooses to restart.
            launchImmediately {
                Ads.showInterstitial()
                restart()
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

// Reveals the heading one character at a time, typewriter style. The glyph list holds
// the stacked faux-bold copies of the same text; they are typed out in lockstep.
fun View.animateTypewriter(glyphs: List<Text>, fullText: String) {
    val stage = stage ?: return
    glyphs.forEach { it.text = "" }
    stage.launchImmediately {
        delay(200L)
        for (i in 1..fullText.length) {
            val visible = fullText.substring(0, i)
            glyphs.forEach { it.text = visible }
            delay(80L)
        }
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
        animate {
            parallel {
                Napier.v("Animating the bomb")
                hoveredBombPositions.forEach { position ->
                    val random = Random.nextDouble(0.0, 2 * 3.1415)
                    val xDirection = sin(random)
                    val yDirection = cos(random)
                    Napier.d("Bombing block at ${position.log()}")
                    moveTo(
                        blocksMap[position]!!,
                        xDirection * 1000,
                        yDirection * 1000,
                        0.8.seconds,
                        Easing.EASE_OUT_QUAD,
                    )
                }
            }
            block {
                hoveredBombPositions.forEach { position -> deleteBlock(blocksMap[position]!!) }
                hoveredBombPositions.clear()
            }
        }
        generateNewBlocks()
        stopAnimating()
        checkGameOver()
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
                Napier.d("Rocketing block from ${firstPosition.log()} to ${secondPosition.log()}")
                animate {
                    parallel {
                        moveTo(
                            blocksMap[firstPosition]!!,
                            getXFromPosition(secondPosition),
                            getYFromPosition(secondPosition),
                            0.15.seconds,
                            Easing.LINEAR,
                        )
                    }
                    sequenceLazy {
                        deleteBlock(blocksMap[secondPosition])
                        updateBlock(blocksMap[firstPosition]!!.copyToNextId().unselect(), secondPosition)
                        deleteBlock(blocksMap[firstPosition])
                    }
                    sequenceLazy {
                        generateNewBlocks()
                    }
                }
                stopAnimating()
                checkGameOver()
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
