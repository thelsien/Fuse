package com.fuse.data

import com.fuse.cosmetics.PlayerAchievements
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * COS-1 — local persistence for the [PlayerAchievements] record (the milestones that drive
 * cosmetic unlocks). Mirrors [SettingsDailyStreakRepository] in shape: the SAME platform
 * [Settings] (via Koin) under its OWN key ([SettingsAchievementsRepository.KEY] =
 * `fuse.achievements`), so achievements never collide with the game blob, daily slots, or the
 * equipped cosmetics. A missing/corrupt blob → a zeroed [PlayerAchievements] (never a crash).
 */
interface AchievementsRepository {
    /** Persists [achievements], overwriting any prior value. */
    fun save(achievements: PlayerAchievements)

    /** Loads the saved achievements, or a default (all-false) record if none/corrupt. */
    fun load(): PlayerAchievements
}

/**
 * COS-1 — [AchievementsRepository] backed by multiplatform-settings [Settings] + JSON.
 * Lenient JSON so an OLDER blob (missing a newly-added milestone field) still decodes to the
 * defaulted record, matching the rest of Fuse's `Settings`-backed persistence.
 */
class SettingsAchievementsRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : AchievementsRepository {

    override fun save(achievements: PlayerAchievements) {
        settings.putString(KEY, json.encodeToString(PlayerAchievements.serializer(), achievements))
    }

    override fun load(): PlayerAchievements {
        val blob = settings.getStringOrNull(KEY) ?: return PlayerAchievements()
        return runCatching { json.decodeFromString(PlayerAchievements.serializer(), blob) }
            .getOrNull() ?: PlayerAchievements()
    }

    companion object {
        /** Storage key for the achievements blob. Distinct from all other Fuse keys. */
        const val KEY: String = "fuse.achievements"

        /** Lenient JSON: tolerate a slightly newer/older blob; a genuinely bad one → default. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * COS-1 — a no-op, never-persisting [AchievementsRepository]: the default for the achievements
 * store so tests/previews construct without a real [Settings] ([load] returns the zeroed
 * record, [save] does nothing).
 */
object NoOpAchievementsRepository : AchievementsRepository {
    override fun save(achievements: PlayerAchievements) = Unit
    override fun load(): PlayerAchievements = PlayerAchievements()
}
