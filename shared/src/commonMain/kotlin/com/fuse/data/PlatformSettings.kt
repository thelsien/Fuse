package com.fuse.data

import com.russhwolf.settings.Settings
import org.koin.core.module.Module

/**
 * UIB-6 — platform Koin module that binds the [com.russhwolf.settings.Settings]
 * instance used by [SettingsGameRepository].
 *
 * Each platform supplies the backing store differently, and crucially Android needs the
 * `Context` that already lives in the Koin graph (contributed by `androidContext(...)`
 * in `FuseApplication`), while iOS needs no extra input. Expressing this as an
 * `expect`/`actual` Koin **module** keeps the wiring inside the DI graph (so the
 * Android actual can `get<Context>()` from Koin) and keeps `dataModule` — which only
 * needs a bound `Settings` — fully platform-agnostic.
 *
 *  - Android (`platformSettingsModule.android.kt`): `SharedPreferencesSettings` over
 *    `context.getSharedPreferences("fuse", MODE_PRIVATE)`, the `Context` resolved from
 *    Koin's `androidContext()`.
 *  - iOS (`platformSettingsModule.ios.kt`): `NSUserDefaultsSettings` over
 *    `NSUserDefaults.standardUserDefaults`.
 *
 * Both app shells' Koin starts continue to work: Android's already calls
 * `androidContext(...)`, and iOS needs nothing extra.
 */
expect val platformSettingsModule: Module

/**
 * Convenience accessor: the common name a platform module binds [Settings] under.
 * (Declared for documentation symmetry; resolution goes through Koin's `get()`.)
 */
internal const val SETTINGS_PREFS_NAME: String = "fuse"
