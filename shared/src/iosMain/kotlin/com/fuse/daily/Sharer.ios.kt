package com.fuse.daily

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * DLY-7 (iOS) — binds [Sharer] backed by a `UIActivityViewController` (the iOS share sheet).
 *
 * iOS has no Application/Context, so (like `platformHapticsModule`) the iOS Koin start needs
 * no changes beyond including this module. Bound as a `single` — stateless.
 */
actual val platformSharerModule: Module = module {
    single<Sharer> { IosSharer() }
}

/**
 * `UIActivityViewController`-backed [Sharer]. Presents the system share sheet for the card
 * text from the TOP-MOST view controller of the app's key window.
 *
 * ## Finding the presenter defensively
 * We resolve the key window's `rootViewController`, then walk its `presentedViewController`
 * chain to the front-most controller so the sheet presents from whatever is currently on
 * screen. Every step is null-guarded and the whole call is wrapped in `runCatching`, so a
 * Simulator with no key window (or any UIKit quirk) no-ops instead of crashing — sharing is
 * best-effort.
 */
private class IosSharer : Sharer {
    override fun share(text: String) {
        runCatching {
            val presenter = topViewController() ?: return@runCatching
            val controller = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            presenter.presentViewController(controller, animated = true, completion = null)
        }
    }

    /** The front-most presented view controller of the key window, or null if none. */
    private fun topViewController(): UIViewController? {
        val window: UIWindow? = UIApplication.sharedApplication.keyWindow
            ?: UIApplication.sharedApplication.windows
                .filterIsInstance<UIWindow>()
                .firstOrNull { it.isKeyWindow() }
            ?: UIApplication.sharedApplication.windows
                .filterIsInstance<UIWindow>()
                .firstOrNull()
        var controller = window?.rootViewController ?: return null
        while (true) {
            val presented = controller.presentedViewController ?: break
            controller = presented
        }
        return controller
    }
}
