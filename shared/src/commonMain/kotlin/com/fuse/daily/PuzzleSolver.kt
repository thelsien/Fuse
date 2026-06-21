package com.fuse.daily

import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.TileIdSource
import com.fuse.engine.move

/**
 * DLY-2 — the no-spawn puzzle step + the optimal-par solver.
 *
 * The Daily Challenge is a DETERMINISTIC, NO-SPAWN puzzle: a fixed seed-derived
 * start board whose preset tiles only slide/merge (no random spawns), and the
 * goal is to form a TARGET tile in the FEWEST moves. This file provides the two
 * engine pieces that goal needs:
 *
 *  1. [puzzleStep] — a thin no-spawn move: apply a [Direction] to a board with
 *     slide + merge and NO new tile. It delegates entirely to the engine's pure
 *     geometric [Board.move]; spawning is a separate concern Classic owns, so a
 *     puzzle step is exactly "move and ignore spawn".
 *  2. [solve] — a BFS solver that, given a start board + a target value, reports
 *     whether the target is reachable, the minimum number of moves ("par"), and
 *     one optimal move sequence. The generator (DLY-3) calls this to validate /
 *     band daily boards; the mode (DLY-4) uses [puzzleStep] + a move counter.
 *
 * Everything here is 100% PURE: no Compose/Koin/platform/RNG-of-its-own. The
 * solver is deterministic — same inputs always yield the same par and path
 * (directions are expanded in a fixed [Direction.entries] order, so ties break
 * identically every run, on every platform).
 */

/**
 * The result of applying a single no-spawn puzzle step ([puzzleStep]).
 *
 * @property board the board after sliding + merging (NO spawned tile).
 * @property changed `true` iff the move actually moved/merged something; when
 *   `false` the board is structurally unchanged and the mode should not count a
 *   turn (an unproductive direction is not a move).
 */
data class PuzzleStepResult(
    val board: Board,
    val changed: Boolean,
)

/**
 * Applies one NO-SPAWN puzzle move in [direction]: slide + merge, no new tile.
 *
 * This is the puzzle counterpart to Classic's `moveAndSpawn`: it delegates to the
 * engine's pure geometric [Board.move] and simply drops the spawn step. Tile ids
 * are irrelevant to a puzzle, so callers may pass their own [idSource] (the mode
 * uses one for animation continuity) or rely on the default throwaway source.
 *
 * @param direction the direction to slide/merge toward.
 * @param idSource minter for merged-tile ids; defaults to a fresh throwaway
 *   source (ids don't affect the puzzle outcome, only animation).
 * @return the new board + whether the move changed anything.
 */
fun Board.puzzleStep(
    direction: Direction,
    idSource: TileIdSource = TileIdSource(),
): PuzzleStepResult {
    val result = move(direction, idSource)
    return PuzzleStepResult(board = result.board, changed = result.changed)
}

/**
 * The outcome of [solve]: is the target reachable from the start within the search
 * bound, and if so how few moves does it take?
 *
 * @property solvable `true` iff a state with a tile `>= target` was reached within
 *   the search bound. When the bound (depth / visited cap) is hit WITHOUT finding
 *   the target, this is reported `false` (i.e. "not solvable within the bound" —
 *   see [solve] for the boundedness contract).
 * @property parMoves the minimum number of moves to reach the target (BFS
 *   guarantees this is optimal), or `null` when [solvable] is `false`.
 * @property optimalPath one optimal move sequence whose length equals [parMoves],
 *   or `null` when unsolvable. Applying these directions in order via [puzzleStep]
 *   from the start board reaches a solved state. An EMPTY list means the start
 *   board already satisfies the target (par 0).
 */
data class PuzzleSolution(
    val solvable: Boolean,
    val parMoves: Int?,
    val optimalPath: List<Direction>?,
) {
    companion object {
        /** The canonical "no solution within the bound" result. */
        val UNSOLVABLE: PuzzleSolution = PuzzleSolution(false, null, null)
    }
}

/** Default depth cap for [solve]. No-spawn boards (a handful of tiles) solve far
 *  below this; the cap only protects against pathological inputs. */
const val DEFAULT_SOLVER_MAX_MOVES: Int = 64

/** Default cap on the number of distinct board states the solver will visit
 *  before giving up and reporting unsolvable. Keeps generation (DLY-3, many
 *  calls) bounded against a degenerate board. */
const val DEFAULT_SOLVER_MAX_STATES: Int = 200_000

