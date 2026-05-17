import kotlin.math.abs
import kotlin.math.min
import kotlin.random.*

data class Position(val x: Int, val y: Int) {
    init {
        require(x >= 0)
        require(x < gridColumns)
        require(y >= 0)
        require(y < gridRows)
    }

    fun log(): String {
        return "Position(${this.x},${this.y})"
    }
}

fun allPositions(): List<Position> {
    val positionList: MutableList<Position> = mutableListOf()
    for (j in 0 until gridRows) {
        for (i in 0 until gridColumns) {
            positionList.add(Position(i, j))
        }
    }
    return positionList
}

enum class Direction {
    NORTH,
    NORTHEAST,
    EAST,
    SOUTHEAST,
    SOUTH,
    SOUTHWEST,
    WEST,
    NORTHWEST,
}

fun getCardinalDirections() = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)

fun initBlock(): Block {
    val selectedId = nextBlockId
    nextBlockId++
    val random = Random.nextDouble()
    return when {
        selectedId == random27ID -> Block(id = selectedId, Number.THREE)
        random < 0.1 -> Block(id = selectedId, Number.THREE)
        random < 0.2 -> Block(id = selectedId, Number.TWO)
        random < 0.5 -> Block(id = selectedId, Number.ONE)
        else -> Block(id = selectedId, Number.ZERO)
    }
}

fun initializeRandomBlocksMap(): MutableMap<Position, Block> {
    return allPositions().map { position -> Pair(position, initBlock()) }.toMap().toMutableMap()
}

// private fun getEnumId(position: Position) = array[position.x + position.y * gridRows]

private fun getNumber(id: Int) = Number.values()[id]

private fun getPosition(idx: Int) = Position(idx % gridColumns, idx / gridColumns)

fun getIndex(position: Position) = position.x + position.y * gridColumns

private fun tryPositionInDirection(
    position: Position,
    direction: Direction,
) = try {
    when (direction) {
        Direction.NORTH -> Position(position.x, position.y + 1)
        Direction.NORTHEAST -> Position(position.x + 1, position.y + 1)
        Direction.EAST -> Position(position.x + 1, position.y)
        Direction.SOUTHEAST -> Position(position.x + 1, position.y - 1)
        Direction.SOUTH -> Position(position.x, position.y - 1)
        Direction.SOUTHWEST -> Position(position.x - 1, position.y - 1)
        Direction.WEST -> Position(position.x - 1, position.y)
        Direction.NORTHWEST -> Position(position.x - 1, position.y + 1)
    }
} catch (e: IllegalArgumentException) {
    null
}

private fun tryAllAdjacentPositions(position: Position) =
    getCardinalDirections().mapNotNull { direction -> tryPositionInDirection(position, direction) }

fun tryAllSurroundingPositions(position: Position) =
    Direction.values().mapNotNull { direction -> tryPositionInDirection(position, direction) }

fun isValidTransition(
    oldPosition: Position,
    newPosition: Position?,
): Boolean  {
    return if (newPosition is Position) {
        (abs(newPosition.x - oldPosition.x) == 1) xor (abs(newPosition.y - oldPosition.y) == 1) &&
            ((newPosition.x - oldPosition.x) * (newPosition.y - oldPosition.y) == 0)
    } else {
        false
    }
}

fun hasAvailableMoves(): Boolean {
    return blocksMap.any { (position, block) -> hasTwoMatchingAdjacents(position, block) }
}

private fun hasTwoMatchingAdjacents(
    position: Position,
    block: Block,
): Boolean {
    return tryAllAdjacentPositions(position)
        .map { adjPosition -> blocksMap[adjPosition]?.number }
        .filter { adjNumber -> adjNumber == block.number }
        .count() >= 2
}

fun getAllEmptyPositions(): List<Position> {
    return allPositions().mapNotNull { position -> if (!blocksMap.containsKey(position)) position else null }
}

fun getRandomNumber(): Number {
    val random = Random.nextDouble()
    return when {
        random < 0.05 -> Number.THREE
        random < 0.2 -> Number.TWO
        random < 0.55 -> Number.ONE
        else -> Number.ZERO
    }
}

