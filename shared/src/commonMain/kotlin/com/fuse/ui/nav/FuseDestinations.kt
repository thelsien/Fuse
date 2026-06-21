package com.fuse.ui.nav

/**
 * SHL-2 — the app-shell navigation destinations (routes).
 *
 * Plain `const` route strings keyed into the [androidx.navigation.NavHost] built in
 * `App()`. Kept as simple strings (not the type-safe `@Serializable` route objects) because
 * none of these destinations carries arguments — Home/Game/Settings are argument-free — so
 * string routes are the least-ceremony option that stays identical on Android and iOS.
 *
 *  - [HOME] — the launch surface ([com.fuse.ui.home.HomeScreen]); the back-stack root.
 *  - [GAME] — the playable Classic board ([com.fuse.ui.game.GameScreen]).
 *  - [SETTINGS] — placeholder Settings ([com.fuse.ui.settings.SettingsScreen]); SHL-3 fills it.
 *
 * Sprint 5's Daily mode will add a `DAILY` route here; SHL-4 (resume-on-launch) will choose
 * the *start* destination (Home vs. straight into Game) but the route set is unchanged.
 */
object FuseDestinations {
    const val HOME: String = "home"
    const val GAME: String = "game"
    const val SETTINGS: String = "settings"
}
