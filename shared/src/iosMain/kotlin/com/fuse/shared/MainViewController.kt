package com.fuse.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.fuse.di.initKoin
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * Creates the UIViewController that hosts the Compose Multiplatform UI.
 * Called from Swift/SwiftUI via the Shared framework.
 *
 * Starts the shared Koin graph on first use (idempotent — safe if Swift also
 * calls doInitKoin() at launch) so the Compose tree can resolve the sample
 * dependency. iOS has no Application object, so the framework owns this.
 */
fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin()
    }
    return ComposeUIViewController { App() }
}
