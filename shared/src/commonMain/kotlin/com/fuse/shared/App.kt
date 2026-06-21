package com.fuse.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.ui.game.GameScreen
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
        GameScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(FuseTheme.colors.bg),
        )
    }
}
