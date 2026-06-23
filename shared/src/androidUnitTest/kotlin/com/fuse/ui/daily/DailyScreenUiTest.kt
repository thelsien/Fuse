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
import com.fuse.ads.AdManager
import com.fuse.ads.FakeAdProvider
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.Sharer
import com.fuse.engine.Board
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyStreakState
import com.fuse.presentation.DailyStreakStore
import com.fuse.presentation.DailyUiState
import com.fuse.ui.theme.FuseTheme
import com.fuse.daily.DailyClock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
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
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC).plus(12.hours)
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

    /** A streak store with the NoOp repo default — records a fresh streak (1) on solve. */
    private fun streakStore() = DailyStreakStore(clock = FixedClock(LocalDate(2026, 6, 21)))

    /** An AdManager over a NoOp-fill fake — these DLY tests never exercise the streak-saver ad. */
    private fun noAdsManager() = AdManager(FakeAdProvider(loadSucceeds = false))

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
        setContent { FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = FakeSharer(), adManager = noAdsManager()) } }

        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("0")
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        // The swipe merged 16+16 → 32: counter advanced AND the puzzle solved.
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("1")
        onNodeWithTag(DailyScreenTags.SOLVED_OVERLAY).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun undoReachesTheStoreAndRevertsMoves() = runComposeUiTest {
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
        setContent { FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = FakeSharer(), adManager = noAdsManager()) } }

        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("1")

        onNodeWithTag(DailyScreenTags.UNDO).performClick()
        waitForIdle()
        onNodeWithTag(DailyHudTags.MOVES).assertTextEquals("0")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restartReachesTheStoreAndRevertsMoves() = runComposeUiTest {
        // Restart path, isolated in its own composition (a single swipe from the start board —
        // the same proven gesture as the other tests — so it doesn't chain a second swipe after
        // an undo, which could be coalesced under the shared Robolectric harness).
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
        setContent { FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = FakeSharer(), adManager = noAdsManager()) } }

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
        setContent { FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = FakeSharer(), adManager = noAdsManager()) } }
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyScreenTags.SOLVED_OVERLAY).assertIsDisplayed()
        onNodeWithTag(DailyScreenTags.SOLVED_SUMMARY).assertTextContains("Par 1", substring = true)
        onNodeWithTag(DailyScreenTags.SHARE_BUTTON).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun solvedOverlayShowsTheStreak() = runComposeUiTest {
        // DLY-5 — the solved overlay surfaces the current + longest streak.
        val solved = unsolvedState().copy(solved = true, winningMoves = 4)
        setContent {
            FuseTheme {
                DailyScreenContent(
                    state = solved,
                    streak = DailyStreakState(current = 3, longest = 7),
                    onSwipe = {},
                    onUndo = {},
                    onRestart = {},
                )
            }
        }
        onNodeWithTag(DailyScreenTags.SOLVED_STREAK).assertIsDisplayed()
        onNodeWithTag(DailyScreenTags.SOLVED_STREAK).assertTextContains("3", substring = true)
        onNodeWithTag(DailyScreenTags.SOLVED_STREAK).assertTextContains("Best 7", substring = true)
    }

    /** A fake [Sharer] that captures the last text it was asked to share (no OS sheet). */
    private class FakeSharer : Sharer {
        var lastShared: String? = null
        override fun share(text: String) { lastShared = text }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun solvedOverlayShowsAShareButton() = runComposeUiTest {
        // DLY-7 — the solved overlay surfaces a real Share button (not the old placeholder).
        val solved = unsolvedState().copy(solved = true, winningMoves = 4)
        setContent {
            FuseTheme {
                DailyScreenContent(
                    state = solved,
                    streak = DailyStreakState(current = 3, longest = 7),
                    onSwipe = {},
                    onUndo = {},
                    onRestart = {},
                )
            }
        }
        onNodeWithTag(DailyScreenTags.SHARE_BUTTON).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingShareBuildsACardWithDayTargetMovesAndSharesIt() = runComposeUiTest {
        // DLY-7 — a live solve, then tapping Share, calls the (fake) Sharer with a non-empty
        // card carrying the day, target and move count. We assert the card text, not the OS sheet.
        val sharer = FakeSharer()
        val store = trivialStore() // target 32, par 1, day #N from the fixed 2026-06-21 clock.
        setContent {
            FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = sharer, adManager = noAdsManager()) }
        }

        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyScreenTags.SHARE_BUTTON).performClick()
        waitForIdle()

        val card = sharer.lastShared
        requireNotNull(card) { "Share button should have called Sharer.share(...)" }
        assert(card.contains("Fuse Daily #")) { "card missing day header: $card" }
        assert(card.contains("🎯 32")) { "card missing target: $card" }
        assert(card.contains("solved in 1 move")) { "card missing move count: $card" }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun solvingARealRunRecordsAndShowsStreakOne() = runComposeUiTest {
        // DLY-5 end-to-end: a live solve drives the recorder (NoOp repo → fresh streak 1) and
        // the overlay shows it.
        val store = trivialStore()
        setContent { FuseTheme { DailyScreen(store = store, streakStore = streakStore(), sharer = FakeSharer(), adManager = noAdsManager()) } }
        onNodeWithTag(DailyScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(DailyScreenTags.SOLVED_STREAK).assertTextContains("Streak: 1", substring = true)
    }
}
