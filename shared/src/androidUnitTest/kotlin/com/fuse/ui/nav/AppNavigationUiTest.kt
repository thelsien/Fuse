package com.fuse.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.fuse.ui.home.HomeScreen
import com.fuse.ui.home.HomeScreenTags
import com.fuse.ui.settings.SettingsScreen
import com.fuse.ui.settings.SettingsScreenTags
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SHL-2 — verifies the real Compose-Multiplatform **navigation graph** end to end (replaces the
 * SHL-1 `AppShellSwitchUiTest`). It exercises the same `NavHost` topology `App()` builds — Home /
 * Game / Settings over a [rememberNavController] — but with a directly-injected [GameStore] and
 * NoOp haptics/sound (no Koin), so the assertions hit the actual nav primitive, not a stand-in.
 *
 * Coverage:
 *  - Home → Classic shows the playable board; back ("‹ Home") returns to Home.
 *  - Home → Settings shows the placeholder Settings; back returns to Home.
 *  - A started game **survives** a Home↔Game round-trip (board/score unchanged — the store is the
 *    single source of truth and nav never resets it), and Home's best reflects the live store.
 *  - Android **system back** (the crux): driving the activity's `OnBackPressedDispatcher` from the
 *    Game destination pops to Home — proving the NavHost's automatic system-back integration. (On
 *    iOS there is no hardware back; the in-screen "‹ Home" affordance asserted above is the back.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppNavigationUiTest {

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

    private fun mergeableStore(): GameStore = GameStore.forState(
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

    /**
     * Mirrors `App.AppShell`'s [NavHost] exactly, but with the store/feedback injected (no Koin).
     * Exposes the [NavHostController] via [onController] so the Android-system-back test can drive
     * the back dispatcher the same way the platform would.
     */
    @Composable
    private fun NavGraphUnderTest(
        store: GameStore,
        onController: (NavHostController) -> Unit = {},
    ) {
        val navController = rememberNavController()
        onController(navController)
        val state by store.state.collectAsState()
        NavHost(
            navController = navController,
            startDestination = FuseDestinations.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(FuseDestinations.HOME) {
                HomeScreen(
                    best = state.bestScore,
                    onPlayClassic = { navController.navigate(FuseDestinations.GAME) },
                    onOpenDaily = {},
                    onOpenSettings = { navController.navigate(FuseDestinations.SETTINGS) },
                )
            }
            composable(FuseDestinations.GAME) {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(FuseDestinations.SETTINGS) {
                // SHL-3 — drive the presentational Settings overload (explicit values + no-op
                // toggles) so this nav test needs no Koin; the holder-bound overload is covered by
                // SettingsScreenUiTest. We only assert the route + back here.
                SettingsScreen(
                    sound = true,
                    haptics = true,
                    reducedMotion = false,
                    colorblind = false,
                    onToggleSound = {},
                    onToggleHaptics = {},
                    onToggleReducedMotion = {},
                    onToggleColorblind = {},
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun classicFromHomeShowsBoard_thenInScreenBackReturnsHome() = runComposeUiTest {
        setContent { FuseTheme { NavGraphUnderTest(mergeableStore()) } }

        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(GameScreenTags.ROOT).assertDoesNotExist()

        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(GameScreenTags.ROOT).assertExists()
        onNodeWithTag(HomeScreenTags.ROOT).assertDoesNotExist()

        onNodeWithTag(GameScreenTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(GameScreenTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsFromHomeShowsScreen_thenBackReturnsHome() = runComposeUiTest {
        setContent { FuseTheme { NavGraphUnderTest(mergeableStore()) } }

        onNodeWithTag(HomeScreenTags.SETTINGS).performClick()
        waitForIdle()
        onNodeWithTag(SettingsScreenTags.ROOT).assertExists()
        onNodeWithTag(SettingsScreenTags.TITLE).assertExists()
        onNodeWithTag(HomeScreenTags.ROOT).assertDoesNotExist()

        onNodeWithTag(SettingsScreenTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(SettingsScreenTags.ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun startedGameSurvivesHomeRoundTrip_andHomeBestReflectsLiveStore() = runComposeUiTest {
        setContent { FuseTheme { NavGraphUnderTest(mergeableStore()) } }

        // Best starts at 0 on Home.
        onNodeWithTag(HomeScreenTags.BEST_VALUE).assertTextEquals("0")

        // Enter game, score a merge (2+2=4 → score/best 4).
        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(GameScreenTags.BOARD).performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("4")

        // Back to Home — best reflects the live store (no manual refresh).
        onNodeWithTag(GameScreenTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(HomeScreenTags.BEST_VALUE).assertTextEquals("4")

        // Re-enter the game — the in-progress game is intact (score still 4, not reset to 0).
        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("4")
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("4")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun androidSystemBackFromGameReturnsHome() = runComposeUiTest {
        // Capture the OnBackPressedDispatcher the NavHost registers its callback with — the same
        // dispatcher Android's hardware/predictive back drives. Dispatching onBackPressed() here is
        // exactly what the platform does, so a pop to Home proves the NavHost's automatic
        // system-back integration (no BackHandler of our own needed).
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        setContent {
            FuseTheme {
                dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                    ?.onBackPressedDispatcher
                NavGraphUnderTest(mergeableStore())
            }
        }

        // Navigate Home → Game.
        onNodeWithTag(HomeScreenTags.CLASSIC).performClick()
        waitForIdle()
        onNodeWithTag(GameScreenTags.ROOT).assertExists()

        // Simulate Android system/predictive back.
        runOnUiThread { requireNotNull(dispatcher).onBackPressed() }
        waitForIdle()

        // Back popped Game → Home via the system back stack.
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(GameScreenTags.ROOT).assertDoesNotExist()
    }
}
