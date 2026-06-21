package com.fuse.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.presentation.GameStore
import com.fuse.ui.game.GameScreen
import com.fuse.ui.home.HomeScreen
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * Root composable for the Fuse game. Entry point for both Android and iOS.
 *
 * UIB-3 makes this the **playable game**: the app now boots straight into
 * [GameScreen], which resolves the MVI [com.fuse.presentation.GameStore] from the
 * (already-started) Koin graph, renders the board, and turns swipes into engine
 * moves — the First-Playable-trajectory moment. The screen is wrapped in [FuseTheme]
 * (dark) so the design tokens drive its colors.
 *
 * The FND-4 [com.fuse.ui.theme.SwatchScreen] token preview is intentionally KEPT in
 * the codebase (it is still a useful tokens reference / future debug route); it is
 * simply no longer the app's content. The Koin graph must already be started (each
 * app shell calls `initKoin()`) before this composable runs, because [GameScreen]
 * injects its store via `koinInject()`.
 *
 * ## FEL-8 — the single reduced-motion switch
 * This is the ONE place the app-wide reduced-motion setting feeds the theme. We resolve
 * [ReducedMotionSettings] (default-OFF) from Koin and pass its flag into
 * `FuseTheme(reducedMotion = …)`. `FuseTheme` maps `reducedMotion = true` → it provides
 * `FuseMotion.Reduced` through `LocalFuseMotion`, so every FEL-1..7 animation collapses
 * (slides/overshoot snap; milestone burst + flash and combo badge suppressed) — no
 * per-effect change. Because [ReducedMotionSettings.reducedMotionEnabled] is Compose-state-
 * backed and read here inside composition, flipping it (the future SHL-3 settings toggle)
 * recomposes `App()` and re-themes everything LIVE, with no app restart.
 */
@Composable
fun App() {
    val reducedMotionSettings: ReducedMotionSettings = koinInject()
    FuseTheme(
        darkTheme = true,
        reducedMotion = reducedMotionSettings.reducedMotionEnabled,
    ) {
        AppShell()
    }
}

/**
 * SHL-1 — the app shell: launch into [HomeScreen], with a deliberately MINIMAL
 * switch into the playable [GameScreen].
 *
 * ## TEMPORARY — to be replaced by SHL-2
 * This is a two-destination local-state switch (`Screen.Home` / `Screen.Game`), NOT
 * navigation. SHL-2 replaces it with Compose-Multiplatform navigation (a real back stack,
 * Home/Game/Settings destinations, system-back handling). No nav library is pulled in yet
 * on purpose — that is SHL-2's job. Until then:
 *  - Home → tapping **Classic** flips to [Screen.Game] (the fully-playable board, unchanged).
 *  - The game shows a **back** affordance (the existing "New game" UI stays; SHL-2 adds the
 *    real top-bar back) — here a system-style back returns to Home via [onBack].
 *  - **Daily** / **Settings** are no-ops for now (Daily = Sprint 5; Settings = SHL-3).
 *
 * ## Best score stays fresh automatically
 * Home's best is read from the shared Koin [GameStore]'s `state` [kotlinx.coroutines.flow.StateFlow]
 * (`bestScore`), collected here. Because the store is the single source of truth and persists/raises
 * best after every accepted move, returning from a game re-renders Home with the up-to-date best
 * with no manual refresh. (The store also seeds its initial best from the persisted
 * `GameRepository.loadBest()`, so a relaunch's Home shows the saved best immediately.)
 */
@Composable
private fun AppShell(store: GameStore = koinInject()) {
    var screen by remember { mutableStateOf(Screen.Home) }
    val state by store.state.collectAsState()

    when (screen) {
        Screen.Home -> HomeScreen(
            best = state.bestScore,
            onPlayClassic = { screen = Screen.Game },
            onOpenDaily = { /* SHL: Sprint 5 — no-op placeholder */ },
            onOpenSettings = { /* SHL-3 — no-op placeholder */ },
            modifier = Modifier.fillMaxSize(),
        )
        Screen.Game -> GameScreen(
            store = store,
            onBack = { screen = Screen.Home },
            modifier = Modifier
                .fillMaxSize()
                .background(FuseTheme.colors.bg),
        )
    }
}

/** SHL-1 — the two shell destinations. TEMPORARY: SHL-2 replaces this with real nav. */
private enum class Screen { Home, Game }
