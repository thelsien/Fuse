package com.fuse.daily

import com.fuse.engine.Board
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DLY-3 — tests for the deterministic daily puzzle generator.
 *
 * These run in `commonTest`, so the SAME assertions execute on the JVM and on iOS
 * Native. The golden cases below therefore double as the cross-platform-determinism
 * proof: the hardcoded (seed -> target + par + board-values) tuples pass on both
 * targets, which can only happen if generation is byte-for-byte identical.
 */
class DailyPuzzleTest {

    // -- Determinism --------------------------------------------------------

    @Test
    fun sameSeed_producesIdenticalPuzzle_acrossCalls() {
        val seeds = longArrayOf(0L, 1L, -1L, 42L, 123_456_789L, Long.MIN_VALUE, Long.MAX_VALUE)
        for (seed in seeds) {
            val a = generateDailyPuzzle(seed)
            val b = generateDailyPuzzle(seed)
            assertEquals(a, b, "generateDailyPuzzle($seed) must be deterministic")
            assertEquals(a.startBoard, b.startBoard, "start board must match for seed $seed")
            assertEquals(a.target, b.target)
            assertEquals(a.par, b.par)
        }
    }

    @Test
    fun dateChain_isDeterministic_dateToSeedToPuzzle() {
        val date = LocalDate(2026, 6, 21)
        val viaDate = dailyPuzzleFor(date)
        val viaSeed = generateDailyPuzzle(dateToSeed(date))
        assertEquals(viaSeed, viaDate, "dailyPuzzleFor(date) must equal generateDailyPuzzle(dateToSeed(date))")
    }

    // -- Golden cross-platform cases (the determinism proof) ----------------
    // (seed -> target, par, row-major board values). These MUST be identical on
    // JVM and iOS; if they ever differ the generator stopped being cross-platform
    // deterministic. Captured from the generator itself.

    @Test
    fun golden_seed0() = assertGolden(
        seed = 0L,
        target = GOLDEN_0_TARGET,
        par = GOLDEN_0_PAR,
        values = GOLDEN_0_VALUES,
    )

    @Test
    fun golden_seed42() = assertGolden(
        seed = 42L,
        target = GOLDEN_42_TARGET,
        par = GOLDEN_42_PAR,
        values = GOLDEN_42_VALUES,
    )

    @Test
    fun golden_epochDate() {
        // dailyPuzzleFor(DAILY_EPOCH) — the launch day's puzzle, pinned end-to-end.
        val puzzle = dailyPuzzleFor(DAILY_EPOCH)
        assertEquals(GOLDEN_EPOCH_TARGET, puzzle.target, "epoch-day target")
        assertEquals(GOLDEN_EPOCH_PAR, puzzle.par, "epoch-day par")
        assertEquals(GOLDEN_EPOCH_VALUES.toList(), puzzle.startBoard.valueGrid(), "epoch-day board")
    }

    // -- Always solvable + reported par matches the solver ------------------

    @Test
    fun everyGeneratedPuzzle_isSolvable_andParMatchesSolver() {
        // Sweep 365 consecutive daily seeds (a full year via dateToSeed).
        var inBand = 0
        val total = 365
        for (offset in 0 until total) {
            val date = LocalDate.fromEpochDays((DAILY_EPOCH.toEpochDays() + offset))
            val puzzle = dailyPuzzleFor(date)

            // Independent re-solve: must be solvable, and its par must equal what the
            // puzzle reports.
            val solution = solve(puzzle.startBoard, puzzle.target)
            assertTrue(solution.solvable, "puzzle for $date must be solvable")
            assertEquals(
                puzzle.par,
                solution.parMoves,
                "reported par must equal the solver's parMoves for $date",
            )
            // Target is always from the documented pool.
            assertTrue(puzzle.target in DAILY_TARGETS, "target ${puzzle.target} for $date must be in DAILY_TARGETS")

            if (puzzle.par in MIN_PAR..MAX_PAR) inBand++
        }
        // The overwhelming majority must land inside the difficulty band (the rest are
        // the documented best-effort fallback when no in-band candidate is found).
        assertTrue(
            inBand >= (total * 95) / 100,
            "expected >=95% of $total daily puzzles in band [$MIN_PAR..$MAX_PAR], got $inBand",
        )
    }

    @Test
    fun rawSeedSweep_alwaysSolvable() {
        // A second sweep over raw consecutive seeds (independent of the date chain).
        for (seed in -100L..100L) {
            val puzzle = generateDailyPuzzle(seed)
            val solution = solve(puzzle.startBoard, puzzle.target)
            assertTrue(solution.solvable, "raw seed $seed must yield a solvable puzzle")
            assertEquals(puzzle.par, solution.parMoves, "par must match solver for raw seed $seed")
        }
    }

    // -- Target variety -----------------------------------------------------

    @Test
    fun target_variesAcrossSeeds() {
        val targets = (0L until 300L).map { generateDailyPuzzle(it).target }.toSet()
        assertTrue(
            targets.size >= 2,
            "target must vary across days; saw only $targets",
        )
        // Every observed target is from the documented pool.
        assertTrue(DAILY_TARGETS.containsAll(targets), "all targets must be in DAILY_TARGETS; saw $targets")
    }

    @Test
    fun par_alwaysInBand_forInBandSeeds_isNonTrivial() {
        // Sanity: across many seeds the par is never 0/1/2 unless it's the rare
        // fallback; assert at least that no in-band puzzle is trivially par 0.
        for (seed in 0L until 300L) {
            val puzzle = generateDailyPuzzle(seed)
            assertTrue(puzzle.par >= 1, "no daily puzzle should be already-solved (par 0); seed $seed")
        }
    }

    // -- helpers ------------------------------------------------------------

    private fun assertGolden(seed: Long, target: Int, par: Int, values: IntArray) {
        val puzzle = generateDailyPuzzle(seed)
        assertEquals(target, puzzle.target, "golden target for seed $seed")
        assertEquals(par, puzzle.par, "golden par for seed $seed")
        assertEquals(values.toList(), puzzle.startBoard.valueGrid(), "golden board for seed $seed")
    }

    /** Row-major value grid of a board (0 = empty); ids ignored. */
    private fun Board.valueGrid(): List<Int> {
        val out = ArrayList<Int>(cellCount)
        for (row in 0 until size) {
            for (col in 0 until size) {
                out.add(this[row, col]?.value ?: 0)
            }
        }
        return out
    }

    companion object {
        // Golden literals — captured from the generator's actual output. They MUST be
        // identical on JVM and iOS; that they pass on both targets is the
        // cross-platform-determinism proof. (Re-capture only if the generator's
        // algorithm intentionally changes.)
        const val GOLDEN_0_TARGET = 128
        const val GOLDEN_0_PAR = 4
        val GOLDEN_0_VALUES = intArrayOf(
            32, 16, 0, 0,
            0, 0, 16, 32,
            16, 0, 32, 0,
            0, 0, 0, 0,
        )

        const val GOLDEN_42_TARGET = 256
        const val GOLDEN_42_PAR = 9
        val GOLDEN_42_VALUES = intArrayOf(
            0, 8, 16, 8,
            16, 0, 0, 128,
            0, 0, 0, 64,
            32, 0, 4, 0,
        )

        const val GOLDEN_EPOCH_TARGET = 32
        const val GOLDEN_EPOCH_PAR = 3
        val GOLDEN_EPOCH_VALUES = intArrayOf(
            0, 16, 8, 16,
            0, 8, 0, 0,
            0, 4, 16, 0,
            4, 0, 4, 0,
        )
    }
}
