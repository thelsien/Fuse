package com.fuse.data

import com.fuse.engine.Direction
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DLY-4 — the **single daily save slot**: the in-progress run of today's Daily puzzle.
 *
 * ## Why a tiny move-list slot (not a board blob)
 * The Daily is a DETERMINISTIC, NO-SPAWN puzzle: today's `startBoard` is regenerable
 * from the date alone (DLY-3's `dailyPuzzleFor`), and every accepted move is replayable
 * via `Board.puzzleStep` (DLY-2). So the entire run is captured by **which day it is**
 * plus **the ordered list of moves played** — the board is just `startBoard` replayed
 * through [moves]. This keeps the slot minimal, makes the write-through after every move
 * cheap and crash-safe, and gives **undo for free** (drop the last move and replay).
 *
 * ## The "single slot, not keyed per-date" contract (NEW-DAY RESET)
 * There is exactly ONE daily slot under ONE key ([SettingsDailyRepository.KEY_DAILY]).
 * It carries the [dayNumber] it belongs to. On open the store compares it to today's
 * day number: a match resumes this run; a mismatch (a new UTC day has begun) means the
 * slot is stale — it is discarded and overwritten with a fresh slot for today's puzzle.
 * The slot is never keyed per-date, so yesterday's progress simply rolls over.
 *
 * @property dayNumber the Daily #N this run belongs to (from `dailyDayNumber(today)`);
 *   the new-day reset key.
 * @property moves the ordered no-spawn moves played so far; the board is `startBoard`
 *   replayed through these, and `moves.size` is the move counter.
 * @property solved `true` once today's puzzle was solved (a tile reached the target);
 *   the run is then LOCKED (one-shot until tomorrow) and [moves] holds the winning path.
 */
@Serializable
data class DailyProgress(
    val dayNumber: Long,
    val moves: List<Direction> = emptyList(),
    val solved: Boolean = false,
)

/**
 * DLY-4 — local persistence for the single daily save slot ([DailyProgress]).
 *
 * Mirrors [GameRepository]/[SettingsGameRepository] in shape and intent, but is a
 * SEPARATE store under its own key so it never touches Classic's `fuse.game.current`
 * slot — Daily and Classic are independent. The contract is intentionally tiny: load,
 * save, clear the one slot. Persistence must never crash the app, so [load] tolerates a
 * missing or corrupt blob by returning `null` (the store then starts today fresh).
 */
interface DailyRepository {
    /** Persists the single in-progress daily run, overwriting any prior slot. */
    fun save(progress: DailyProgress)

    /**
     * Loads the saved daily run, or `null` if none is stored or the blob cannot be
     * decoded (missing/corrupt → fresh start, never a crash).
     */
    fun load(): DailyProgress?

    /** Removes the saved daily run (e.g. an explicit reset). */
    fun clear()
}

/**
 * DLY-4 — [DailyRepository] backed by a multiplatform-settings [Settings] + JSON.
 *
 * The platform supplies the [Settings] (the SAME instance Classic and the feedback
 * prefs use) through Koin; this impl is platform-agnostic and writes synchronously to
 * match the synchronous reduce of the daily store.
 *
 * @param settings the platform key-value store (injected per platform via Koin).
 * @param json the serializer; lenient so a slightly-different blob decodes where
 *   possible and is otherwise rejected cleanly (→ `null` in [load]).
 */
class SettingsDailyRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : DailyRepository {

    override fun save(progress: DailyProgress) {
        settings.putString(KEY_DAILY, json.encodeToString(DailyProgress.serializer(), progress))
    }

    override fun load(): DailyProgress? {
        val blob = settings.getStringOrNull(KEY_DAILY) ?: return null
        return runCatching { json.decodeFromString(DailyProgress.serializer(), blob) }.getOrNull()
    }

    override fun clear() {
        settings.remove(KEY_DAILY)
    }

    companion object {
        /**
         * Storage key for the single daily run blob. DISTINCT from Classic's
         * [SettingsGameRepository.KEY_GAME] so the two modes never collide.
         */
        const val KEY_DAILY: String = "fuse.daily.progress"

        /** Lenient JSON: tolerate a slightly newer blob; a genuinely bad one fails → null. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * DLY-4 — a no-op, never-persisting [DailyRepository].
 *
 * The default for [com.fuse.presentation.DailyStore] so store tests / previews construct
 * without a real [Settings]: [load] returns `null` (always start today fresh) and the
 * mutators do nothing.
 */
object NoOpDailyRepository : DailyRepository {
    override fun save(progress: DailyProgress) = Unit
    override fun load(): DailyProgress? = null
    override fun clear() = Unit
}
