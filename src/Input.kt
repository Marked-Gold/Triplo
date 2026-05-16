import korlibs.korge.view.Stage
import korlibs.math.geom.Point

fun getXFromIndex(index: Int) = leftIndent + cellIndentSize + (cellSize + cellIndentSize) * index

fun getYFromIndex(index: Int) = topIndent + cellIndentSize + (cellSize + cellIndentSize) * index

fun getXFromPosition(position: Position) = getXFromIndex(position.x)

fun getYFromPosition(position: Position) = getYFromIndex(position.y)

fun getPositionFromPoint(point: Point): Position? {
    Napier.d("Point x = ${point.x}, y = ${point.y}")
    var xCoord = -1
    var yCoord = -1
    for (i in 0 until gridColumns) {
        if (point.x > (i * (cellSize + cellIndentSize) + (cellIndentSize / 2) + leftIndent) &&
            point.x < ((i + 1) * (cellSize + cellIndentSize) + (cellIndentSize / 2) + leftIndent)
        ) {
            Napier.d("Matched x value to position index $i")
            xCoord = i
            break
        }
    }
    if (xCoord == -1) {
        Napier.d("x value outside of cell range")
        return null
    } else {
        for (j in 0 until gridRows) {
            if (point.y > (j * (cellSize + cellIndentSize) + (cellIndentSize / 2) + topIndent) &&
                point.y < ((j + 1) * (cellSize + cellIndentSize) + (cellIndentSize / 2) + topIndent)
            ) {
                Napier.d("Matched y value to position index $j")
                yCoord = j
                break
            }
        }
    }
    return	if (yCoord == -1) {
        Napier.d("y value outside of cell range")
        null
    } else {
        Napier.d("Returned Position($xCoord,$yCoord)")
        Position(xCoord, yCoord)
    }
}

fun Stage.handleDown(point: Point)  {
    when {
        isAnimating -> return
        showingRestart -> return
        bombSelected -> {
            isPressed = true
            drawBombHover(getPositionFromPoint(point))
        }
        rocketSelection.selected -> {
            isPressed = true
            drawRocketSelection(getPositionFromPoint(point))
        }
        else -> {
            isPressed = true
            return pressDown(getPositionFromPoint(point))
        }
    }
}

fun Stage.handleHover(point: Point)  {
    if (isPressed) {
        when {
            isAnimating ||
                showingRestart ||
                rocketSelection.selected -> return
            bombSelected -> {
                removeBombHover()
                drawBombHover(getPositionFromPoint(point))
            }
            else -> hoverBlock(getPositionFromPoint(point))
        }
    }
}

fun Stage.handleUp(point: Point)  {
    isPressed = false
    when {
        isAnimating ||
            rocketSelection.selected ||
            showingRestart -> return
        bombSelected -> {
            val maybePosition = getPositionFromPoint(point)
            if (maybePosition == null) {
                removeBombHover()
            } else {
                animateBomb()
                removeBomb()
                bombSelected = false
                animatePowerUpSelection(bombContainer, false)
            }
        }
        else ->
            {
                if (atLeastThreeSelected()) {
                    successfulShape()
                } else {
                    unsuccessfulShape()
                }
            }
    }
}

fun Stage.pressDown(maybePosition: Position?) {
    if (maybePosition != null) {
        if (blocksMap[maybePosition] != null) {
            Napier.v("Selecting Block at Position(${maybePosition.x},${maybePosition.y})")
            hoveredPositions.add(maybePosition)
            updateBlock(blocksMap[maybePosition]!!.select(), maybePosition)
            selectBlocks()
        } else {
            Napier.w("No block found at Position(${maybePosition.x},${maybePosition.y})")
        }
    } else {
        Napier.w("Position parameter was null ")
    }
}

