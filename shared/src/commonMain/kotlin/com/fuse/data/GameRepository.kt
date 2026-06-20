package com.fuse.data

import com.fuse.engine.GameState
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * UIB-6 — local persistence for the in-progress game and the cross-session best score.
 *
 * The contract is intentionally tiny: the engine's [GameState] is fully `@Serializable`
 * (board + tile ids + score + phase + rng/id state), so the entire in-progress game is
 * persisted as ONE JSON blob under a single key, and the best score is persisted
 * separately so it survives even when the in-progress game is cleared/finished.
 *
 * ## Why best is stored separately from the game blob
 * Best must outlive any single game: starting a brand-new game clears/overwrites the
 * game blob, but the best should still be remembered across launches. Keeping it under
 * its own key decouples the two lifetimes.
 *
 * ## Graceful degradation
 * Persistence must never crash the app. [loadGame] tolerates a missing key (fresh
 * install / cleared game) AND a corrupt/incompatible JSON blob (e.g. an old schema) by
 * returning `null` — the caller then starts a fresh game. [loadBest] defaults to `0`.
 */
interface GameRepository {
    /** Persists the whole in-progress [state] as a single JSON blob. */
    fun saveGame(state: GameState)

    /**
     * Loads the saved in-progress game, or `null` if none is stored or the stored blob
     * cannot be decoded (missing/corrupt → fresh start, never a crash).
     */
    fun loadGame(): GameState?

    /** Removes the saved in-progress game (e.g. on an explicit fresh start). */
    fun clearGame()

    /** Persists the cross-session [best] score (stored independently of the game blob). */
    fun saveBest(best: Long)

    /** Loads the persisted best score, or `0` if none has ever been stored. */
    fun loadBest(): Long
}

/**
 * UIB-6 — [GameRepository] backed by a multiplatform-settings [Settings] instance and
 * kotlinx-serialization JSON.
 *
 * The platform supplies the [Settings] (Android `SharedPreferencesSettings`, iOS
 * `NSUserDefaultsSettings`) through Koin; this impl is platform-agnostic. Writes are
 * synchronous — `Settings` is a fast key-value store, which matches the synchronous
 * reduce of the [com.fuse.presentation.GameStore] (see its KDoc).
 *
 * @param settings the platform key-value store (injected per platform via Koin).
 * @param json the serializer; defaults to a lenient, non-pretty config so an older or
 *   slightly-different blob is decoded where possible and otherwise rejected cleanly.
 */
class SettingsGameRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : GameRepository {

    override fun saveGame(state: GameState) {
        settings.putString(KEY_GAME, json.encodeToString(GameState.serializer(), state))
    }

    override fun loadGame(): GameState? {
        val blob = settings.getStringOrNull(KEY_GAME) ?: return null
        // Tolerate a corrupt / incompatible blob: decode failure → null (fresh start).
        return runCatching { json.decodeFromString(GameState.serializer(), blob) }.getOrNull()
    }

    override fun clearGame() {
        settings.remove(KEY_GAME)
    }

    override fun saveBest(best: Long) {
        settings.putLong(KEY_BEST, best)
    }

    override fun loadBest(): Long = settings.getLong(KEY_BEST, 0L)

    companion object {
        /** Storage key for the single in-progress game JSON blob. */
        const val KEY_GAME: String = "fuse.game.current"

        /** Storage key for the cross-session best score (survives clearGame). */
        const val KEY_BEST: String = "fuse.score.best"

        /**
         * Lenient JSON: `ignoreUnknownKeys` lets a blob written by a slightly newer
         * build still load; a genuinely incompatible blob fails to decode and is
         * caught in [loadGame] → `null`.
         */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * UIB-6 — a no-op, never-persisting [GameRepository].
 *
 * Used as the default for [com.fuse.presentation.GameStore] so existing store tests
 * (and previews) construct without a real [Settings]: [loadGame] returns `null` (always
 * a fresh game), [loadBest] returns `0`, and the save methods do nothing.
 */
object NoOpGameRepository : GameRepository {
    override fun saveGame(state: GameState) = Unit
    override fun loadGame(): GameState? = null
    override fun clearGame() = Unit
    override fun saveBest(best: Long) = Unit
    override fun loadBest(): Long = 0L
}
