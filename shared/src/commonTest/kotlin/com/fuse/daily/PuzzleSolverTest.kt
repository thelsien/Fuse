package com.fuse.daily

import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.TileIdSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DLY-2 — coverage for the no-spawn puzzle step ([puzzleStep]) and the optimal
 * BFS solver ([solve] / [PuzzleSolution]).
 *
 * Runs on every CI target: the JVM via `:shared:testDebugUnitTest` and
 * Kotlin/Native via `:shared:iosSimulatorArm64Test`. Boards are hand-built with
 * [Board.fromValues] (0 = empty) so each puzzle's minimal solution is known by
 * construction; we assert EXACT par + that the returned path actually reaches the
 * target when replayed through [puzzleStep].
 */
class PuzzleSolverTest {

    private fun board(vararg rows: IntArray): Board = Board.fromValues(arrayOf(*rows))

    /** Replays a path from [start] via the no-spawn step and returns the end board. */
    private fun replay(start: Board, path: List<Direction>): Board {
        var b = start
        val ids = TileIdSource()
        for (dir in path) {
            b = b.puzzleStep(dir, ids).board
        }
        return b
    }

    private fun maxValue(b: Board): Int = b.tiles().maxOfOrNull { it.value } ?: 0

    // ---- no-spawn step -----------------------------------------------------

