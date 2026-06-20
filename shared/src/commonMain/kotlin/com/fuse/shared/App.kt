package com.fuse.shared

import androidx.compose.runtime.Composable
import com.fuse.presentation.SamplePresenter
import com.fuse.ui.theme.SwatchScreen
import org.koin.compose.koinInject

/**
 * Root composable for the Fuse game. Entry point for both Android and iOS.
 *
 * FND-4 makes this the design-token swatch/preview screen: it renders the full
 * palette, tile color ramp, type scale, shapes and spacing purely from the token
 * layer ([com.fuse.ui.theme]), with a dark/light toggle. The token layer is
 * Koin-independent, so the screen renders with or without a started graph.
 *
 * The FND-3 Koin wiring is preserved: the sample [SamplePresenter] is still
 * resolved from the graph (proving DI is live) and its value is handed to the
 * screen as a footer. The Koin graph must already be started (each app shell
 * calls initKoin()) before this composable runs.
 */
@Composable
fun App() {
    val presenter = koinInject<SamplePresenter>()
    SwatchScreen(diStatus = presenter.greetingText())
}
