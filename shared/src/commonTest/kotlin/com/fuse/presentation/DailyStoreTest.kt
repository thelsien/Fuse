package com.fuse.presentation

import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.dailyDayNumber
import com.fuse.data.DailyProgress
import com.fuse.data.SettingsDailyRepository
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DLY-4 — store-level tests for the Daily Challenge MVI store ([DailyStore]).
 *
 * Drives the store with a fixed [DailyClock] (so "today" is pinned) and a tiny, known
 * [DailyPuzzle] (two adjacent 16s → swipe LEFT merges to a 32; target 32, par 1) so the
 * win sequence is short and deterministic. Persistence is exercised over an in-memory
 * [MapSettings] via the real [SettingsDailyRepository], so the suite runs on JVM + iOS.
 */
class DailyStoreTest {

    /** A fixed-date clock for deterministic "today". */
    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date
    }

    private val today = LocalDate(2026, 6, 21)
    private val yesterday = LocalDate(2026, 6, 20)

    /** A trivial puzzle: two 16s on the top-left; LEFT merges them to a 32 (par 1). */
    private fun trivialPuzzle(): DailyPuzzle = DailyPuzzle(
        seed = 0L,
        startBoard = Board.fromValues(
            arrayOf(
                intArrayOf(16, 16, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
        target = 32,
        par = 1,
    )

    private fun repoOver(settings: Settings) = SettingsDailyRepository(settings)

    private fun storeWith(
        settings: Settings = MapSettings(),
        clock: DailyClock = FixedClock(today),
        puzzle: DailyPuzzle = trivialPuzzle(),
    ): DailyStore = DailyStore(clock = clock, repository = repoOver(settings), puzzle = puzzle)

    @Test
    fun startLoadsTodaysPuzzleAtMoveZero() = runTest {
        val puzzle = trivialPuzzle()
        val store = storeWith(puzzle = puzzle)
        val s = store.state.value

        assertEquals(puzzle.startBoard, s.board, "board is the puzzle start board")
        assertEquals(0, s.moveCount)
        assertEquals(32, s.target)
        assertEquals(1, s.par)
        assertEquals(dailyDayNumber(today), s.dayNumber)
        assertFalse(s.solved)
        assertFalse(s.canUndo)
        assertFalse(s.canRestart)
    }

    @Test
    fun aBlockedMoveIsANoOp() = runTest {
        val store = storeWith()
        // UP on two top-row tiles can't move them up → blocked.
        store.accept(DailyIntent.Move(Direction.UP))
        val s = store.state.value
        assertEquals(0, s.moveCount, "blocked move not counted")
        assertFalse(s.canUndo)
    }

    @Test
    fun aChangedMoveIncrementsCountAndDoesNotAddTiles() = runTest {
        val store = storeWith()
        val tilesBefore = store.state.value.board.tiles().size

        // LEFT merges the two 16s into a single 32 → tile count DROPS (no spawn).
        store.accept(DailyIntent.Move(Direction.LEFT))
        val s = store.state.value
        assertEquals(1, s.moveCount)
        assertTrue(s.board.tiles().size < tilesBefore, "merge reduces tiles; nothing spawned")
        assertTrue(s.solved, "reaching 32 solves the puzzle")
        assertEquals(1, s.winningMoves)
    }

    @Test
    fun solvingSetsSolvedAndLocksFurtherMoves() = runTest {
        val store = storeWith()
        store.accept(DailyIntent.Move(Direction.LEFT)) // wins (32 formed)
        assertTrue(store.state.value.solved)

        // Further moves / undo / restart are ignored once solved (one-shot).
        store.accept(DailyIntent.Move(Direction.RIGHT))
        store.accept(DailyIntent.Undo)
        store.accept(DailyIntent.Restart)
        val s = store.state.value
        assertTrue(s.solved)
        assertEquals(1, s.moveCount, "no intent changes a solved run")
        assertFalse(s.canUndo)
        assertFalse(s.canRestart)
    }

    @Test
    fun undoRevertsOneMove() = runTest {
        // A puzzle needing 2 moves so an undo leaves a non-empty, non-solved run.
        // Two 16s separated by a gap: RIGHT slides them together (no merge yet, 1 move),
        // then... simpler: use a board where LEFT is one productive move and DOWN another.
        val puzzle = DailyPuzzle(
            seed = 0L,
            startBoard = Board.fromValues(
                arrayOf(
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            target = 32, // needs two merges (8+8=16, 16+16=32): more than one move.
            par = 2,
        )
        val store = storeWith(puzzle = puzzle)

        store.accept(DailyIntent.Move(Direction.LEFT)) // merges each row's 8s → two 16s
        val afterOne = store.state.value
        assertEquals(1, afterOne.moveCount)
        assertTrue(afterOne.canUndo)
        assertFalse(afterOne.solved)

        store.accept(DailyIntent.Undo) // back to start
        val afterUndo = store.state.value
        assertEquals(0, afterUndo.moveCount)
        assertEquals(puzzle.startBoard, afterUndo.board, "undo restores the start board")
        assertFalse(afterUndo.canUndo)
    }

    @Test
    fun restartResetsToStartBoard() = runTest {
        val puzzle = DailyPuzzle(
            seed = 0L,
            startBoard = Board.fromValues(
                arrayOf(
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            target = 32,
            par = 2,
        )
        val store = storeWith(puzzle = puzzle)
        store.accept(DailyIntent.Move(Direction.LEFT))
        store.accept(DailyIntent.Move(Direction.RIGHT))
        assertEquals(2, store.state.value.moveCount)

        store.accept(DailyIntent.Restart)
        val s = store.state.value
        assertEquals(0, s.moveCount)
        assertEquals(puzzle.startBoard, s.board)
        assertFalse(s.canRestart)
    }

    @Test
    fun solvedEffectFiresOnceWithDayNumberAndMoveCount() = runTest {
        val store = storeWith()
        val received = mutableListOf<DailyEffect.Solved>()
        // Subscribe before the winning move. The effect is hot/non-replaying; collect a
        // window after triggering. We use a simple approach: launch a collector via runTest.
        val job = launch {
            store.effects.collect { if (it is DailyEffect.Solved) received.add(it) }
        }
        yield()
        store.accept(DailyIntent.Move(Direction.LEFT)) // wins
        yield()
        job.cancel()

        assertEquals(1, received.size, "Solved fires exactly once")
        assertEquals(dailyDayNumber(today), received.first().dayNumber)
        assertEquals(1, received.first().moves)
    }

    // ---- Persistence (single slot) ----

    @Test
    fun afterMovesANewStoreOverSameSettingsResumesTheRun() = runTest {
        val settings = MapSettings()
        // Make a productive but non-winning run on a 2-move puzzle.
        val puzzle = DailyPuzzle(
            seed = 0L,
            startBoard = Board.fromValues(
                arrayOf(
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(8, 0, 0, 8),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            target = 32,
            par = 2,
        )
        val s1 = storeWith(settings = settings, puzzle = puzzle)
        s1.accept(DailyIntent.Move(Direction.LEFT))
        val savedBoard = s1.state.value.board
        val savedCount = s1.state.value.moveCount
        assertEquals(1, savedCount)

        // "Re-open": a fresh store over the SAME settings + same puzzle resumes the run.
        val s2 = storeWith(settings = settings, puzzle = puzzle)
        assertEquals(savedCount, s2.state.value.moveCount, "resumes the move count")
        assertEquals(savedBoard, s2.state.value.board, "resumes the exact in-progress board")
        assertTrue(s2.state.value.canUndo)
    }

    @Test
    fun resumeOfASolvedRunStaysLocked() = runTest {
        val settings = MapSettings()
        val first = storeWith(settings = settings)
        first.accept(DailyIntent.Move(Direction.LEFT)) // solves
        assertTrue(first.state.value.solved)

        val second = storeWith(settings = settings)
        assertTrue(second.state.value.solved, "a solved run resumes locked")
        second.accept(DailyIntent.Move(Direction.RIGHT)) // ignored
        assertTrue(second.state.value.solved)
    }

    @Test
    fun newDayResetDiscardsYesterdaysSlotAndStartsToday() = runTest {
        val settings = MapSettings()
        // Seed the slot as if it belongs to YESTERDAY with some moves.
        repoOver(settings).save(
            DailyProgress(
                dayNumber = dailyDayNumber(yesterday),
                moves = listOf(Direction.LEFT, Direction.RIGHT),
                solved = false,
            ),
        )

        // Open the store with TODAY's clock → the stale slot must be discarded.
        val store = storeWith(settings = settings, clock = FixedClock(today))
        val s = store.state.value
        assertEquals(0, s.moveCount, "new day discards yesterday's moves")
        assertEquals(dailyDayNumber(today), s.dayNumber)
        assertFalse(s.solved)

        // And the persisted slot is rewritten for today (so the day rolled over).
        val slot = repoOver(settings).load()
        assertEquals(dailyDayNumber(today), slot?.dayNumber)
        assertEquals(emptyList(), slot?.moves)
    }

    @Test
    fun noOpRepositoryAlwaysStartsFresh() = runTest {
        // Default NoOp repo: no slot ever, always today fresh.
        val store = DailyStore(clock = FixedClock(today), puzzle = trivialPuzzle())
        assertEquals(0, store.state.value.moveCount)
        assertNull(SettingsDailyRepository(MapSettings()).load())
    }
}
