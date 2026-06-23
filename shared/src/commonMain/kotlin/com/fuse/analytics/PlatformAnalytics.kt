package com.fuse.analytics

import org.koin.core.module.Module

/**
 * ANL-1 — the one-line platform print seam used by [DebugAnalyticsLogger] so the shared logger can
 * emit to the native debug console:
 *  - **Android** (`PlatformAnalytics.android.kt`): `android.util.Log.d`, visible in logcat.
 *  - **iOS** (`PlatformAnalytics.ios.kt`): `NSLog`, visible in the Xcode console / Console.app.
 *
 * This keeps [DebugAnalyticsLogger] (and its event formatting) in `commonMain` while only the
 * actual write-to-console differs per platform. It is the ONLY platform-specific part of ANL-1.
 */
expect fun analyticsDebugPrint(line: String)

/**
 * Per-platform Koin module binding the active [AnalyticsLogger] `single`, mirroring
 * [com.fuse.daily.platformSharerModule] / [com.fuse.ads.platformAdsModule].
 *
 * For ANL-1 BOTH platform actuals bind [DebugAnalyticsLogger] — that satisfies the "client analytics
 * initialized on both platforms" acceptance criterion (a live, resolvable, debug-verifiable logger)
 * with no SDK and no config. The Firebase-later seam swaps this binding for a `FirebaseAnalyticsLogger`
 * when the project exists (see [AnalyticsLogger] kdoc + `docs/analytics/ANL-1-firebase-seam.md`); no
 * call site changes because everything depends on the [AnalyticsLogger] interface.
 *
 * Registered in `appModules` (see `com.fuse.di.Modules`). The debug logger needs no init, so app
 * start merely resolves it; ANL-2 instruments the actual events at their call sites.
 */
expect val platformAnalyticsModule: Module
