package com.fuse.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Creates the UIViewController that hosts the Compose Multiplatform UI.
 * Called from Swift/SwiftUI via the Shared framework.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
