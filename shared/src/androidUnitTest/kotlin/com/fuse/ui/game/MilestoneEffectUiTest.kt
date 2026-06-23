package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import com.fuse.ads.AdManager
import com.fuse.ads.NoOpAdProvider
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
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.GameStore
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FEL-6 headless Compose UI tests for the milestone VISUAL, driving the REAL [GameStore] through
 * [GameScreen]. Same Robolectric harness (androidUnitTest, runs in `:shared:testDebugUnitTest`),
 * with a frozen clock so the self-dismiss is deterministic via `mainClock.advanceTimeBy`.
 *
 * The board `[256,256]` in the top row is one LEFT-merge from a 512 milestone (NOT the win
 * target, so no win overlay interferes). A real LEFT swipe produces `mergedValues = [512]`, the
 * store emits `GameEffect.Moved`, and [GameScreen]'s collector raises the one-shot milestone
 * trigger → the [MilestoneEffect] overlay appears, then self-dismisses after the effect window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MilestoneEffectUiTest {

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

    /** A board one LEFT-merge from a 512 milestone (below the win target). */
    private fun nearMilestoneBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(256, 256, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(2, 4, 8, 16),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun milestoneEffectShowsOnMilestoneMergeThenSelfDismisses() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(nearMilestoneBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider)) }
        }

        // No milestone yet — the effect overlay is absent.
        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()

        // Swipe LEFT: 256 + 256 = 512 (a milestone, not the win). The effect appears.
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L) // into the effect window, before it self-dismisses
        onNodeWithTag(MilestoneEffectTags.ROOT).assertExists()

        // Let the effect window elapse (milestoneMs = 900). After it, the effect self-dismisses.
        mainClock.advanceTimeBy(2_000L)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun milestoneEffectIsSuppressedUnderReducedMotion() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(nearMilestoneBoard()))
        mainClock.autoAdvance = false
        setContent {
            // Reduced motion active: the milestone burst/flash must NOT appear at all (hard AC).
            FuseTheme(reducedMotion = true) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider))
            }
        }

        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()

        // Reach the 512 milestone — but with reduced motion the effect is gated off entirely.
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()

        mainClock.advanceTimeBy(2_000L)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nonMilestoneMergeDoesNotShowTheEffect() = runComposeUiTest {
        // A plain 2+2 = 4 merge is not a milestone, so the celebration must not fire.
        val store = GameStore.forState(
            stateFromBoard(
                Board.fromValues(
                    arrayOf(
                        intArrayOf(2, 2, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                    ),
                ),
            ),
        )
        mainClock.autoAdvance = false
        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider)) }
        }

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()
    }
}
