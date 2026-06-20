package com.fuse.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.fuse.presentation.SamplePresenter
import org.koin.compose.koinInject

/**
 * Root composable for the Fuse game. This is the entry point for both Android and iOS.
 *
 * For FND-3 it resolves the sample dependency ([SamplePresenter]) straight out of
 * the Koin graph via [koinInject] and renders its text, making the DI wiring
 * observable on screen. The Koin graph must already be started (see initKoin()
 * called from each app shell) before this composable runs.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val presenter = koinInject<SamplePresenter>()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Fuse",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = presenter.greetingText(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
