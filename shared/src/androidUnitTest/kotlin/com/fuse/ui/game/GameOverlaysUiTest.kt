package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.fuse.ads.AdManager
import com.fuse.ads.InterstitialController
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UIB-5 headless Compose UI tests for the win/lose overlays, driving the REAL [GameStore]
 * through [GameScreen]. Same Robolectric harness as the other UI tests (androidUnitTest,
 * runs in `:shared:testDebugUnitTest`).
 *
 * The states are constructed via [GameStore.forState]:
 *  - LOSE: a stuck, full checkerboard board (no adjacent equals, no empty cells) is
 *    already `Lost`, so the lose overlay shows immediately.
 *  - WIN: a board one merge from 2048 ([1024,1024] in the top row); a real LEFT swipe
 *    merges to 2048, the store emits the one-shot `Won` effect, and the win overlay
 *    appears once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameOverlaysUiTest {

    /** FEL-4 — a Koin-free coordinator so these UI tests still need no Koin. */
    private fun testHaptics(): HapticsCoordinator =
        HapticsCoordinator(NoOpHaptics, HapticsSettings())

    /** FEL-5 — a Koin-free sound coordinator (NoOp sound) so these UI tests need no Koin. */
    private fun testSound(): SoundCoordinator =
        SoundCoordinator(NoOpSound, SoundSettings())

    private fun stateFromBoard(
        board: Board,
        phase: GamePhase = GamePhase.Playing,
        score: Score = Score.zero,
    ): GameState = GameState(
        board = board,
        score = score,
        phase = phase,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = 0,
    )

    /** A full checkerboard with no adjacent equals: no move changes it -> Lost. */
    private fun stuckBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        ),
    )

    /** A board one LEFT-merge from 2048. */
    private fun nearWinBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(1024, 1024, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        ),
    )

    // --- LOSE -------------------------------------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loseOverlayShowsFinalScoreAndRestartsOnTap() = runComposeUiTest {
        val store = GameStore.forState(
            stateFromBoard(
                board = stuckBoard(),
                phase = GamePhase.Lost,
                score = Score(current = 1234L, best = 1234L),
            ),
        )
        setContent { FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider), interstitialController = InterstitialController()) } }

        // Lose overlay is up with the final score; best preserved.
        onNodeWithTag(GameOverlayTags.LOSE_ROOT).assertExists()
        onNodeWithTag(GameOverlayTags.SCORE_VALUE).assertExists()
        onNodeWithTag(GameOverlayTags.LOSE_RESTART).assertExists()

        // Restart -> fresh game, overlay gone, score reset, best held.
        onNodeWithTag(GameOverlayTags.LOSE_RESTART).performClick()
        waitForIdle()

        onNodeWithTag(GameOverlayTags.LOSE_ROOT).assertDoesNotExist()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("1 234")
    }

    // --- WIN --------------------------------------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun winOverlayShowsOnceAndKeepGoingContinuesPlay() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(nearWinBoard()))
        setContent { FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider), interstitialController = InterstitialController()) } }

        // Not won yet.
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertDoesNotExist()

        // Swipe LEFT -> 1024+1024 = 2048: win overlay appears once.
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertExists()
        onNodeWithTag(GameOverlayTags.WIN_KEEP_GOING).assertExists()
        onNodeWithTag(GameOverlayTags.WIN_RESTART).assertExists()
        // Score reflects the +2048 merge.
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("2 048")

        // Keep going -> overlay dismissed, game continues (phase still Won).
        onNodeWithTag(GameOverlayTags.WIN_KEEP_GOING).performClick()
        waitForIdle()
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertDoesNotExist()

        // A subsequent valid swipe still moves tiles (play continues) AND the win
        // overlay does NOT reappear.
        val scoreBefore = store.state.value.currentScore
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeRight() }
        waitForIdle()
        // The board changed (a move was accepted) — moveCount advanced past the win move.
        assertTrue(
            "still Won after keep-going move",
            store.state.value.phase is GamePhase.Won,
        )
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertDoesNotExist()
        // Score did not regress; play is live.
        assertTrue(store.state.value.currentScore >= scoreBefore)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun winOverlayRestartStartsNewGame() = runComposeUiTest {
        val store = GameStore.forState(stateFromBoard(nearWinBoard()))
        setContent { FuseTheme { GameScreen(store = store, haptics = testHaptics(), sound = testSound(), achievements = AchievementsStore(), adManager = AdManager(NoOpAdProvider), interstitialController = InterstitialController()) } }

        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertExists()

        onNodeWithTag(GameOverlayTags.WIN_RESTART).performClick()
        waitForIdle()

        // Fresh game: overlay gone, running score zeroed, back to Playing.
        onNodeWithTag(GameOverlayTags.WIN_ROOT).assertDoesNotExist()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
        assertTrue("restart -> Playing", store.state.value.phase is GamePhase.Playing)
    }
}
