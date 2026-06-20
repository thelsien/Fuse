package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
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
            FuseTheme { GameScreen(store = store) }
        }

        // Before: two 2-tiles, score 0.
        onNodeWithText("Score 0").assertExists()

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()

        // After: a merged 4 appears and the score line shows +4 (plus a spawned tile).
        onNodeWithText("4").assertExists()
        onNodeWithText("Score 4").assertExists()
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
            FuseTheme { GameScreen(store = store) }
        }

        onNodeWithText("Score 0").assertExists()

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()

        // Blocked: numerals and score unchanged, no merged/higher tile appeared.
        onNodeWithText("2").assertExists()
        onNodeWithText("16").assertExists()
        onNodeWithText("Score 0").assertExists()
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
            FuseTheme { GameScreen(store = store) }
        }

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithText("Score 4").assertExists()

        onNodeWithTag(GameScreenTags.NEW_GAME).performClick()
        waitForIdle()

        // Restart zeroes the score.
        onNodeWithText("Score 0").assertExists()
    }
}
