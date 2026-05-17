import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import kotlin.math.abs
import kotlin.math.max

val finalColor = Colors["#ffa96a"]

enum class Number(
    val blockName: String,
    val value: Int,
    val color: RGBA,
    val TextColor: RGBA,
    val display: String,
    val emoji: String
    ) {
    ZERO("Black", 1, Colors["#3F373D"], Colors["#FFFFFF"], "1", "\u2B1B"),
    ONE("Yellow", 3, Colors["#F3EAA0"], Colors["#150A19"], "3", "\uD83D\uDFE8"),
    TWO("Brown", 9, Colors["#8B5A2B"], Colors["#FFFFFF"], "9", "\uD83D\uDFEB"),
    THREE("Green", 27, Colors["#0C6408"],  Colors["#FFFFFF"], "27", "\uD83D\uDFE9"),
    FOUR("Purple", 81, Colors["#8150C3"], Colors["#FFFBFA"], "81", "\uD83D\uDFEA"),
    FIVE("Red", 243, Colors["#A71D31"], Colors["#FFECCE"], "243", "\uD83D\uDFE5"),
    SIX("Blue", 729, Colors["#9ECDD3"], Colors["#220D00"], "729", "\uD83D\uDFE6"),
    SEVEN("White", 2187, Colors["#FFFFFF"], Colors["#0A0810"], "2187", "\u2B1C"),
    EIGHT("Orange", 6561, Colors["#FF4500"], Colors["#FFFFFF"], "6561", "\uD83D\uDFE7"),
    NINE("Purple Heart", 19683, Colors["#9B30FF"], Colors["#FFFFFF"], "19683", "\uD83D\uDC9C"),
    TEN("Red Heart", 59049, Colors["#CD5C5C"], Colors["#FFFFFF"], "59049", "\uD83D\uDC9D"),
    ELEVEN("White Heart", 177147, Colors["#F0F0F0"], Colors["#010101"], "3^11", "\uD83E\uDD0D"),
    TWELVE("Pink Heart", 531441, Colors["#FF8FAB"], Colors["#2A0A14"], "3^12", "\uD83E\uDE77"),
    THIRTEEN("Black Heart", 1594323, Colors["#4A4A4A"], Colors["#FFFFFF"], "3^13", "\uD83D\uDC9A"),
    FOURTEEN("Green Heart", 4782969, Colors["#08A255"], Colors["#FFFFFF"], "3^14", "\uD83D\uDC9A"),
    FIFTEEN("Blue Heart", 14348907, Colors["#6495ED"], Colors["#FFFFFF"], "3^15", "\uD83D\uDC99"),
    SIXTEEN("Brown Heart", 43046721, Colors["#8B4513"], Colors["#FFFFFF"], "3^16", "\uD83D\uDC94"),
    SEVENTEEN("Panda", 129140163, Colors["#BEBEBE"], Colors["#FFFFFF"], "3^17", "\uD83D\uDC3C"),
    EIGHTEEN("Melting Face", 387420489, Colors["#FFDAB9"], Colors["#040811"], "3^18", "\uD83E\uDD75"),
    NINETEEN("100 Emoji", 1162261467, Colors["#7B3F05"], Colors["#FFFFFF"], "3^19", "\uD83D\uDD1F"),
    ;

    fun next() = values()[(ordinal + 1) % values().size]
    fun previous() = values()[max((ordinal - 1), 0) % values().size]
}
fun findClosest(comparisonValue: Int) = Number.values().minByOrNull { number: Number -> abs(number.value - comparisonValue) }!!
fun findClosestRoundedUp(comparisonValue: Int) = Number.values().find { number: Number -> number.value > comparisonValue }!!



