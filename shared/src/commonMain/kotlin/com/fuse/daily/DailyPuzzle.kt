package com.fuse.daily

import com.fuse.engine.Board
import com.fuse.engine.SeededRng
import com.fuse.engine.Tile
import com.fuse.engine.TileIdSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * DLY-3 — the deterministic daily puzzle generator.
 *
 * The Daily Challenge is a DETERMINISTIC, NO-SPAWN puzzle: every player on a given
 * UTC day gets the *same* seed-derived start board and must form the *same*
 * seed-derived target tile in the fewest moves. This file turns a daily seed (from
 * DLY-1's [dateToSeed]) into a concrete, **guaranteed-solvable** puzzle with a known
 * **par** (optimal move count, from DLY-2's [solve]) that falls inside a sensible
 * difficulty band.
 *
 * Everything here is 100% PURE and CROSS-PLATFORM DETERMINISTIC: the whole puzzle is
 * driven from a single engine [SeededRng] seeded with the daily seed, using only
 * pure engine ops (no `kotlin.random`, no platform calls, no platform-dependent
 * hashing). The same seed therefore produces the byte-for-byte same [DailyPuzzle] on
 * the JVM and on Kotlin/Native (iOS) — pinned by golden assertions in
 * `DailyPuzzleTest`, which runs on both targets.
 */

/**
 * A concrete daily puzzle: a solvable [startBoard], the [target] tile to form, and
 * the [par] (optimal number of no-spawn moves to reach the target, from [solve]).
 *
 * Fully [Serializable] (its [Board] / [Tile] members already are) so DLY-4 can
 * persist the day's puzzle in its own save slot and DLY-7 can put [target] + the
 * player's moves-vs-[par] on the share card.
 *
 * Invariants (held by [generateDailyPuzzle]):
 *  - `solve(startBoard, target).solvable == true`
 *  - `solve(startBoard, target).parMoves == par`
 *  - `target` is one of [DAILY_TARGETS]
 *
 * @property seed the daily seed this puzzle was generated from (the full identity:
 *   same seed -> same puzzle; carried so callers can label / verify it).
 * @property startBoard the no-spawn start board (preset tiles only; the player
 *   slides/merges via [Board.puzzleStep], no new tiles ever appear).
 * @property target the tile value the player must form to win (a [DAILY_TARGETS] entry).
 * @property par the optimal move count to reach [target] from [startBoard]; the
 *   player's score is their moves measured against this.
 */
@Serializable
data class DailyPuzzle(
    val seed: Long,
    val startBoard: Board,
    val target: Int,
    val par: Int,
)

/**
 * The per-day target pool. The day's target is chosen from this set (weighted by
 * [TARGET_WEIGHTS]) so the goal **varies per day** while staying achievable on a
 * small board. Ordered ascending; values are powers of two so they are reachable by
 * 2048 merges.
 */
val DAILY_TARGETS: List<Int> = listOf(32, 64, 128, 256)

/**
 * Selection weights for [DAILY_TARGETS], index-aligned. Lower targets are weighted
 * more heavily: they are the most reliable to band as non-trivial-yet-achievable
 * puzzles, while 256 still appears often enough to vary the daily goal. The weights
 * only affect *which* target a seed lands on; they never affect solvability.
 */
private val TARGET_WEIGHTS: List<Int> = listOf(4, 4, 3, 2) // 32,64 common; 128 medium; 256 rarer

/**
 * Difficulty band for the daily par (inclusive). A puzzle is accepted only when its
 * optimal solution length [solve]s to a value in `MIN_PAR..MAX_PAR`: at least
 * [MIN_PAR] so the day is never a one-or-two-move giveaway, at most [MAX_PAR] so it
 * is always achievable in a sitting. [MAX_PAR] also bounds the solver's depth during
 * generation, so candidates harder than the band are rejected cheaply.
 */
const val MIN_PAR: Int = 3
const val MAX_PAR: Int = 10

/**
 * How many candidate boards [generateDailyPuzzle] will roll before falling back. Each
 * attempt draws fresh tiles from the *same* rng stream (so the whole process stays
 * deterministic) and runs the bounded [solve]. The cap guarantees termination; in
 * practice an in-band candidate is found in the first handful of attempts.
 */
private const val MAX_ATTEMPTS: Int = 400

/**
 * Inclusive range for how many preset tiles a candidate board carries. A 4x4 board
 * holds 16; this leaves headroom so slides/merges have room to work and the solver
 * stays cheap.
 */
private const val MIN_TILES: Int = 5
private const val MAX_TILES: Int = 9

/**
 * The candidate tile-value palette for a given [target]: powers of two from 2 up to
 * `target / 2`. Including values close to the target is what makes the *higher*
 * targets (128, 256) reachable in-band on a small board — a couple of `target/2`
 * tiles merge straight to the goal, while the smaller values add the slides/merges
 * that lift par into the band. Drawing is biased toward the *larger* values (see
 * [drawTileValue]) so high targets are actually buildable.
 */
private fun paletteFor(target: Int): IntArray {
    val out = ArrayList<Int>()
    var v = 2
    while (v <= target / 2) {
        out.add(v)
        v *= 2
    }
    return out.toIntArray()
}

/**
 * Generates the deterministic daily puzzle for [seed].
 *
 * ## Algorithm (forward-generate + solver-filter, fully deterministic)
 * Everything is driven from one `SeededRng(seed)`, so the result is reproducible
 * from the seed alone and identical on every platform.
 *
 *  1. **Pick the target** for the day from [DAILY_TARGETS], weighted by
 *     [TARGET_WEIGHTS] (the per-day variety). One rng draw.
 *  2. **Roll a candidate start board**: choose a tile count in `MIN_TILES..MAX_TILES`,
 *     then repeatedly pick a value from the target's palette ([paletteFor], biased
 *     toward larger values via [drawTileValue]) and a random empty cell and place a
 *     tile there (ids from a [TileIdSource]; ids never affect solving). All choices
 *     come from the same rng stream.
 *  3. **Validate** with `solve(candidate, target, maxMoves = MAX_PAR)`: accept iff it
 *     is `solvable` AND `parMoves in MIN_PAR..MAX_PAR`. Capping the solver's depth at
 *     [MAX_PAR] makes too-hard candidates reject quickly. Otherwise the loop re-rolls
 *     the *next* candidate from the same stream and retries, up to [MAX_ATTEMPTS].
 *  4. While searching, remember the **best solvable candidate seen** — the one whose
 *     par is closest to the band (ties prefer the larger par, i.e. the harder, more
 *     interesting puzzle).
 *
 * ## Termination & guarantee (never loops; never returns unsolvable)
 *  - The loop is bounded by [MAX_ATTEMPTS], so it always terminates.
 *  - If an in-band candidate is found, it is returned immediately.
 *  - Else, if any solvable candidate was seen, the best one (par nearest the band) is
 *    returned — solvable, with its true par (which may fall just outside the band).
 *  - Else (no candidate solved within depth [MAX_PAR] at all — not expected for these
 *    small boards), a **constructed-solvable** fallback is returned: a board with the
 *    target's two immediate predecessors adjacent so a single merge chain reaches the
 *    target, re-[solve]d to report its true par. This last resort can never be
 *    unsolvable, so the function ALWAYS returns a solvable [DailyPuzzle].
 *
 * The returned puzzle's [DailyPuzzle.par] is always the solver's `parMoves` for the
 * chosen board+target, so `solve(p.startBoard, p.target).parMoves == p.par` holds.
 *
 * @param seed the daily seed (from [dateToSeed]); any [Long] is valid.
 * @return a guaranteed-solvable [DailyPuzzle] deterministic in [seed].
 */
fun generateDailyPuzzle(seed: Long): DailyPuzzle {
    val rng = SeededRng(seed)
    val ids = TileIdSource()

    val target = pickTarget(rng)

    var best: DailyPuzzle? = null
    var bestDistance = Int.MAX_VALUE

    val palette = paletteFor(target)

    repeat(MAX_ATTEMPTS) {
        val candidate = rollCandidateBoard(rng, ids, palette)
        // Tighten the solver depth to the band's upper bound: anything that needs
        // more than MAX_PAR moves is out of band, so we don't waste search on it.
        val solution = solve(candidate, target, maxMoves = MAX_PAR)
        if (solution.solvable) {
            val par = solution.parMoves!!
            if (par in MIN_PAR..MAX_PAR) {
                return DailyPuzzle(seed = seed, startBoard = candidate, target = target, par = par)
            }
            // Track the best out-of-band-but-solvable candidate as a fallback. Par
            // here is <= MAX_PAR (depth-capped), so "out of band" means par < MIN_PAR.
            val distance = bandDistance(par)
            // Tie-break toward the larger par (the more interesting near-miss).
            if (distance < bestDistance || (distance == bestDistance && best != null && par > best!!.par)) {
                bestDistance = distance
                best = DailyPuzzle(seed = seed, startBoard = candidate, target = target, par = par)
            }
        }
    }

    best?.let { return it }

    // Last-resort constructed-solvable fallback (not expected to be reached for these
    // small boards). Build a board whose tiles merge straight up to the target, then
    // re-solve to report its real par.
    val constructed = constructSolvableBoard(target, ids)
    val solution = solve(constructed, target)
    val par = solution.parMoves ?: 1 // constructed board is solvable by design.
    return DailyPuzzle(seed = seed, startBoard = constructed, target = target, par = par)
}

/**
 * Convenience: the daily puzzle for a UTC calendar [date], i.e.
 * `generateDailyPuzzle(dateToSeed(date))`. Same date -> same seed -> same puzzle on
 * every platform.
 */
fun dailyPuzzleFor(date: LocalDate): DailyPuzzle = generateDailyPuzzle(dateToSeed(date))

// ---------------------------------------------------------------------------

/** How far [par] sits below the band (0 inside the band). */
private fun bandDistance(par: Int): Int = when {
    par < MIN_PAR -> MIN_PAR - par
    par > MAX_PAR -> par - MAX_PAR
    else -> 0
}

/** Picks a target from [DAILY_TARGETS] using [TARGET_WEIGHTS] and one rng draw. */
private fun pickTarget(rng: SeededRng): Int {
    val total = TARGET_WEIGHTS.sum()
    var roll = rng.nextInt(total)
    for (i in DAILY_TARGETS.indices) {
        roll -= TARGET_WEIGHTS[i]
        if (roll < 0) return DAILY_TARGETS[i]
    }
    return DAILY_TARGETS.last() // unreachable; defensive.
}

/**
 * Rolls one candidate start board: a tile count in `MIN_TILES..MAX_TILES`, then that
 * many tiles drawn from [palette] placed in random empty cells. All draws come from
 * [rng] (so consecutive attempts advance the same deterministic stream) and ids from
 * [ids]. Values are biased toward the larger palette entries (see [drawTileValue]) so
 * high targets are buildable in-band on a small board.
 */
private fun rollCandidateBoard(rng: SeededRng, ids: TileIdSource, palette: IntArray): Board {
    var board = Board.empty()
    val count = MIN_TILES + rng.nextInt(MAX_TILES - MIN_TILES + 1)
    repeat(count) {
        val empties = board.emptyCells()
        if (empties.isEmpty()) return@repeat
        val cell = empties[rng.nextInt(empties.size)]
        val value = drawTileValue(rng, palette)
        board = board.withTile(cell.row, cell.col, Tile(value, ids.next()))
    }
    return board
}

/**
 * Draws one tile value from [palette] with a bias toward the *larger* values. We draw
 * an index `max(i, j)` of two independent uniform indices: this triangular weighting
 * makes the top of the palette (values near `target/2`) the most common, which is what
 * lets a small board actually assemble a high target while still mixing in smaller
 * tiles for the lower-target days. Deterministic: exactly two rng draws per tile.
 */
private fun drawTileValue(rng: SeededRng, palette: IntArray): Int {
    val i = rng.nextInt(palette.size)
    val j = rng.nextInt(palette.size)
    return palette[if (i >= j) i else j]
}

/**
 * Builds a board that is solvable for [target] by construction: places the two
 * immediate predecessors of [target] (e.g. for 64: two 32s) adjacent on the top row
 * so a single Up+ merge chain forms [target]. Used only as the ultimate fallback.
 */
private fun constructSolvableBoard(target: Int, ids: TileIdSource): Board {
    val half = target / 2
    return Board.empty()
        .withTile(0, 0, Tile(half, ids.next()))
        .withTile(0, 1, Tile(half, ids.next()))
}
