package com.fuse.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuse.feedback.ColorblindSettings
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.presentation.DailyStreakStore
import com.fuse.presentation.GameIntent
import com.fuse.presentation.GameStore
import com.fuse.ui.daily.DailyScreen
import com.fuse.ui.game.GameScreen
import com.fuse.ui.home.HomeScreen
import com.fuse.ui.nav.FuseDestinations
import com.fuse.ui.settings.SettingsScreen
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * Root composable for the Fuse game. Entry point for both Android and iOS.
 *
 * UIB-3 made this the **playable game**; SHL-1 launched into [HomeScreen] with a temporary
 * hand-rolled switch. SHL-2 replaces that switch with **real Compose-Multiplatform navigation**
 * (see [AppShell]).
 *
 * The screen tree is wrapped in [FuseTheme] (dark) so the design tokens drive its colors. The
 * Koin graph must already be started (each app shell calls `initKoin()`) before this composable
 * runs, because [GameScreen] injects its store via `koinInject()` and [AppShell] resolves the
 * shared [GameStore] here.
 *
 * ## FEL-8 — the single reduced-motion switch
 * This is the ONE place the app-wide reduced-motion setting feeds the theme. We resolve
 * [ReducedMotionSettings] (default-OFF) from Koin and pass its flag into
 * `FuseTheme(reducedMotion = …)`. `FuseTheme` maps `reducedMotion = true` → it provides
 * `FuseMotion.Reduced` through `LocalFuseMotion`, so every FEL-1..7 animation collapses. Because
 * [ReducedMotionSettings.reducedMotionEnabled] is Compose-state-backed and read here inside
 * composition, flipping it (the SHL-3 settings toggle) recomposes `App()` and re-themes everything
 * LIVE, with no app restart.
 */
@Composable
fun App() {
    val reducedMotionSettings: ReducedMotionSettings = koinInject()
    // SHL-3 — the colorblind-mode seam, mirroring reduced-motion: read the holder in composition so
    // a Settings flip recomposes App() and re-themes live. The palette behind the flag is ACC-1.
    val colorblindSettings: ColorblindSettings = koinInject()
    FuseTheme(
        darkTheme = true,
        reducedMotion = reducedMotionSettings.reducedMotionEnabled,
        colorblind = colorblindSettings.colorblindEnabled,
    ) {
        AppShell()
    }
}

/**
 * SHL-2 — the app shell as a real **Compose-Multiplatform navigation graph**.
 *
 * ## Navigation mechanism — JetBrains AndroidX Navigation Compose (multiplatform)
 * A [NavHost] over a [rememberNavController] with three argument-free destinations
 * ([FuseDestinations.HOME] / [FuseDestinations.GAME] / [FuseDestinations.SETTINGS]). This is the
 * idiomatic CMP nav primitive (real back stack, `navigate`/`popBackStack`) and — the crux of
 * SHL-2 — it integrates the platform back automatically:
 *  - **Android:** the `NavHost` registers with the `OnBackPressedDispatcher`, so the system /
 *    predictive back pops the nav back stack (Game→Home, Settings→Home) and, at the Home root,
 *    falls through to the default behaviour (exit the app). No `BackHandler` of our own is needed.
 *  - **iOS:** UIKit has no hardware back, so each non-root screen exposes an **in-screen** back
 *    affordance (Game's "‹ Home", Settings' "‹ Home") wired to [NavHostController.popBackStack];
 *    that is the single coherent back on iOS. (The same in-screen buttons also work on Android,
 *    in addition to system back.)
 *
 * ## Wiring
 *  - Home: `onPlayClassic → navigate(GAME)`, `onOpenSettings → navigate(SETTINGS)`. `onOpenDaily`
 *    stays a no-op placeholder (Daily = Sprint 5).
 *  - Game / Settings: their `onBack` calls `popBackStack()` (reconciling SHL-1's standalone
 *    "‹ Home" button into the nav-driven back — one back affordance, not two).
 *
 * ## In-progress game survives navigation
 * The [GameStore] is a Koin **singleton** holding the live game state, resolved once here and passed
 * straight into [GameScreen]. Navigating Home↔Game does NOT recreate or reset it: the destination's
 * composable is torn down/rebuilt but the store (hence board + score) is untouched, so a started game
 * is exactly where the player left it on return. Nothing in the nav layer calls `NewGame`.
 *
 * ## Best score stays fresh automatically
 * Home's best is read from the same shared store's `state` `StateFlow` (`bestScore`), collected here.
 * Because the store is the single source of truth and persists/raises best after every accepted move,
 * popping back to Home re-renders it with the up-to-date best with no manual refresh.
 */
@Composable
private fun AppShell(
    store: GameStore = koinInject(),
    dailyStreakStore: DailyStreakStore = koinInject(),
    navController: NavHostController = rememberNavController(),
) {
    val state by store.state.collectAsState()
    // DLY-5 — the live daily streak, surfaced on Home's Daily entry. Reactive: solving the
    // Daily (DailyScreen records it) updates this flow, so popping back to Home shows the
    // up-to-date streak with no manual refresh.
    val dailyStreak by dailyStreakStore.state.collectAsState()

    NavHost(
        navController = navController,
        startDestination = FuseDestinations.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(FuseDestinations.HOME) {
            // SHL-4 — the store auto-resumes a saved game on init and projects `canResume`
            // reactively, so Home offers Continue + New game when a resumable game exists and
            // a single Play Classic otherwise. Collecting `state` here keeps the choice
            // reactive: starting a new game (moveCount 0) or losing flips canResume back to
            // false, so popping to Home shows the right options.
            //
            //  - Play Classic (shown only when NOT resumable, i.e. the store already holds a
            //    fresh moveCount-0 game): just navigate — the held game IS the fresh board, so
            //    there is nothing to reset (and re-entering preserves an in-progress game).
            //  - Continue (resumable): just navigate to the already-resumed game — score/board
            //    intact, NO NewGame.
            //  - New game (resumable): explicitly start a fresh board (NewGame, best preserved,
            //    overwrites the saved blob) THEN navigate.
            HomeScreen(
                best = state.bestScore,
                canResume = state.canResume,
                savedScore = state.currentScore,
                onPlayClassic = { navController.navigate(FuseDestinations.GAME) },
                onContinue = { navController.navigate(FuseDestinations.GAME) },
                onNewGame = {
                    store.accept(GameIntent.NewGame())
                    navController.navigate(FuseDestinations.GAME)
                },
                // DLY-4 — Daily is now enabled: navigate to the playable Daily screen.
                dailyEnabled = true,
                // DLY-5 — surface the live daily streak on the Daily entry ("Daily · 🔥 X").
                dailyStreak = dailyStreak.current,
                onOpenDaily = { navController.navigate(FuseDestinations.DAILY) },
                onOpenSettings = { navController.navigate(FuseDestinations.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FuseDestinations.DAILY) {
            // DLY-4 — the playable Daily Challenge. The shared DailyStore (Koin singleton)
            // resolves today's puzzle + the single slot; back pops to Home (system back on
            // Android, the in-screen "‹ Home" affordance on iOS — same as Game/Settings).
            DailyScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(FuseTheme.colors.bg),
            )
        }
        composable(FuseDestinations.GAME) {
            GameScreen(
                store = store,
                onBack = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(FuseTheme.colors.bg),
            )
        }
        composable(FuseDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