/**
 * Breadth-first solver for the no-spawn puzzle.
 *
 * ## Win condition
 * A state is SOLVED when its maximum tile value is `>= target`. `>=` (not `==`) is
 * correct because in 2048 you cannot form a value larger than [target] without
 * first forming [target] itself — so the first time any tile reaches `>= target`,
 * a [target] tile necessarily existed on that path. Using `>=` is both correct and
 * the simplest predicate. (A start board that ALREADY satisfies this returns par 0
 * with an empty path.)
 *
 * ## Search
 * BFS over no-spawn board STATES from [start], expanding the four [Direction]s at
 * each state and skipping any direction that doesn't change the board. BFS visits
 * states in nondecreasing move-count, so the FIRST time a solved state is dequeued
 * its depth is the minimum number of moves — the par. The path is reconstructed
 * from parent links.
 *
 * ## State canonicalization (critical)
 * Tile IDS are irrelevant to solving: two boards with the same VALUE layout are the
 * same puzzle state. The visited set is therefore keyed on the canonical VALUE grid
 * (a [List] of cell values, `0` for empty) — NOT on [Board.equals], which includes
 * ids and would treat id-only-different boards as distinct, defeating dedup and
 * blowing up the search. A throwaway [TileIdSource] is used for the moves so the
 * solver never perturbs any real id state.
 *
 * ## Boundedness / performance
 * No-spawn moves never INCREASE the tile count (merges only reduce it), so the
 * reachable state space is finite. The search is additionally bounded two ways:
 *  - **[maxMoves]** — a depth cap; states deeper than this are not expanded.
 *  - **[maxStates]** — a cap on distinct visited states.
 * If either bound is reached WITHOUT solving, the result is [PuzzleSolution.UNSOLVABLE]
 * (`solvable = false`). So a `false` result means "not solvable within the given
 * bound" — for typical small daily boards the bound is never close to being hit, so
 * in practice `false` means genuinely unsolvable. The default bounds
 * ([DEFAULT_SOLVER_MAX_MOVES], [DEFAULT_SOLVER_MAX_STATES]) are generous for daily
 * boards yet cheap enough to call repeatedly at generation time.
 *
 * @param start the puzzle start board (preset tiles; no spawning happens).
 * @param target the tile value the puzzle must form to be solved.
 * @param maxMoves depth cap (default [DEFAULT_SOLVER_MAX_MOVES]).
 * @param maxStates distinct-state cap (default [DEFAULT_SOLVER_MAX_STATES]).
 * @return a [PuzzleSolution] reporting solvability, par, and one optimal path.
 */
fun solve(
    start: Board,
    target: Int,
    maxMoves: Int = DEFAULT_SOLVER_MAX_MOVES,
    maxStates: Int = DEFAULT_SOLVER_MAX_STATES,
): PuzzleSolution {
    require(maxMoves >= 0) { "maxMoves must be non-negative, was $maxMoves" }
    require(maxStates >= 1) { "maxStates must be at least 1, was $maxStates" }

    // Solved already? (par 0, empty path.)
    if (start.maxTileValue() >= target) {
        return PuzzleSolution(solvable = true, parMoves = 0, optimalPath = emptyList())
    }

    // BFS. Each visited state remembers its predecessor (state key) and the
    // direction taken to reach it, so an optimal path can be reconstructed.
    data class Node(val board: Board, val depth: Int)

    val startKey = start.valueKey()
    val parentKey = HashMap<List<Int>, List<Int>?>()
    val parentDir = HashMap<List<Int>, Direction>()
    parentKey[startKey] = null

    val queue = ArrayDeque<Node>()
    queue.add(Node(start, 0))

    val ids = TileIdSource() // throwaway; ids never affect the outcome.

    while (queue.isNotEmpty()) {
        val (board, depth) = queue.removeFirst()
        if (depth >= maxMoves) continue // don't expand beyond the depth cap.

        for (direction in Direction.entries) {
            val stepped = board.puzzleStep(direction, ids)
            if (!stepped.changed) continue

            val next = stepped.board
            val key = next.valueKey()
            if (parentKey.containsKey(key)) continue // already seen this value layout.

            parentKey[key] = board.valueKey()
            parentDir[key] = direction

            if (next.maxTileValue() >= target) {
                return PuzzleSolution(
                    solvable = true,
                    parMoves = depth + 1,
                    optimalPath = reconstructPath(key, startKey, parentKey, parentDir),
                )
            }

            if (parentKey.size > maxStates) return PuzzleSolution.UNSOLVABLE
            queue.add(Node(next, depth + 1))
        }
    }

    // Queue exhausted (or bound hit) without reaching the target.
    return PuzzleSolution.UNSOLVABLE
}

/**
 * Rebuilds the move sequence from [startKey] to [endKey] by walking parent links
 * backward, then reversing. Returns the directions in play order.
 */
private fun reconstructPath(
    endKey: List<Int>,
    startKey: List<Int>,
    parentKey: Map<List<Int>, List<Int>?>,
    parentDir: Map<List<Int>, Direction>,
): List<Direction> {
    val reversed = ArrayList<Direction>()
    var cursor: List<Int>? = endKey
    while (cursor != null && cursor != startKey) {
        reversed.add(parentDir.getValue(cursor))
        cursor = parentKey[cursor]
    }
    reversed.reverse()
    return reversed
}

/**
 * The canonical value-only key for a board: every cell's tile value in row-major
 * order, `0` for empty. Identical for any two boards with the same value layout
 * regardless of tile ids — this is what the visited set dedups on.
 */
private fun Board.valueKey(): List<Int> {
    val n = size
    val key = ArrayList<Int>(cellCount)
    for (row in 0 until n) {
        for (col in 0 until n) {
            key.add(this[row, col]?.value ?: 0)
        }
    }
    return key
}

/** The largest tile value on the board, or `0` for an empty board. */
private fun Board.maxTileValue(): Int = tiles().maxOfOrNull { it.value } ?: 0
