import Number.*
import korlibs.korge.view.*
import korlibs.image.color.*

enum class Pattern() {
    UNDETERMINED,
    TRIPLE,
    RECTANGLE,
    I4,
    I5,
    I6,
    I7,
    ;

    fun isPowerUp() =
        when (this) {
            UNDETERMINED,
            TRIPLE,
            -> false
            RECTANGLE,
            I4,
            I5,
            I6,
            I7,
            -> true
        }
}

fun determinePattern(positionList: MutableList<Position>): Pattern {
    val uniqueX =
        positionList.fold(
            arrayOf<Int>(),
            { directionArray, position -> if (directionArray.contains(position.x)) directionArray else directionArray.plus(position.x) },
        ).size
    val uniqueY =
        positionList.fold(
            arrayOf<Int>(),
            { directionArray, position -> if (directionArray.contains(position.y)) directionArray else directionArray.plus(position.y) },
        ).size

    if (positionList.size == 3) {
        return Pattern.TRIPLE
    } else if (uniqueX == 1 || uniqueY == 1)
        {
            return when {
                (positionList.size == 4) -> {
                    Napier.v("Pattern I4 found")
                    Pattern.I4
                }
                (positionList.size == 5) -> {
                    Napier.v("Pattern I5 found")
                    Pattern.I5
                }
                (positionList.size == 6) -> {
                    Napier.v("Pattern I6 found")
                    Pattern.I6
                }
                (positionList.size == 7) -> {
                    Napier.v("Pattern I7 found")
                    Pattern.I7
                }
                else -> {
                    Napier.v("Undetermined line pattern found")
                    Pattern.UNDETERMINED
                }
            }
        } else if (positionList.size == uniqueX * uniqueY) {
        Napier.v("Rectangle pattern found")
        return Pattern.RECTANGLE
    } else {
        Napier.v("Boring pattern found")
        return Pattern.UNDETERMINED
    }
}
