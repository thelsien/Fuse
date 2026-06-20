package com.fuse.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * UIB-6 (iOS) — binds a [Settings] backed by `NSUserDefaults`.
 *
 * iOS has no Application/Context, and `NSUserDefaults.standardUserDefaults` needs no
 * extra input, so the iOS Koin start (`MainViewController` → `initKoin()`) requires no
 * changes beyond including this module in the graph.
 */
actual val platformSettingsModule: Module = module {
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
}
