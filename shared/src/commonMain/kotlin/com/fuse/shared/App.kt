package com.fuse.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fuse.ui.game.GameScreen
import com.fuse.ui.theme.FuseTheme

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
 */
@Composable
fun App() {
    FuseTheme(darkTheme = true) {
        GameScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(FuseTheme.colors.bg),
        )
    }
}
