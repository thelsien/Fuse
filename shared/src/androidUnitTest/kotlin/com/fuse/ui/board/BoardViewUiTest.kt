package com.fuse.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.engine.Board
import com.fuse.ui.theme.FuseTheme
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
}