fun Stage.hoverBlock(maybePosition: Position?) {
    if (maybePosition != null && (hoveredPositions.size > 0 && hoveredPositions.last() != maybePosition)) {
        if (blocksMap[maybePosition] == null) {
            Napier.w("Null block found at Position(${maybePosition.x},${maybePosition.y})")
        } else if (hoveredPositions.size > 0 &&
            !isValidTransition(
                hoveredPositions.last(),
                maybePosition,
            )
        ) {
            Napier.d("Block transition is invalid")
        } else if (hoveredPositions.contains(maybePosition) && hoveredPositions.elementAtOrNull(hoveredPositions.size - 2) != maybePosition) {
            Napier.d("Block is already selected)")
        } else if (hoveredPositions.size > 0 && blocksMap[hoveredPositions.last()]?.number != blocksMap[maybePosition]?.number) {
            Napier.d("Hovered a square of a different value")
        } else {
            if (hoveredPositions.elementAtOrNull(hoveredPositions.size - 2) == maybePosition) {
                Napier.d("Reverted previous hover")
                updateBlock(
                    blocksMap[hoveredPositions[(hoveredPositions.size - 1)]]!!.unselect(),
                    hoveredPositions[(hoveredPositions.size - 1)],
                )
                val removedBlock = hoveredPositions.removeAt(hoveredPositions.size - 1)
            } else {
                Napier.v(
                    "Hovering Block at Position(${maybePosition.x},${maybePosition.y} from Position(${hoveredPositions.last().x},${hoveredPositions.last().y})",
                )
                hoveredPositions.add(maybePosition)
                updateBlock(blocksMap[maybePosition]!!.select(), maybePosition)
            }

            checkForHoveredPattern(maybePosition!!)
        }
    }
}

var hoveredSelection: BlockSelection = BlockSelection.SMALL

fun Stage.checkForHoveredPattern(position: Position)  {
    val isPowerUp = determinePattern(hoveredPositions).isPowerUp()
    if (isPowerUp && hoveredSelection != BlockSelection.PATTERN)
        {
            hoveredSelection = BlockSelection.PATTERN
            hoveredPositions.forEach { position2 -> updateBlock(blocksMap[position2]!!.selectPattern(), position2) }
        } else if (isPowerUp)
        {
            updateBlock(blocksMap[position]!!.selectPattern(), position)
        } else if (hoveredPositions.size >= largeSelectionSize && hoveredSelection != BlockSelection.EXTRALARGE) {
        hoveredSelection = BlockSelection.EXTRALARGE
        hoveredPositions.forEach { position2 -> updateBlock(blocksMap[position2]!!.selectExtraLarge(), position2) }
    } else if (hoveredPositions.size >= largeSelectionSize) {
        updateBlock(blocksMap[position]!!.selectExtraLarge(), position)
    } else if (hoveredPositions.size >= rocketPowerUpLength && hoveredSelection != BlockSelection.LARGE) {
        hoveredSelection = BlockSelection.LARGE
        hoveredPositions.forEach { position2 -> updateBlock(blocksMap[position2]!!.selectLarge(), position2) }
    } else if (hoveredPositions.size >= rocketPowerUpLength) {
        updateBlock(blocksMap[position]!!.selectLarge(), position)
    } else if (hoveredPositions.size >= mediumSelectionSize && hoveredSelection != BlockSelection.MEDIUM) {
        hoveredSelection = BlockSelection.MEDIUM
        hoveredPositions.forEach { position2 -> updateBlock(blocksMap[position2]!!.selectMedium(), position2) }
    } else if (hoveredPositions.size >= mediumSelectionSize) {
        updateBlock(blocksMap[position]!!.selectMedium(), position)
    } else if (hoveredSelection != BlockSelection.SMALL) {
        hoveredSelection = BlockSelection.SMALL
        hoveredPositions.forEach { position2 -> updateBlock(blocksMap[position2]!!.select(), position2) }
    } else {
        updateBlock(blocksMap[position]!!.select(), position)
    }
}
