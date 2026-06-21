package com.fuse.data

import com.fuse.daily.DailyStreak
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * DLY-5 — local persistence for the **daily streak** ([DailyStreak]).
 *
 * Mirrors [SettingsDailyRepository] (the in-progress puzzle slot) in shape, but is a
 * SEPARATE store under its OWN key ([SettingsDailyStreakRepository.KEY_STREAK] =
 * `fuse.daily.streak`, distinct from `fuse.daily.progress`). The two lifetimes are
 * deliberately decoupled: the in-progress slot rolls over / resets every UTC day, while the
 * streak must OUTLIVE any single day's puzzle (it spans the whole history of completions).
 * Keeping them under different keys means a new-day reset of the puzzle slot never touches
 * the streak — exactly the "persisted separately from the in-progress puzzle slot"
 * acceptance criterion.
 *
 * Persistence must never crash the app, so [loadStreak] tolerates a missing or corrupt blob
 * by returning a zeroed [DailyStreak] (never played yet).
 */
interface DailyStreakRepository {
    /** Persists the [streak], overwriting any prior value. */
    fun saveStreak(streak: DailyStreak)

    /**
     * Loads the saved streak, or a zeroed [DailyStreak] if none is stored or the blob
     * cannot be decoded (missing/corrupt → "never played", never a crash).
     */
    fun loadStreak(): DailyStreak
}

/**
 * DLY-5 — [DailyStreakRepository] backed by a multiplatform-settings [Settings] + JSON.
 *
 * Uses the SAME platform [Settings] instance (via Koin) as the game/daily/feedback prefs,
 * but under its own [KEY_STREAK] so Daily's streak never collides with the in-progress
 * puzzle slot or Classic's blob. Synchronous writes, matching the rest of Fuse's
 * `Settings`-backed persistence.
 *
 * @param settings the platform key-value store (injected per platform via Koin).
 * @param json the serializer; lenient so a slightly-different blob decodes where possible
 *   and is otherwise rejected cleanly (→ zeroed streak in [loadStreak]).
 */
class SettingsDailyStreakRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : DailyStreakRepository {

    override fun saveStreak(streak: DailyStreak) {
        settings.putString(KEY_STREAK, json.encodeToString(DailyStreak.serializer(), streak))
    }

    override fun loadStreak(): DailyStreak {
        val blob = settings.getStringOrNull(KEY_STREAK) ?: return DailyStreak()
        return runCatching { json.decodeFromString(DailyStreak.serializer(), blob) }
            .getOrNull() ?: DailyStreak()
    }

    companion object {
        /**
         * Storage key for the daily streak blob. DISTINCT from
         * [SettingsDailyRepository.KEY_DAILY] (`fuse.daily.progress`) so the streak's
         * lifetime is fully decoupled from the per-day puzzle slot.
         */
        const val KEY_STREAK: String = "fuse.daily.streak"

        /** Lenient JSON: tolerate a slightly newer blob; a genuinely bad one fails → zeroed. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * DLY-5 — a no-op, never-persisting [DailyStreakRepository].
 *
 * The default for the streak store/recorder so tests/previews construct without a real
 * [Settings]: [loadStreak] returns a zeroed [DailyStreak] and [saveStreak] does nothing.
 */
object NoOpDailyStreakRepository : DailyStreakRepository {
    override fun saveStreak(streak: DailyStreak) = Unit
    override fun loadStreak(): DailyStreak = DailyStreak()
}
