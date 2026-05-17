import korlibs.image.color.*

val patternBorderOptions: Array<RGBA> = arrayOf(
    Colors["#F26419"],
    Colors["#86BBD8"],
    Colors["#33658A"],
    Colors["#B20D30"],
    Colors["#454372"],
    Colors["#C69DD2"],
    Colors["#F79F79"],
    Colors["#FDE12D"],
)

val gameFieldColor = Colors["#e0d8e822"]

val grayedGameFieldColor = Colors["#aaa6a4cc"]

val cellColor = Colors["#cec0b250"]

//val restartAndScoreColor = Colors["#639cd9"]
// Faded to match the power-up tray backdrops so the board shows through.
val restartAndScoreColor = Colors["#a3d6b840"]

val scoreTextColor = Colors["#12291b"]

// Power-up tray backdrops: a soft, faded tint matching each power-up.
val bombContainerColor = Colors["#4f8fd140"]
val rocketContainerColor = Colors["#f8785540"]

val emptyCartridgeColor = Colors["#e6e6e6A0"]

// Semi-transparent so the cartridge outline reads as a soft border, not a glaring frame.
val cartridgeBorderColor = Colors["#ffffff99"]

val loadedBombCartridgeColor =  Colors["#1f3079"]

val loadedRocketCartridgeColor =  Colors["#F87855"]

val pauseScreenBlockColor = Colors["#a3d6b8"]
val pauseScreenBlockCopiedColor = Colors["#6fcf97"]

val pauseScreenTextColor = Colors["#12291b"]
val pauseScreenTextHoverColor = RGBA(90, 90, 90)
val pauseScreenTextDownColor = RGBA(120, 120, 120)

