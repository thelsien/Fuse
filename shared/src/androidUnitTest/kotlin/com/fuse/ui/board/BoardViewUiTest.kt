package com.fuse.ui.board

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.fuse.engine.Board
import com.fuse.engine.Tile
import com.fuse.ui.theme.FuseTheme
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UIB-1 headless Compose UI tests for [BoardView].
 *
 * Same harness as [com.fuse.ui.SwatchScreenUiTest]: Compose-MP `runComposeUiTest`
 * needs the Robolectric environment on the Android target, so the test carries
 * `@RunWith(RobolectricTestRunner::class)` + `@GraphicsMode(NATIVE)` and lives in
 * androidUnitTest (a JUnit/Android runner can't sit in commonTest). Runs fully
 * headless in the existing `:shared:testDebugUnitTest` step.
 *
 * [BoardView] is rendered inside [FuseTheme] WITHOUT a started Koin graph, proving
 * the renderer is Koin-free.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardViewUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersKnownTileNumerals() = runComposeUiTest {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 0, 0),
                intArrayOf(0, 8, 0, 0),
                intArrayOf(0, 0, 16, 0),
                intArrayOf(0, 0, 0, 2048),
            ),
        )
        setContent {
            FuseTheme { BoardView(board) }
        }

        onNodeWithText("2").assertExists()
        onNodeWithText("4").assertExists()
        onNodeWithText("8").assertExists()
        onNodeWithText("16").assertExists()
        onNodeWithText("2048").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptyBoardRendersNoNumerals() = runComposeUiTest {
        setContent {
            FuseTheme { BoardView(Board.empty()) }
        }

        // No tiles -> none of the ramp numerals exist.
        onAllNodesWithText("2").assertCountEquals(0)
        onAllNodesWithText("4").assertCountEquals(0)
        onAllNodesWithText("2048").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isSizeAgnostic_rendersNonDefaultBoardSize() = runComposeUiTest {
        // 3x3 board — proves the renderer does not hardcode 4.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0, 4),
                intArrayOf(0, 8, 0),
                intArrayOf(0, 0, 16),
            ),
        )
        setContent {
            FuseTheme { BoardView(board) }
        }
        onNodeWithText("2").assertExists()
        onNodeWithText("8").assertExists()
        onNodeWithText("16").assertExists()
    }

    /**
     * Proves AC "recomposes on state change": hoist the board in mutableState,
     * assert the initial numerals, swap in a different Board instance, assert the
     * UI reflects the new state (old numerals gone, new ones present).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recomposesWhenBoardStateChanges() = runComposeUiTest {
        val first = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val second = Board.fromValues(
            arrayOf(
                intArrayOf(64, 0, 0, 0),
                intArrayOf(0, 128, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )

        var board by mutableStateOf(first)
        setContent {
            FuseTheme { BoardView(board) }
        }

        // Initial state.
        onNodeWithText("2").assertExists()
        onAllNodesWithText("64").assertCountEquals(0)
        onAllNodesWithText("128").assertCountEquals(0)

        // Mutate the hoisted state with a brand-new Board instance.
        board = second

        // UI reflects the change: old numeral gone, new numerals present.
        onAllNodesWithText("2").assertCountEquals(0)
        onNodeWithText("64").assertExists()
        onNodeWithText("128").assertExists()
    }

    // ----- FEL-1: slide animation -----------------------------------------------------

    /**
     * A fixed board side, deliberately under the Robolectric default root width (320dp)
     * so `Modifier.size(...)` is NOT clamped and the rendered side equals this value —
     * keeping geometry (and the expected offsets) deterministic.
     */
    private val boardSidePx = 300f

    private fun boardWith(vararg tiles: Pair<com.fuse.engine.Position, Tile>): Board {
        var b = Board.empty(4)
        for ((pos, tile) in tiles) b = b.withTile(pos.row, pos.col, tile)
        return b
    }

    /**
     * Proves the surviving tile ANIMATES rather than teleports: with the virtual clock
     * frozen, the tile is still near its OLD column at t=0 of the slide, and only reaches
     * the NEW column after the slide duration has elapsed. `tile.id` (7) is unchanged, so
     * it is the same composable sliding — not a destroy/recreate.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun survivingTileSlidesFromOldToNewPosition() = runComposeUiTest {
        val moving = Tile(value = 2, id = 7L)
        val first = boardWith(com.fuse.engine.Position(0, 0) to moving)
        // Same id, moved three columns to the right.
        val second = boardWith(com.fuse.engine.Position(0, 3) to moving)

        // Per-column horizontal step in layout space. Because every cell is the same
        // width, the text node's left differs between two columns by exactly this step,
        // independent of how the numeral is centered within its cell.
        val geo = BoardGeometry.forBoard(4, boardSidePx)
        val step = geo.offsetX(1) - geo.offsetX(0)
        val fullTravel = step * 3 // col 0 -> col 3

        var board by mutableStateOf(first)
        mainClock.autoAdvance = false
        setContent {
            FuseTheme {
                BoardView(board, modifier = Modifier.size(boardSidePx.dp))
            }
        }
        mainClock.advanceTimeByFrame()

        // Settled at the start column.
        val atStart = onNodeWithText("2").getUnclippedBoundsInRoot().left.value

        // Trigger the move; the slide begins but should NOT have jumped to the end.
        board = second
        mainClock.advanceTimeByFrame() // recomposition: relaunch LaunchedEffect(target)
        mainClock.advanceTimeByFrame() // first animation frame
        mainClock.advanceTimeBy(40L) // ~40ms into a 110ms ease-out slide
        val midX = onNodeWithText("2").getUnclippedBoundsInRoot().left.value
        val midProgress = midX - atStart
        assertTrue(
            midProgress in 1f..(fullTravel - 10f),
            "Mid-slide the tile should be PARTWAY (1..${fullTravel - 10f}px from start), " +
                "was $midProgress (0 => didn't move at all; $fullTravel => teleported)",
        )

        // After the full slide it settles exactly three columns to the right.
        mainClock.advanceTimeBy(400L)
        val atEnd = onNodeWithText("2").getUnclippedBoundsInRoot().left.value
        assertTrue(
            abs((atEnd - atStart) - fullTravel) < 8f,
            "Tile should settle 3 cols right (~${fullTravel}px), moved ${atEnd - atStart}",
        )
    }

    /**
     * Proves NO SPAWN FLICKER: a brand-new tile (new id, present only in the second board,
     * as a spawn/merge result would be) renders AT its target column from its very first
     * frame — it does not first appear at the origin (col 0 / 0,0) and slide in.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newTileAppearsAtTargetWithoutSlidingFromOrigin() = runComposeUiTest {
        // Reference tile "4" stays at col 0 the whole time — anchors col-0 text-left.
        val reference = Tile(value = 4, id = 1L)
        val first = boardWith(com.fuse.engine.Position(0, 0) to reference)
        // A new tile (new id) appears at col 3 — like a post-move spawn.
        val spawned = Tile(value = 2, id = 99L)
        val second = boardWith(
            com.fuse.engine.Position(0, 0) to reference,
            com.fuse.engine.Position(0, 3) to spawned,
        )

        val geo = BoardGeometry.forBoard(4, boardSidePx)
        val expectedTravel = geo.offsetX(3) - geo.offsetX(0) // col 0 -> col 3

        var board by mutableStateOf(first)
        mainClock.autoAdvance = false
        setContent {
            FuseTheme {
                BoardView(board, modifier = Modifier.size(boardSidePx.dp))
            }
        }
        mainClock.advanceTimeByFrame()

        // Col-0 text-left, from the stationary reference tile.
        val col0Left = onNodeWithText("4").getUnclippedBoundsInRoot().left.value

        // Introduce the spawned tile; check its VERY FIRST rendered frame.
        board = second
        mainClock.advanceTimeByFrame()
        val firstFrameLeft = onNodeWithText("2").getUnclippedBoundsInRoot().left.value
        val firstFrameTravel = firstFrameLeft - col0Left
        assertTrue(
            abs(firstFrameTravel - expectedTravel) < 8f,
            "Spawned tile must appear AT col 3 (~${expectedTravel}px right of col 0) from " +
                "its first frame — no flicker. Was ${firstFrameTravel}px " +
                "(~0 would mean it flashed at the origin first).",
        )
    }

    // ----- FEL-2: merge pop -----------------------------------------------------------

    /**
     * The merge-pop path composes and renders. A tile whose id is marked as a merge RESULT
     * in the [BoardTransition] runs the one-shot scale/glow [androidx.compose.runtime.LaunchedEffect]
     * (intensity-driven, see [MergePopTest] for the curves); here we assert that path renders
     * the tile without error, and that the default `transition = null` path is unchanged.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mergeResultTransitionComposesAndRendersValue() = runComposeUiTest {
        val result = Tile(value = 64, id = 42L)
        val board = boardWith(com.fuse.engine.Position(1, 1) to result)
        setContent {
            FuseTheme {
                BoardView(
                    board = board,
                    transition = BoardTransition(mapOf(42L to 64)),
                    modifier = Modifier.size(boardSidePx.dp),
                )
            }
        }
        onNodeWithText("64").assertExists()
    }

    // ----- FEL-3: spawn entrance ------------------------------------------------------

    /**
     * Proves the spawned tile appears IN PLACE at its target cell — it does not first flash
     * at the origin and slide in (the entrance is a scale+fade, not a slide). With the clock
     * frozen, the spawn's text-left at its very first frame already equals its target column,
     * so when the deferred entrance later runs, it grows in place rather than travelling.
     *
     * The entrance itself is a `graphicsLayer` scale+alpha, which is DRAW-only and does not
     * change layout bounds, so the "deferred until after the slide" timing is asserted purely
     * and deterministically in [com.fuse.ui.board.MergePopTest] via [spawnEntranceProgress]
     * (zero during the slide, ramps after). This test covers the render/positioning path.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun spawnedTileAppearsInPlaceAtItsTargetCell() = runComposeUiTest {
        // Reference "4" stays at col 0 — anchors col-0 text-left.
        val reference = Tile(value = 4, id = 1L)
        val first = boardWith(com.fuse.engine.Position(0, 0) to reference)
        // A new tile (new id) appears at col 2 — the move's spawn.
        val spawned = Tile(value = 2, id = 99L)
        val second = boardWith(
            com.fuse.engine.Position(0, 0) to reference,
            com.fuse.engine.Position(0, 2) to spawned,
        )

        val geo = BoardGeometry.forBoard(4, boardSidePx)
        val expectedTravel = geo.offsetX(2) - geo.offsetX(0) // col 0 -> col 2

        var board by mutableStateOf(first)
        var transition by mutableStateOf<BoardTransition?>(null)
        mainClock.autoAdvance = false
        setContent {
            FuseTheme {
                BoardView(board, transition = transition, modifier = Modifier.size(boardSidePx.dp))
            }
        }
        mainClock.advanceTimeByFrame()

        val col0Left = onNodeWithText("4").getUnclippedBoundsInRoot().left.value

        // Introduce the spawn, marking id 99 as the spawned tile (FEL-3 entrance branch).
        board = second
        transition = BoardTransition.fromOutcome(merges = emptyList(), spawnedId = 99L)
        mainClock.advanceTimeByFrame()
        val firstFrameLeft = onNodeWithText("2").getUnclippedBoundsInRoot().left.value
        val firstFrameTravel = firstFrameLeft - col0Left
        assertTrue(
            abs(firstFrameTravel - expectedTravel) < 8f,
            "Spawned tile must appear AT col 2 (~${expectedTravel}px right of col 0) from its " +
                "first frame — it grows in place, never slides in. Was ${firstFrameTravel}px.",
        )

        // Let the deferred entrance fully play out; the tile stays at its target column.
        mainClock.advanceTimeBy(600L)
        val settledLeft = onNodeWithText("2").getUnclippedBoundsInRoot().left.value
        assertTrue(
            abs((settledLeft - col0Left) - expectedTravel) < 8f,
            "After the entrance the spawn remains at col 2 (no travel): " +
                "was ${settledLeft - col0Left}px vs ${expectedTravel}px",
        )
    }

    /**
     * A spawn entrance composes/renders cleanly via the public [BoardTransition.fromOutcome]
     * path and the spawned numeral exists. Smoke coverage of the spawn branch independent of
     * the positioning assertions above.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun spawnTransitionComposesAndRendersValue() = runComposeUiTest {
        val tile = Tile(value = 2, id = 99L)
        val board = boardWith(com.fuse.engine.Position(1, 1) to tile)
        setContent {
            FuseTheme {
                BoardView(
                    board = board,
                    transition = BoardTransition.fromOutcome(emptyList(), spawnedId = 99L),
                    modifier = Modifier.size(boardSidePx.dp),
                )
            }
        }
        onNodeWithText("2").assertExists()
    }
}
