package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * UIB-3 headless Compose UI tests for [GameScreen] — the swipe → store → engine → UI
 * binding. Same Robolectric harness as the other UI tests (androidUnitTest, runs in
 * `:shared:testDebugUnitTest`). The store is injected directly (no Koin needed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameScreenUiTest {

    /** FEL-4 — a Koin-free coordinator so the UI tests still need no Koin (NoOp haptics). */
    private fun testHaptics(): HapticsCoordinator =
        HapticsCoordinator(NoOpHaptics, HapticsSettings())

    /** FEL-5 — a Koin-free sound coordinator (NoOp sound) so the UI tests need no Koin. */
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swipeLeftMergesTilesAndUpdatesScore() = runComposeUiTest {
        // A single row [2,2]: swiping LEFT merges to a 4 and scores +4.
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

        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound()) }
        }

        // Before: two 2-tiles, score 0.
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()

        // After: the HUD score shows +4 (the merge), and best tracks the new max.
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("4")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("4")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun blockedSwipeLeavesNumeralsAndScoreUnchanged() = runComposeUiTest {
        // Row [2,4,8,16] flush-left: swiping LEFT is a no-op.
        val store = GameStore.forState(
            stateFromBoard(
                Board.fromValues(
                    arrayOf(
                        intArrayOf(2, 4, 8, 16),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                    ),
                ),
            ),
        )

        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound()) }
        }

        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()

        // Blocked: numerals and score unchanged, no merged/higher tile appeared.
        onNodeWithText("2").assertExists()
        onNodeWithText("16").assertExists()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newGameButtonResetsBoard() = runComposeUiTest {
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
        setContent {
            FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound()) }
        }

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("4")

        onNodeWithTag(GameScreenTags.NEW_GAME).performClick()
        waitForIdle()

        // Restart zeroes the running score but best holds the session max (4).
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("4")
    }
}
