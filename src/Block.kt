import Number.*
import korlibs.korge.view.*
import korlibs.korge.view.align.*
import korlibs.image.color.*
import korlibs.math.geom.*
import kotlin.random.Random

private const val idleBorderThickness = 3.5

// A thicker border makes the selection-preview colour clearly visible (issue 3).
private const val selectionBorderThickness = 6.0


fun Container.addBlock(block: Block) = block.addTo(this)
fun Container.removeBlock(block: Block) {
    Napier.d("Removing block")
    block.removeFromParent()
}


enum class BlockSelection () {
    UNSELECTED, SMALL, MEDIUM, LARGE, EXTRALARGE, BOMB, ROCKET, PATTERN;

    fun colorContent (number: Number) =
        when (this){
            UNSELECTED -> number.color
            SMALL -> number.next().color
            MEDIUM -> number.next().next().color
            LARGE -> loadedRocketCartridgeColor
            EXTRALARGE -> number.next().next().next().color
            BOMB -> loadedBombCartridgeColor
            ROCKET -> loadedRocketCartridgeColor
            PATTERN -> patternBorderOptions[Random.nextInt(patternBorderOptions.size)]
        }

    fun colorBorder (number: Number) =
        when (this){
            UNSELECTED -> number.color
            SMALL -> number.next().color
            MEDIUM -> number.next().next().color
            LARGE -> loadedRocketCartridgeColor
            EXTRALARGE -> number.next().next().next().color
            BOMB -> loadedBombCartridgeColor
            ROCKET -> loadedRocketCartridgeColor
            PATTERN -> patternBorderOptions[Random.nextInt(patternBorderOptions.size)]
        }
}

/**
 * A single playing-field block.
 *
 * Selection preview (issue 4): while a chain is being dragged, every selected block previews the
 * number it is becoming. [previewNumber] drives that preview — non-result blocks just recolour
 * their border to the upcoming colour, while [isResultBlock] blocks (the squares that actually
 * become the upgraded result) are fully recoloured.
 */
data class Block(
    val id: Int,
    var number: Number,
    var selection: BlockSelection = BlockSelection.UNSELECTED,
    var previewNumber: Number? = null,
    var isResultBlock: Boolean = false,
) : Container() {

    init {
        val size = Size(cellSize, cellSize)
        val preview = previewNumber
        when {
            // Result square: fully recoloured to the block it is becoming.
            preview != null && isResultBlock -> {
                roundRect(size, RectCorners(5), fill = preview.color, stroke = preview.color, strokeThickness = selectionBorderThickness)
                drawNumber(preview.TextColor)
            }
            // Selected (but consumed) square: keeps its colour, border hints the upcoming colour.
            preview != null -> {
                roundRect(size, RectCorners(5), fill = number.color, stroke = preview.color, strokeThickness = selectionBorderThickness)
                drawNumber(number.TextColor)
            }
            // Bomb / rocket selections keep their dedicated styling.
            selection == BlockSelection.BOMB || selection == BlockSelection.ROCKET -> {
                roundRect(size, RectCorners(5), fill = number.color, stroke = selection.colorBorder(number), strokeThickness = selectionBorderThickness)
                roundRect(size, RectCorners(5), fill = selection.colorContent(number).withA(80))
                drawNumber(number.TextColor)
            }
            // Idle block.
            else -> {
                roundRect(size, RectCorners(5), fill = number.color, stroke = number.color, strokeThickness = idleBorderThickness)
                drawNumber(number.TextColor)
            }
        }
    }

    /**
     * Draws the block's number centred on its actual glyph ink (issue 3).
     *
     * KorGE's text alignment centres the glyph *advance* box, which leaves characters with an
     * asymmetric side-bearing (notably "1") looking off-centre. Measuring the rendered bounds and
     * offsetting by them centres what is actually visible.
     */
    private fun drawNumber(textColor: RGBA) {
        val label = text(number.display, textSizeFor(number), textColor, font)
        val bounds = label.getLocalBounds()
        label.xy(
            cellSize / 2.0 - bounds.x - bounds.width / 2.0,
            cellSize / 2.0 - bounds.y - bounds.height / 2.0,
        )
    }

    fun unselect (): Block {
        this.selection = BlockSelection.UNSELECTED
        this.previewNumber = null
        this.isResultBlock = false
        return this
    }

    fun select (): Block {
        this.selection = BlockSelection.SMALL
        return this
    }

    fun isGenerallySelected (): Boolean{
        return when (this.selection){
            BlockSelection.PATTERN,
            BlockSelection.SMALL,
            BlockSelection.MEDIUM,
            BlockSelection.LARGE,
            BlockSelection.EXTRALARGE,
            BlockSelection.ROCKET -> true
            else -> false
        }
    }

    fun selectMedium (): Block {
        this.selection = BlockSelection.MEDIUM
        return this
    }

    fun selectLarge (): Block {
        this.selection = BlockSelection.LARGE
        return this
    }

    fun selectExtraLarge (): Block {
        this.selection = BlockSelection.EXTRALARGE
        return this
    }

    fun selectBomb (): Block {
        this.selection = BlockSelection.BOMB
        return this
    }

    fun selectRocket (): Block {
        this.selection = BlockSelection.ROCKET
        return this
    }

    fun selectPattern (): Block {
        this.selection = BlockSelection.PATTERN
        return this
    }

    fun copy (): Block {
        return Block(id, number, selection, previewNumber, isResultBlock)
    }

    fun copyToNextId (): Block {
        val id = nextBlockId
        nextBlockId++
        return Block(id, number, selection)
    }

    fun add (numberValue: Int): Block {
        this.number = findClosest(numberValue + number.value)
        return this
    }

    fun updateNumber (numberValue: Number): Block {
        this.number = numberValue
        return this
    }

    override fun equals(other: Any?): Boolean {
        return other is Block && this.id == other.id
    }

    override fun hashCode(): Int { return id }
}

private fun textSizeFor(number: Number) = when (number) {
    ZERO, ONE, TWO, THREE, FOUR, FIVE -> cellSize / 2.0
    SIX, SEVEN, EIGHT -> cellSize * 4 / 9.0
    NINE, TEN -> cellSize * 2 / 5.0
    ELEVEN, TWELVE, THIRTEEN, FOURTEEN, FIFTEEN, SIXTEEN, SEVENTEEN, EIGHTEEN, NINETEEN -> cellSize / 2.0
}

