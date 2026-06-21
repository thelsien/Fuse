package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import com.fuse.engine.Board
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.fuse.feedback.HapticsCoordinator
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.NoOpHaptics
import com.fuse.feedback.NoOpSound
import com.fuse.feedback.SoundCoordinator
import com.fuse.feedback.SoundSettings
import com.fuse.presentation.GameStore
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FEL-7 headless Compose UI tests for the combo VISUAL, driving the REAL [GameStore] through
 * [GameScreen]. Same Robolectric harness as FEL-6 (androidUnitTest, runs in
 * `:shared:testDebugUnitTest`), with a frozen clock so the self-dismiss is deterministic via
 * `mainClock.advanceTimeBy`.
 *
 * A row `[2,2,4,4]` is one LEFT swipe from TWO merges in the same move (`2+2=4` and `4+4=8`),
 * i.e. a combo of x2 (none of which is a milestone, so the milestone effect stays silent). The
 * store emits `GameEffect.Moved(mergedValues = [4, 8])`, [GameScreen]'s collector raises the
 * one-shot combo trigger → the [ComboEffect] "x2" badge appears, then self-dismisses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComboEffectUiTest {

    private fun testHaptics(): HapticsCoordinator =
        HapticsCoordinator(NoOpHaptics, HapticsSettings())

    private fun testSound(): SoundCoordinator =
        SoundCoordinator(NoOpSound, SoundSettings())

    private fun stateFromBoard(board: Board): GameState = GameState(
        board = board,
        score = Score.zero,
        phase = GamePhase.Playing,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = 0,
    )

    /** A board one LEFT-swipe from a x2 combo: 2+2=4 and 4+4=8 in the top row. */
    private fun comboBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 2, 4, 4),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        ),
    )

    /** A board one LEFT-swipe from a single merge (2+2=4) — NOT a combo. */
    private fun singleMergeBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(8, 16, 32, 64),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun comboBadgeShowsCountOnMultiMergeThenSelfDismisses() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(comboBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound()) }
        }

        // No combo yet — the badge is absent.
        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()

        // Swipe LEFT: 2+2=4 AND 4+4=8 → two merges → x2 combo. The badge appears with "x2".
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L) // into the effect window, before it self-dismisses
        onNodeWithTag(ComboEffectTags.ROOT).assertExists()
        onNodeWithTag(ComboEffectTags.LABEL).assertTextEquals("x2")

        // Let the effect window elapse (comboMs = 650). After it, the badge self-dismisses.
        mainClock.advanceTimeBy(2_000L)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun comboBadgeIsSuppressedUnderReducedMotion() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(comboBoard()))
        mainClock.autoAdvance = false
        setContent {
            // Reduced motion active: the combo badge must NOT appear at all (hard AC).
            FuseTheme(reducedMotion = true) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound())
            }
        }

        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()

        // Produce the x2 combo — but with reduced motion the badge is gated off entirely.
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()

        mainClock.advanceTimeBy(2_000L)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleMergeDoesNotShowTheComboBadge() = runComposeUiTest {
        // A single 2+2=4 merge is not a combo, so the badge must not fire.
        val store = GameStore.forState(stateFromBoard(singleMergeBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound()) }
        }

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()
    }
}
