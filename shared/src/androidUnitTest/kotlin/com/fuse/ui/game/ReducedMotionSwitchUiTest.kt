package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
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
import com.fuse.feedback.ReducedMotionSettings
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
 * FEL-8 — proves the SINGLE switch end to end: the [ReducedMotionSettings] SETTING drives
 * `FuseTheme(reducedMotion = …)`, which drives `FuseMotion.Reduced`, which collapses every
 * FEL effect. Same Robolectric harness as FEL-6/FEL-7 (androidUnitTest, runs in
 * `:shared:testDebugUnitTest`).
 *
 * This mirrors exactly how `App()` wires the switch — `FuseTheme(reducedMotion =
 * settings.reducedMotionEnabled)` around the real [GameScreen] driving the real [GameStore] —
 * but flips the SETTING rather than passing a literal, so the test exercises
 * `setting → theme → effect`, not just `theme → effect`. We use BOTH a milestone (FEL-6) and a
 * combo (FEL-7) overlay so the "one toggle disables shake / particles / overshoot app-wide"
 * acceptance criterion is verified for more than one effect at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReducedMotionSwitchUiTest {

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

    /** A board one LEFT-merge from a 512 milestone (FEL-6), below the win target. */
    private fun nearMilestoneBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(256, 256, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(2, 4, 8, 16),
        ),
    )

    /** A board one LEFT-swipe from a x2 combo (FEL-7): 2+2=4 and 4+4=8 in the top row. */
    private fun comboBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 2, 4, 4),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingOnSuppressesMilestoneEffectAppWide() = runComposeUiTest {
        // The user setting is ON → it must collapse the milestone burst/flash (the single switch).
        val settings = ReducedMotionSettings(reducedMotionEnabled = true)
        val store = GameStore.forState(stateFromBoard(nearMilestoneBoard()))
        mainClock.autoAdvance = false
        setContent {
            // Exactly App()'s wiring: the SETTING drives FuseTheme(reducedMotion = …).
            FuseTheme(reducedMotion = settings.reducedMotionEnabled) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound())
            }
        }

        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()
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
    fun settingOffShowsMilestoneEffect() = runComposeUiTest {
        // Default OFF (full motion) → the milestone effect DOES fire. Proves the switch is what
        // suppresses it, not some unconditional gate.
        val settings = ReducedMotionSettings() // default OFF
        val store = GameStore.forState(stateFromBoard(nearMilestoneBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme(reducedMotion = settings.reducedMotionEnabled) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound())
            }
        }

        onNodeWithTag(MilestoneEffectTags.ROOT).assertDoesNotExist()
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(MilestoneEffectTags.ROOT).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingOnSuppressesComboBadgeAppWide() = runComposeUiTest {
        // The SAME single switch also suppresses the combo badge — one toggle, app-wide.
        val settings = ReducedMotionSettings(reducedMotionEnabled = true)
        val store = GameStore.forState(stateFromBoard(comboBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme(reducedMotion = settings.reducedMotionEnabled) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound())
            }
        }

        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()
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
    fun settingOffShowsComboBadge() = runComposeUiTest {
        // Default OFF (full motion) → the combo badge DOES appear.
        val settings = ReducedMotionSettings() // default OFF
        val store = GameStore.forState(stateFromBoard(comboBoard()))
        mainClock.autoAdvance = false
        setContent {
            FuseTheme(reducedMotion = settings.reducedMotionEnabled) {
                GameScreen(store = store, haptics = testHaptics(), sound = testSound())
            }
        }

        onNodeWithTag(ComboEffectTags.ROOT).assertDoesNotExist()
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(50L)
        onNodeWithTag(ComboEffectTags.ROOT).assertExists()
    }
}
