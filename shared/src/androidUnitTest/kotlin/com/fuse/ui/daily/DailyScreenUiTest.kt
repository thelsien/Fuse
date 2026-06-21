package com.fuse.ui.daily

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import com.fuse.daily.DailyPuzzle
import com.fuse.engine.Board
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyUiState
import com.fuse.ui.theme.FuseTheme
import com.fuse.daily.DailyClock
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DLY-4 — headless Compose UI tests for [DailyScreen] (Robolectric, androidUnitTest,
 * runs in `:shared:testDebugUnitTest`).
 *
 * The presentational [DailyScreenContent] is driven with hand-built [DailyUiState]s (no
 * store/Koin); the swipe + solved-overlay paths drive a real [DailyStore] over a tiny,
 * known puzzle so a single swipe-left both increments the counter and solves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyScreenUiTest {

    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date
    }

    /** Two 16s on the top-left → swipe LEFT merges to 32 (target 32, par 1). */
    private fun trivialPuzzle() = DailyPuzzle(
        seed = 0L,
        startBoard = Board.fromValues(
            arrayOf(
                intArrayOf(16, 16, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
        target = 32,
        par = 1,
    )

    private fun trivialStore() = DailyStore(
        clock = FixedClock(LocalDate(2026, 6, 21)),
        puzzle = trivialPuzzle(),
    )

    private fun unsolvedState() = DailyUiState(
        board = trivialPuzzle().startBoard,
        moveCount = 3,
        target = 32,
        par = 5,
        dayNumber = 172,
        solved = false,
        winningMoves = null,
        canUndo = true,
        canRestart = true,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsMoveCounterTargetParAndDay() = runComposeUiTest {
        setContent {
            FuseTheme {
                DailyScreenContent(
                    state = unsolvedState(),
                    onSwipe = {},
                    onUndo = {},
                    onRestart = {},
                )
            }
        }
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("3")
        onNodeWithTag(DailyHudTags.PAR).assertTextEquals("5")
        onNodeWithTag(DailyHudTags.TARGET).assertTextEquals("32")
        onNodeWithTag(DailyHudTags.DAY).assertTextEquals("#172")
        onNodeWithTag(DailyScreenTags.UNDO).assertExists()
        onNodeWithTag(DailyScreenTags.RESTART).assertExists()
        onNodeWithTag(DailyScreenTags.SOLVED_OVERLAY).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSwipeThatChangesBoardUpdatesTheCounter() = runComposeUiTest {
        val store = trivialStore()
        setContent { FuseTheme { DailyScreen(store = store) } }

        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("0")
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        // The swipe merged 16+16 → 32: counter advanced AND the puzzle solved.
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("1")
        onNodeWithTag(DailyScreenTags.SOLVED_OVERLAY).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun undoAndRestartReachTheStoreAndRevertMoves() = runComposeUiTest {
        // A 2-move puzzle so a productive move leaves an unsolved, undoable run.
        val puzzle = DailyPuzzle(
            seed = 0L,
            startBoard = Board.fromValues(
                arrayOf(
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            target = 32,
            par = 2,
        )
        val store = DailyStore(clock = FixedClock(LocalDate(2026, 6, 21)), puzzle = puzzle)
        setContent { FuseTheme { DailyScreen(store = store) } }

        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("1")

        onNodeWithTag(DailyScreenTags.UNDO).performClick()
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("0")

        // Restart after a move also returns to 0.
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("1")
        onNodeWithTag(DailyScreenTags.RESTART).performClick()
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("0")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun solvingShowsTheSolvedOverlay() = runComposeUiTest {
        val store = trivialStore()
        setContent { FuseTheme { DailyScreen(store = store) } }
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyScreenTags.SOLVED_OVERLAY).assertIsDisplayed()
        onNodeWithTag(DailyScreenTags.SOLVED_SUMMARY).assertTextContains("Par 1", substring = true)
        onNodeWithTag(DailyScreenTags.SHARE_PLACEHOLDER).assertExists()
    }
}