    @Test
    fun puzzleStepNeverAddsATile() {
        // 4 tiles; sliding LEFT must keep exactly 4 (a spawn would make 5).
        val start = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 4, 0, 0),
            intArrayOf(0, 0, 8, 0),
            intArrayOf(0, 0, 0, 16),
        )
        val before = start.tiles().size
        val result = start.puzzleStep(Direction.LEFT)
        assertTrue(result.changed)
        assertEquals(before, result.board.tiles().size, "no-spawn step must not add a tile")
    }

    @Test
    fun puzzleStepMergeReducesTileCountAndDoesNotSpawn() {
        // [2,2,..] LEFT -> [4,..]: 2 tiles become 1, and nothing spawns to refill.
        val start = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val result = start.puzzleStep(Direction.LEFT)
        assertTrue(result.changed)
        assertEquals(1, result.board.tiles().size)
        assertEquals(4, result.board[0, 0]?.value)
    }

    @Test
    fun puzzleStepUnproductiveDirectionReportsNotChanged() {
        // Single tile already pinned to the left edge: LEFT does nothing.
        val start = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val result = start.puzzleStep(Direction.LEFT)
        assertFalse(result.changed)
        // Board is structurally unchanged (value layout identical).
        assertEquals(2, result.board[0, 0]?.value)
        assertEquals(1, result.board.tiles().size)
    }

    // ---- trivial 1-move puzzle ---------------------------------------------

    @Test
    fun trivialAdjacentPairSolvesInOneMove() {
        val start = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 4)
        assertTrue(solution.solvable)
        assertEquals(1, solution.parMoves)
        assertNotNull(solution.optimalPath)
        assertEquals(1, solution.optimalPath!!.size)
        // The returned path actually reaches the target.
        assertTrue(maxValue(replay(start, solution.optimalPath!!)) >= 4)
    }

    // ---- a known two-move puzzle -------------------------------------------

    @Test
    fun twoMovePuzzleHasParTwoAndReachablePath() {
        // LEFT collapses row0 -> 4@(0,0) and row2 -> 4@(2,0); then UP merges the two
        // 4s in column 0 into an 8. No single move can form 8, so par is exactly 2.
        val start = board(
            intArrayOf(2, 0, 2, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 4, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 8)
        assertTrue(solution.solvable)
        assertEquals(2, solution.parMoves)
        assertEquals(2, solution.optimalPath!!.size)
        assertTrue(maxValue(replay(start, solution.optimalPath!!)) >= 8)
    }

    @Test
    fun threeMovePuzzleBuildingTo16HasExactPar() {
        // Eight 2s in the top two rows. Optimal cascade to 16 in 3 moves.
        val start = board(
            intArrayOf(2, 2, 2, 2),
            intArrayOf(2, 2, 2, 2),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        // LEFT: each of the two rows -> [4,4,0,0]. UP: column0 4+4=8, column1 4+4=8.
        // LEFT: [8,8,..] in row0 -> [16,..]. Reaches 16 in 3 moves.
        val solution = solve(start, target = 16)
        assertTrue(solution.solvable)
        assertEquals(3, solution.parMoves)
        assertTrue(maxValue(replay(start, solution.optimalPath!!)) >= 16)
    }

    // ---- reachability: unsolvable ------------------------------------------

    @Test
    fun unsolvableWhenTargetCannotBeFormed() {
        // A single 2 can never become 4 (no second tile to merge with).
        val start = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 4)
        assertFalse(solution.solvable)
        assertNull(solution.parMoves)
        assertNull(solution.optimalPath)
    }

    @Test
    fun unsolvableWhenNotEnoughMatchingTilesForTarget() {
        // Two 2s can reach 4 but never 8: only one merge's worth of mass.
        val start = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 8)
        assertFalse(solution.solvable)
    }

    // ---- >= win semantics --------------------------------------------------

    @Test
    fun startBoardAlreadyAtTargetIsParZero() {
        val start = board(
            intArrayOf(8, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 8)
        assertTrue(solution.solvable)
        assertEquals(0, solution.parMoves)
        assertEquals(emptyList(), solution.optimalPath)
    }

    @Test
    fun tileAboveTargetCountsAsSolvedViaGreaterOrEqual() {
        // A 16 already exceeds a target of 8: >= semantics => already solved.
        val start = board(
            intArrayOf(16, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val solution = solve(start, target = 8)
        assertTrue(solution.solvable)
        assertEquals(0, solution.parMoves)
    }

    // ---- solver ignores tile ids -------------------------------------------

    @Test
    fun solverIgnoresTileIds() {
        val values = arrayOf(
            intArrayOf(2, 0, 2, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 4, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        // Same value layout, wildly different id sources -> identical solution.
        val a = Board.fromValues(values, TileIdSource(start = 1L))
        val b = Board.fromValues(values, TileIdSource(start = 9_000L))
        val solA = solve(a, target = 8)
        val solB = solve(b, target = 8)
        assertEquals(solA.parMoves, solB.parMoves)
        assertEquals(solA.optimalPath, solB.optimalPath)
        assertEquals(solA.solvable, solB.solvable)
    }

    // ---- determinism -------------------------------------------------------

    @Test
    fun solverIsDeterministic() {
        val start = board(
            intArrayOf(2, 2, 4, 0),
            intArrayOf(0, 4, 0, 0),
            intArrayOf(8, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val first = solve(start, target = 16)
        val second = solve(start, target = 16)
        assertEquals(first.solvable, second.solvable)
        assertEquals(first.parMoves, second.parMoves)
        assertEquals(first.optimalPath, second.optimalPath)
    }

    // ---- maxMoves / maxStates cap behavior ---------------------------------

    @Test
    fun maxMovesCapReportsUnsolvableWhenParExceedsBound() {
        // Genuinely solvable in 3 moves, but cap the depth at 1 -> unsolvable.
        val start = board(
            intArrayOf(2, 2, 2, 2),
            intArrayOf(2, 2, 2, 2),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        assertTrue(solve(start, target = 16).solvable) // generous default solves it
        val capped = solve(start, target = 16, maxMoves = 1)
        assertFalse(capped.solvable)
        assertNull(capped.parMoves)
    }

    @Test
    fun maxMovesZeroOnlySolvesAlreadySolvedBoards() {
        val solved = board(
            intArrayOf(8, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        assertEquals(0, solve(solved, target = 8, maxMoves = 0).parMoves)

        val needsAMove = board(
            intArrayOf(4, 4, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        assertFalse(solve(needsAMove, target = 8, maxMoves = 0).solvable)
    }

    @Test
    fun maxStatesCapReportsUnsolvable() {
        // A rich board with many reachable states; cap states at 1 -> bound hit.
        val start = board(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        )
        val capped = solve(start, target = 1024, maxStates = 1)
        assertFalse(capped.solvable)
    }
}
