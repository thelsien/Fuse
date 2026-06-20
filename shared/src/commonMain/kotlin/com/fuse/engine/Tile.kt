package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * A single game tile: a power-of-two [value] together with a stable [id].
 *
 * The [id] is what gives the UI animation continuity across moves:
 *  - A tile that **slides** keeps its id (it is the "same" tile that moved).
 *  - A tile produced by a **merge** (ENG-4) is minted with a NEW id, so the UI
 *    can animate the two source tiles into the destination and then swap in the
 *    fresh merged tile.
 *  - A tile **spawned** after a move (ENG-6) is also minted with a NEW id.
 *
 * Equality is structural over (`value`, `id`): two tiles are equal iff they have
 * the same value and the same id. Because this is a `data class`, [copy],
 * [equals], [hashCode] and value semantics come for free. Ids are injected by
 * the caller (see [TileIdSource]) so tests can pin them deterministically.
 */
@Serializable
data class Tile(
    val value: Int,
    val id: Long,
)

/**
 * Monotonic source of stable tile ids, threaded through the engine.
 *
 * ENG-4 (merges) and ENG-6 (spawns) call [next] to mint a fresh id whenever a
 * brand-new logical tile comes into existence. It is intentionally tiny and
 * deterministic: starting from the same [start] value yields the same id
 * sequence, which keeps replays (ENG-9) reproducible. The model itself never
 * calls this implicitly — ids are always passed in explicitly — so unit tests
 * can construct tiles with hand-chosen ids and assert on them.
 *
 * Not thread-safe; the engine is single-threaded per game.
 */
class TileIdSource(start: Long = 1L) {
    private var counter: Long = start

    /** Returns the next id and advances the counter. */
    fun next(): Long = counter++

    /** The id that will be returned by the next call to [next] (for snapshots). */
    fun peek(): Long = counter
}
