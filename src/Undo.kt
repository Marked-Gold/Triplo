import korlibs.korge.view.Stage

/**
 * Undo support for the pause menu.
 *
 * Each player-driven board change (merge / bomb / rocket) calls [Undo.push] right before mutating
 * state, capturing a minimal snapshot of the board and counters. [Stage.undoLastMove] later wipes
 * the current board views and rebuilds from the most recent snapshot.
 *
 * The history is capped at [maxHistory] = 1 so the player can only undo the single most recent
 * move. After they undo, the button is disabled (no history) until they make another move.
 *
 * Selection state, hover state, and the background gradient pulse are not captured: blocks come
 * back unselected (the default for `Block(id, number)`) and the background colour stays where it
 * was — purely cosmetic and not worth the bookkeeping.
 */
object Undo {
    private const val maxHistory = 1

    /** Minimal per-block state — `id` + `number` is everything a fresh [Block] needs. */
    private data class BlockSnap(val id: Int, val number: Rank)

    private data class Snapshot(
        val blocks: Map<Position, BlockSnap>,
        val score: Int,
        val bombs: Int,
        val rockets: Int,
    )

    private val history = ArrayDeque<Snapshot>()

    fun canUndo(): Boolean = history.isNotEmpty()

    fun clear() = history.clear()

    /**
     * Snapshot the current state. Called from the merge / bomb / rocket entry points right before
     * they mutate the board. Skipped during the scripted tutorial so a post-tutorial undo cannot
     * restore tutorial-era state.
     */
    fun push() {
        if (tutorialActive) return
        history.addLast(
            Snapshot(
                blocks = blocksMap.mapValues { (_, b) -> BlockSnap(b.id, b.number) },
                score = score.value,
                bombs = bombsLoadedCount.value,
                rockets = rocketsLoadedCount.value,
            ),
        )
        while (history.size > maxHistory) history.removeFirst()
    }

    /**
     * Restore the most recent snapshot into the live game state. Returns true if a restore
     * happened. The Stage receiver is needed so the rebuilt blocks attach to the same container
     * as the original board.
     */
    fun restoreLatest(stage: Stage): Boolean {
        val snap = history.removeLastOrNull() ?: return false

        blocksMap.values.forEach { it.removeFromParent() }
        blocksMap.clear()
        snap.blocks.forEach { (pos, b) -> blocksMap[pos] = Block(b.id, b.number) }
        stage.drawAllBlocks()

        score.update(snap.score)
        bombsLoadedCount.update(snap.bombs)
        rocketsLoadedCount.update(snap.rockets)

        resetIdleTimer()
        // The restored state should almost never be a dead end (every undoable move presupposes a
        // playable pre-state), but re-run the check just in case so the game-over screen reappears
        // correctly if it would otherwise be missed.
        stage.checkGameOver()
        return true
    }
}

/** Convenience receiver-style wrapper used from the pause-menu UNDO button. */
fun Stage.undoLastMove(): Boolean = Undo.restoreLatest(this)
