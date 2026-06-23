package com.fuse.analytics

import android.util.Log
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * ANL-1 (Android) — the debug print seam writes to **logcat** via `Log.d`, so events are observable
 * with `adb logcat -s Fuse-Analytics` (or filtering the `Fuse-Analytics` tag in Android Studio).
 */
actual fun analyticsDebugPrint(line: String) {
    Log.d(LOGCAT_TAG, line)
}

/** The logcat tag; the [DebugAnalyticsLogger] line already carries the `[Fuse-Analytics]` prefix. */
private const val LOGCAT_TAG = "Fuse-Analytics"

/**
 * ANL-1 (Android) — binds the active [AnalyticsLogger] to [DebugAnalyticsLogger] (debug-verifiable,
 * no SDK). `single` — stateless, one is enough. The Firebase-later seam replaces this single binding
 * with a `FirebaseAnalyticsLogger` once the project/config exists; no call site changes.
 */
actual val platformAnalyticsModule: Module = module {
    single<AnalyticsLogger> { DebugAnalyticsLogger() }
}