fun generateBlocksForEmptyPositions(): List<Pair<Position, Block>> {
    return getAllEmptyPositions().map { position ->
        val selectedId = nextBlockId
        nextBlockId++
        Pair(position, Block(selectedId, getRandomNumber()))
    }
}

fun determineMerge(positionList: MutableList<Position>): MutableMap<Position, Pair<Number, List<Position>>> {
    val mergeMap = mutableMapOf<Position, Pair<Number, List<Position>>>()
    val pattern = determinePattern(positionList)

    var nextNumber =
        when (positionList.size) {
            in 0..mediumSelectionSize - 1 -> blocksMap[positionList.first()]?.number?.next() ?: Number.ZERO
            in mediumSelectionSize..largeSelectionSize - 1 -> blocksMap[positionList.first()]?.number?.next()?.next() ?: Number.ZERO
            else -> blocksMap[positionList.first()]?.number?.next()?.next()?.next() ?: Number.ZERO
        }
    when (pattern) {
        Pattern.TRIPLE -> {
            val last = positionList.removeLast()
            mergeMap[last] = Pair(nextNumber, positionList)
        }
        Pattern.I4 -> {
            val last = positionList.removeLast()
            val secondLast = positionList.removeLast()
            mergeMap[secondLast] = Pair(nextNumber, positionList.subList(0, 1).toMutableList())
            mergeMap[last] = Pair(nextNumber, positionList.subList(1, 2).toMutableList())
        }
        Pattern.I5 -> {
            val last = positionList.removeLast()
            mergeMap[last] = Pair(nextNumber.next(), positionList.toMutableList())
        }
        Pattern.I6 -> {
            val last = positionList.removeLast()
            val secondLast = positionList.removeLast()
            mergeMap[secondLast] = Pair(nextNumber, positionList.subList(0, 2).toMutableList())
            mergeMap[last] = Pair(nextNumber, positionList.subList(2, 4).toMutableList())
        }
        Pattern.I7 -> {
            val last = positionList.removeLast()
            val secondLast = positionList.removeLast()
            mergeMap[secondLast] = Pair(nextNumber, positionList.subList(0, 2).toMutableList())
            mergeMap[last] = Pair(nextNumber.next(), positionList.subList(2, 5).toMutableList())
        }
        Pattern.RECTANGLE -> {
            val last = positionList.last()
            val secondLast = positionList[positionList.size - 2]

            val uniqueX =
                positionList.fold(
                    arrayOf<Int>(),
                    {
                            directionArray,
                            position,
                        ->
                        if (directionArray.contains(position.x)) directionArray else directionArray.plus(position.x)
                    },
                )
            val uniqueY =
                positionList.fold(
                    arrayOf<Int>(),
                    {
                            directionArray,
                            position,
                        ->
                        if (directionArray.contains(position.y)) directionArray else directionArray.plus(position.y)
                    },
                )

            Napier.d("UniqueX size: ${uniqueX.size} UniqueY size: ${uniqueY.size}")

            nextNumber = blocksMap[positionList.first()]?.number?.next() ?: Number.ZERO

            val mergeNumber =
                when (min(uniqueX.size, uniqueY.size)) {
                    in 0..2 -> nextNumber
                    3 -> nextNumber.next()
                    4 -> nextNumber.next().next()
                    5 -> nextNumber.next().next().next()
                    else -> nextNumber.next().next().next().next()
                }

            if (uniqueX.size < uniqueY.size || (uniqueX.size == uniqueY.size && last.x == secondLast.x))
                {
                    for (i in uniqueY) {
                        mergeMap[Position(last.x, i)] = Pair(mergeNumber, uniqueX.filter { x -> x != last.x }.map { x -> Position(x, i) })
                    }
                } else {
                for (i in uniqueX) {
                    mergeMap[Position(i, last.y)] = Pair(mergeNumber, uniqueY.filter { y -> y != last.y }.map { y -> Position(i, y) })
                }
            }
        }
        else -> {
            val last = positionList.removeLast()
            mergeMap[last] = Pair(nextNumber, positionList)
        }
    }

    return mergeMap
}

fun determineScore(positionList: MutableList<Position>): Int {
    return positionList.map { position -> blocksMap[position]?.number?.value ?: 0 }.fold(0, { a, b -> a + b })
}
