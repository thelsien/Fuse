package com.fuse.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
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
import com.fuse.ui.game.GameScreen
import com.fuse.ui.game.GameScreenTags
import com.fuse.ui.game.ScoreHudTags
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SHL-1 — verifies the MINIMAL App() Home↔Game switch end to end: from Home, activating
 * **Classic** shows the playable board; the board's back affordance returns to Home; and Home's
 * best score reflects the live store (it rises after a scoring move and is current on return).
 *
 * `App()`'s real switch is private + Koin-driven, so this test reconstructs the SAME two-screen
 * `when (screen)` switch over `Screen.Home`/`Screen.Game`, feeding a directly-injected [GameStore]
 * (no Koin) and a NoOp haptics/sound — mirroring `AppShell` exactly. When SHL-2 replaces the switch
 * with real navigation, this becomes the navigation test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppShellSwitchUiTest {

    private enum class Screen { Home, Game }

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

    @Composable
    private fun ShellUnderTest(store: GameStore) {
        var screen by remember { mutableStateOf(Screen.Home) }
        val state by store.state.collectAsState()
        when (screen) {
            Screen.Home -> HomeScreen(
                best = state.bestScore,
                onPlayClassic = { screen = Screen.Game },
                onOpenDaily = {},
                onOpenSettings = {},
            )
            Screen.Game -> GameScreen(
                store = store,
                haptics = testHaptics(),
                sound = testSound(),
                onBack = { screen = Screen.Home },
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun classicFromHomeShowsBoard_thenBackReturnsHome() = runComposeUiTest {
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
        setContent { FuseTheme { ShellUnderTest(store) } }

        // Start on Home.
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(GameScreenTags.ROOT).assertDoesNotExist()

        // Classic → board appears, playable.
        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(GameScreenTags.ROOT).assertExists()
        onNodeWithTag(HomeScreenTags.ROOT).assertDoesNotExist()

        // Back → Home again.
        onNodeWithTag(GameScreenTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(GameScreenTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun homeBestReflectsLiveStoreAfterAScoringMove() = runComposeUiTest {
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
        setContent { FuseTheme { ShellUnderTest(store) } }

        // Best starts at 0 on Home.
        onNodeWithTag(HomeScreenTags.BEST_VALUE).assertTextEquals("0")

        // Enter game, score a merge (2+2=4 → score/best 4), go back.
        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("4")
        onNodeWithTag(GameScreenTags.BACK).performClick()
        waitForIdle()

        // Home now reflects the live store best (no manual refresh).
        onNodeWithTag(HomeScreenTags.BEST_VALUE).assertTextEquals("4")
    }
}
