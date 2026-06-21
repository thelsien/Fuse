package com.fuse.feedback

import com.russhwolf.settings.Settings

/**
 * SHL-3 — the persistence seam for the four feel/accessibility toggles
 * (Sound, Haptics, Reduced motion, Colorblind mode).
 *
 * ## One repository, four keys
 * The Settings *screen* (SHL-3) flips four independent flags. Rather than scatter
 * `multiplatform-settings` reads/writes across four holders, all persistence lives HERE:
 * each holder ([SoundSettings], [HapticsSettings], [ReducedMotionSettings],
 * [ColorblindSettings]) delegates its load (seed-on-construction) and store (write-through
 * on flip) to this one repository. So the holders keep their existing TYPES/shape — the
 * coordinators ([HapticsCoordinator]/[SoundCoordinator]) and `App()` (which read the holders)
 * need no change — while gaining durable persistence behind them.
 *
 * ## Defaults (the documented out-of-the-box state)
 *  - **Sound ON**, **Haptics ON** — feedback is on by default (opt-out).
 *  - **Reduced motion OFF** — full motion by default (opt-in to reduced motion).
 *  - **Colorblind mode OFF** — standard palette by default (opt-in).
 *
 * A *missing* key (fresh install / never toggled) reads back the default; once the user
 * toggles, the explicit value is stored and restored on the next launch.
 *
 * ## Seeding on launch == persistence across relaunch
 * The Koin graph constructs each holder from this repository at startup
 * ([com.fuse.di.feedbackModule]). Because the repository is backed by the same platform
 * [Settings] (SharedPreferences / NSUserDefaults) used by
 * [com.fuse.data.SettingsGameRepository], a value written on one launch is read back on the
 * next — so the user's choices survive an app relaunch (the SHL-3 acceptance criterion).
 *
 * ## Test seam
 * Pure JVM/iOS tests construct a [SettingsFeedbackPreferences] over a `MapSettings`
 * (multiplatform-settings-test) to round-trip values across a simulated relaunch without a
 * real platform store; UI/unit tests that don't care about persistence use the
 * [NoOpFeedbackPreferences] default (mirrors `NoOpGameRepository` for the game store).
 */
interface FeedbackPreferences {
    /** Loads the persisted sound flag, or [DEFAULT_SOUND] (ON) if never set. */
    fun loadSound(): Boolean

    /** Loads the persisted haptics flag, or [DEFAULT_HAPTICS] (ON) if never set. */
    fun loadHaptics(): Boolean

    /** Loads the persisted reduced-motion flag, or [DEFAULT_REDUCED_MOTION] (OFF) if never set. */
    fun loadReducedMotion(): Boolean

    /** Loads the persisted colorblind flag, or [DEFAULT_COLORBLIND] (OFF) if never set. */
    fun loadColorblind(): Boolean

    /** Persists the sound flag. */
    fun saveSound(enabled: Boolean)

    /** Persists the haptics flag. */
    fun saveHaptics(enabled: Boolean)

    /** Persists the reduced-motion flag. */
    fun saveReducedMotion(enabled: Boolean)

    /** Persists the colorblind flag. */
    fun saveColorblind(enabled: Boolean)

    companion object {
        /** Out-of-the-box: sound ON (opt-out). */
        const val DEFAULT_SOUND: Boolean = true

        /** Out-of-the-box: haptics ON (opt-out). */
        const val DEFAULT_HAPTICS: Boolean = true

        /** Out-of-the-box: full motion (reduced motion OFF, opt-in). */
        const val DEFAULT_REDUCED_MOTION: Boolean = false

        /** Out-of-the-box: standard palette (colorblind OFF, opt-in). */
        const val DEFAULT_COLORBLIND: Boolean = false
    }
}

/**
 * SHL-3 — [FeedbackPreferences] backed by a `multiplatform-settings` [Settings] instance.
 *
 * The platform supplies the [Settings] (Android `SharedPreferencesSettings` over the `"fuse"`
 * prefs file, iOS `NSUserDefaultsSettings`) through Koin — the SAME instance
 * [com.fuse.data.SettingsGameRepository] uses, so all of Fuse's local state lives in one store.
 * Keys are namespaced under `fuse.settings.*` so they never collide with the game blob/best
 * keys (`fuse.game.current` / `fuse.score.best`).
 *
 * @param settings the platform key-value store (injected per platform via Koin).
 */
class SettingsFeedbackPreferences(
    private val settings: Settings,
) : FeedbackPreferences {

    override fun loadSound(): Boolean =
        settings.getBoolean(KEY_SOUND, FeedbackPreferences.DEFAULT_SOUND)

    override fun loadHaptics(): Boolean =
        settings.getBoolean(KEY_HAPTICS, FeedbackPreferences.DEFAULT_HAPTICS)

    override fun loadReducedMotion(): Boolean =
        settings.getBoolean(KEY_REDUCED_MOTION, FeedbackPreferences.DEFAULT_REDUCED_MOTION)

    override fun loadColorblind(): Boolean =
        settings.getBoolean(KEY_COLORBLIND, FeedbackPreferences.DEFAULT_COLORBLIND)

    override fun saveSound(enabled: Boolean) = settings.putBoolean(KEY_SOUND, enabled)

    override fun saveHaptics(enabled: Boolean) = settings.putBoolean(KEY_HAPTICS, enabled)

    override fun saveReducedMotion(enabled: Boolean) =
        settings.putBoolean(KEY_REDUCED_MOTION, enabled)

    override fun saveColorblind(enabled: Boolean) = settings.putBoolean(KEY_COLORBLIND, enabled)

    companion object {
        /** Storage key for the sound toggle. */
        const val KEY_SOUND: String = "fuse.settings.sound"

        /** Storage key for the haptics toggle. */
        const val KEY_HAPTICS: String = "fuse.settings.haptics"

        /** Storage key for the reduced-motion toggle. */
        const val KEY_REDUCED_MOTION: String = "fuse.settings.reducedMotion"

        /** Storage key for the colorblind-mode toggle. */
        const val KEY_COLORBLIND: String = "fuse.settings.colorblind"
    }
}

/**
 * SHL-3 — a no-op, never-persisting [FeedbackPreferences].
 *
 * Used as the default for the settings holders so existing holder/coordinator tests (and
 * previews) construct without a real [Settings]: every `load*` returns the documented default
 * and every `save*` does nothing. Mirrors `com.fuse.data.NoOpGameRepository`.
 */
object NoOpFeedbackPreferences : FeedbackPreferences {
    override fun loadSound(): Boolean = FeedbackPreferences.DEFAULT_SOUND
    override fun loadHaptics(): Boolean = FeedbackPreferences.DEFAULT_HAPTICS
    override fun loadReducedMotion(): Boolean = FeedbackPreferences.DEFAULT_REDUCED_MOTION
    override fun loadColorblind(): Boolean = FeedbackPreferences.DEFAULT_COLORBLIND
    override fun saveSound(enabled: Boolean) = Unit
    override fun saveHaptics(enabled: Boolean) = Unit
    override fun saveReducedMotion(enabled: Boolean) = Unit
    override fun saveColorblind(enabled: Boolean) = Unit
}
