data class RocketSelection(
    var selected: Boolean = false,
    var firstPosition: Position? = null,
    var secondPosition: Position? = null,
) {
    fun copy(): RocketSelection {
        return RocketSelection(selected, firstPosition, secondPosition)
    }

    fun unselect(): RocketSelection {
        this.selected = false
        this.firstPosition = null
        this.secondPosition = null
        return this
    }

    fun select(): RocketSelection {
        this.selected = true
        return this
    }

    fun toggleSelect(): RocketSelection {
        this.selected = !this.selected
        return this
    }

    fun selectFirst(position: Position): RocketSelection {
        this.firstPosition = position
        return this
    }

    fun unselectFirst(): RocketSelection {
        this.firstPosition = null
        return this
    }

    fun selectSecond(position: Position): RocketSelection {
        this.secondPosition = position
        return this
    }
}
