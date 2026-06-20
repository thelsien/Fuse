package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * The result of attempting to spawn a tile after a move (ENG-6).
 *
 * Spawning is the only post-move source of chance in the engine, and it is fully
 * deterministic given an injected [Rng] + [TileIdSource]. This type is the
 * description a caller (ENG-9 state machine) and the spawn animation (Sprint 3)
 * inspect to learn WHAT appeared and WHERE.
 *
 * When the board has no empty cell, nothing spawns: [spawned] is `null` and
 * [position] is `null`, while [board] is the (unchanged) input board. Callers
 * must therefore null-check rather than assume a tile always appears — see
 * [spawnedOrNull].
 *
 * @property board the board after the spawn (identical to the input when nothing
 *   spawned). Always non-null so callers can keep threading a board through.
 * @property spawned the freshly minted [Tile] (value 2 or 4, new id), or `null`
 *   if the board was full and nothing spawned.
 * @property position the board [Position] the tile was placed at, or `null` if
 *   nothing spawned.
 */
@Serializable
data class SpawnResult(
    val board: Board,
    val spawned: Tile?,
    val position: Position?,
) {
    /** True iff a tile was actually placed (board had at least one empty cell). */
    val didSpawn: Boolean get() = spawned != null
}

/**
 * Spawns exactly ONE new tile into a random empty cell of this board (ENG-6).
 *
 * This is the pure, independently-testable spawn primitive. It does NOT check
 * whether a preceding move "changed" the board — that policy belongs to the
 * caller (see [moveAndSpawn] and the note below). Calling this on a board with a
 * free cell ALWAYS spawns; calling it on a full board NEVER throws and spawns
 * nothing.
 *
 * ## RNG threading (deterministic)
 * Two draws are consumed from [rng], in this fixed order (matching the
 * convention sketched in [Rng] / `RngTest`):
 *  1. `rng.nextInt(emptyCells.size)` picks which empty cell (row-major order)
 *     receives the tile.
 *  2. `rng.nextDouble()` rolls the value: `< 0.9 -> 2`, otherwise `4`
 *     (so 2 appears ~90% of the time, 4 ~10%). The boundary value `0.9` itself
 *     yields a 4 (the comparison is strictly `<`).
 *
 * On a full board NO draws are consumed (the empty-cell check short-circuits
 * before touching [rng]), so a caller that only spawns on changed moves never
 * desyncs the RNG stream on a no-op move.
 *
 * ## Id threading (for ENG-9)
 * The spawned tile's id is minted from [idSource]. ENG-9 MUST pass the SAME
 * [TileIdSource] instance it threaded through [Board.move], so spawn ids never
 * collide with the fresh ids merges minted during the same turn.
 *
 * ## Immutability
 * Pure — the receiver board is never mutated; a new board is returned inside the
 * [SpawnResult] (or the receiver itself, unchanged, when the board is full).
 *
 * @param rng the injected deterministic generator (cell pick + value roll).
 * @param idSource the monotonic id minter shared with [Board.move] for this turn.
 * @return a [SpawnResult] describing the placed tile, or a no-spawn result if full.
 */
fun Board.spawnTile(rng: Rng, idSource: TileIdSource): SpawnResult {
    val cells = emptyCells()
    if (cells.isEmpty()) {
        // Full board: spawn nothing, consume no RNG draws, never throw.
        return SpawnResult(board = this, spawned = null, position = null)
    }

    val position = cells[rng.nextInt(cells.size)]
    val value = if (rng.nextDouble() < SPAWN_TWO_PROBABILITY) SPAWN_VALUE_LOW else SPAWN_VALUE_HIGH
    val tile = Tile(value, idSource.next())
    val newBoard = withTile(position.row, position.col, tile)
    return SpawnResult(board = newBoard, spawned = tile, position = position)
}

/**
 * Convenience that composes ENG-5 [Board.move] with ENG-6 [spawnTile], applying
 * the "only spawn when the board changed" rule in one place.
 *
 * Runs [move] in [direction]; if (and only if) the move [MoveResult.changed],
 * spawns one tile into the resulting board via [spawnTile]. A no-op move returns
 * the original board with [MoveAndSpawnResult.spawn] reporting nothing spawned
 * and consumes NO RNG draws.
 *
 * ENG-9 may use this directly for the common "player swiped" path, or call
 * [move] and [spawnTile] separately when it needs finer control; both share the
 * same [idSource], keeping ids collision-free across merges and the spawn.
 *
 * @param direction the swipe direction.
 * @param rng injected generator for the spawn (untouched on a no-op move).
 * @param idSource id minter shared across the move's merges AND the spawn.
 * @return the [MoveResult] plus the [SpawnResult]; on a no-op move the spawn is a
 *   no-spawn result and `board` equals the (structurally unchanged) input board.
 */
fun Board.moveAndSpawn(
    direction: Direction,
    rng: Rng,
    idSource: TileIdSource,
): MoveAndSpawnResult {
    val move = move(direction, idSource)
    if (!move.changed) {
        // No-op move: do not spawn, do not draw from rng.
        return MoveAndSpawnResult(
            move = move,
            spawn = SpawnResult(board = move.board, spawned = null, position = null),
        )
    }
    val spawn = move.board.spawnTile(rng, idSource)
    return MoveAndSpawnResult(move = move, spawn = spawn)
}

/**
 * The combined outcome of [moveAndSpawn]: the geometric [move] (slide + merges)
 * and the [spawn] that followed it. [spawn].board is the final board for the turn
 * (post-move AND post-spawn); on a no-op move it equals the input board and
 * [spawn].didSpawn is `false`.
 */
@Serializable
data class MoveAndSpawnResult(
    val move: MoveResult,
    val spawn: SpawnResult,
) {
    /** The final board for the turn (after move + any spawn). */
    val board: Board get() = spawn.board

    /** True iff the move was a real move (and therefore a tile was spawned). */
    val changed: Boolean get() = move.changed
}

/** Probability of a spawned tile being a 2 (vs a 4). Roll: `nextDouble() < this`. */
private const val SPAWN_TWO_PROBABILITY: Double = 0.9

/** The common spawn value, rolled with probability [SPAWN_TWO_PROBABILITY]. */
private const val SPAWN_VALUE_LOW: Int = 2

/** The rare spawn value, rolled the rest of the time. */
private const val SPAWN_VALUE_HIGH: Int = 4
