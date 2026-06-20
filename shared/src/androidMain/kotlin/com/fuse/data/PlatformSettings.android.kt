package com.fuse.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * UIB-6 (Android) — binds a [Settings] backed by `SharedPreferences`.
 *
 * The `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)` in `FuseApplication.onCreate()`), so no extra
 * wiring is needed in the app shell beyond what already exists. A dedicated
 * `"fuse"`-named prefs file keeps the game blob + best isolated from any other prefs.
 */
actual val platformSettingsModule: Module = module {
    single<Settings> {
        val context = get<Context>()
        val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        SharedPreferencesSettings(prefs)
    }
}
