import korlibs.image.font.*
import korlibs.io.file.std.*
import korlibs.korge.tests.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.time.*
import kotlin.test.*

/**
 * Soak tests guarding the two ways a casual game like this quietly ruins a phone:
 *
 *  - **Slowdown over time** — leaking views/objects so the scene graph grows a
 *    little every game until rendering each frame gets expensive.
 *  - **Battery drain** — doing unbounded work (spawning views, coroutines) while
 *    the board just sits idle with no input.
 *
 * None of these assert the *exact* count of a healthy frame; they assert the
 * count does not **grow** as the same operation repeats. A passing run means the
 * view lifecycle (`drawBlock` / `deleteBlock` / `restart`) is correctly paired
 * and idling is genuinely free of accumulation.
 */
class MemoryAndEnergyTest : ViewsForTesting() {

    /**
     * Minimal game-globals setup the block renderer needs. `Block`'s constructor
     * draws its rounded rect and number text immediately, so `cellSize` and
     * `font` must be set *before* any block is created.
     */
    private suspend fun Stage.prepareGame() {
        cellSize = 32
        font = resourcesVfs["clear_sans.fnt"].readBitmapFont()
        nextBlockId = 0
        blocksMap = initializeRandomBlocksMap()
    }

    /**
     * Each block must map to exactly one view in the scene graph. Catches a
     * regression where `drawBlock` adds a block more than once (an orphaned,
     * untracked view that never gets removed = a per-block leak).
     */
    @Test
    fun drawBlockAddsExactlyOneViewPerBlock() =
        viewsTest {
            prepareGame()
            val board = container { }
            board.drawAllBlocks()
            assertEquals(
                blocksMap.size,
                board.numChildren,
                "every block must be exactly one view — extra children mean drawBlock is leaking views",
            )
        }

    /**
     * `updateBlock` deletes the old block and draws its replacement. Repeating it
     * hundreds of times (what a long drag-selection does) must leave the scene
     * graph the same size — a rising child count means deleted blocks are not
     * actually leaving the scene.
     */
    @Test
    fun repeatedBlockUpdatesDoNotGrowTheSceneGraph() =
        viewsTest {
            prepareGame()
            drawAllBlocks()
            val baseline = views.stage.numChildren

            repeat(300) { i ->
                val position = Position(i % gridColumns, (i / gridColumns) % gridRows)
                updateBlock(blocksMap[position]!!, position)
            }

            assertEquals(
                baseline,
                views.stage.numChildren,
                "updateBlock must replace blocks 1:1 — a growing child count means views are leaking",
            )
        }

    /**
     * Every restart should fully recycle the board. Playing 30 games in a row
     * must not leave the scene graph any larger than it was after the 10th —
     * steady growth here is exactly the "slows down the longer you play" bug.
     */
    @Test
    fun repeatedRestartsDoNotGrowTheSceneGraph() =
        viewsTest {
            prepareGame()
            var countAfterWarmup = -1

            repeat(30) { i ->
                restart()
                if (i == 9) countAfterWarmup = views.stage.numChildren
            }

            assertEquals(
                countAfterWarmup,
                views.stage.numChildren,
                "restart() must recycle the whole board — a rising child count means each game leaks views",
            )
        }

    /**
     * The background runs an `addUpdater` shimmer every frame for the life of the
     * app. Advancing 10 simulated seconds (~600 frames) with no player input must
     * not spawn a single view or change the board — if idling accumulated work,
     * the device would burn battery sitting on a static screen.
     */
    @Test
    fun idlingDoesNotAccumulateViewsOrBlocks() =
        viewsTest {
            prepareGame()
            setupBackground()
            drawAllBlocks()

            val childrenBefore = views.stage.numChildren
            val blocksBefore = blocksMap.size

            // A dummy tween is just a clock: running it advances ~600 frames,
            // ticking every registered updater (including the background shimmer).
            val clock = solidRect(1, 1)
            tween(clock::x[2], time = 10.seconds)

            assertEquals(
                blocksBefore,
                blocksMap.size,
                "idling must not spawn or drop blocks",
            )
            assertEquals(
                childrenBefore + 1, // +1 for the dummy clock rect
                views.stage.numChildren,
                "idling must not accumulate views — a per-frame leak in an updater would surface here",
            )
        }

    /**
     * Block generation refills empty cells. Repeatedly clearing and refilling
     * must never push `blocksMap` past the board's capacity — an overfill would
     * mean stray Block objects (each a view container) piling up off-grid.
     */
    @Test
    fun blockGenerationNeverOverfillsTheBoard() =
        viewsTest {
            prepareGame()
            val capacity = gridRows * gridColumns

            repeat(100) {
                blocksMap.keys.shuffled().take(5).forEach { blocksMap.remove(it) }
                blocksMap.putAll(generateBlocksForEmptyPositions())
                assertTrue(
                    blocksMap.size <= capacity,
                    "block generation must never exceed the ${gridColumns}x$gridRows board (was ${blocksMap.size})",
                )
            }

            assertEquals(capacity, blocksMap.size, "the board should end full")
        }
}
