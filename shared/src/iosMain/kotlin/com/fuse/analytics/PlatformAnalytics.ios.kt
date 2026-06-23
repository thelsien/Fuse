package com.fuse.analytics

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * ANL-1 (iOS) — the debug print seam writes via Kotlin/Native `println`, which is line-buffered to
 * stdout and observable in the Xcode console (and Console.app) for the running app. We deliberately
 * avoid `NSLog("%@", line)`: marshalling a Kotlin `String` into a varargs `%@` (an Obj-C object
 * pointer) format from Kotlin/Native is unsafe and crashes the runtime, whereas `println` carries the
 * already-formatted, escape-safe line verbatim. (When Firebase lands, the real logger replaces this
 * binding entirely — this seam is debug-only.)
 */
actual fun analyticsDebugPrint(line: String) {
    println(line)
}

/**
 * ANL-1 (iOS) — binds the active [AnalyticsLogger] to [DebugAnalyticsLogger] (debug-verifiable, no
 * SDK). iOS has no Application/Context, so (like `platformSharerModule`) the iOS Koin start needs no
 * changes beyond including this module. `single` — stateless. The Firebase-later seam replaces this
 * binding with a `FirebaseAnalyticsLogger` once the project/config exists; no call site changes.
 */
actual val platformAnalyticsModule: Module = module {
    single<AnalyticsLogger> { DebugAnalyticsLogger() }
}
