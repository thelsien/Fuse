package com.fuse.presentation

import com.fuse.analytics.AnalyticsEvents
import com.fuse.analytics.AnalyticsParams
import com.fuse.analytics.AnalyticsValues
import com.fuse.analytics.FakeAnalyticsLogger
import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.dailyDayNumber
import com.fuse.engine.Board
import com.fuse.engine.Direction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ANL-2 — instrumentation of the Daily [DailyStore]: `daily_completed` (day_number/moves/par) fires
 * exactly once on the solving move, alongside the one-shot [DailyEffect.Solved]. No PII.
 */
class DailyStoreAnalyticsTest {

    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC).plus(12.hours)
    }

    private val today = LocalDate(2026, 6, 21)

    /** Two 16s top-left; LEFT merges to 32 (target 32, par 1) — a one-move solve. */
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

    @Test
    fun solvingDailyLogsDailyCompletedOnceWithDayMovesAndPar() = runTest {
        val analytics = FakeAnalyticsLogger()
        val store = DailyStore(clock = FixedClock(today), puzzle = trivialPuzzle(), analytics = analytics)

        // Not solved yet → nothing logged.
        assertTrue(analytics.loggedEvents.isEmpty(), "no event before the solve")

        store.accept(DailyIntent.Move(Direction.LEFT)) // merges 16+16 → 32 → solved

        assertTrue(store.state.value.solved, "precondition: the move solved the puzzle")
        val event = analytics.loggedEvents.single()
        assertEquals(AnalyticsEvents.DAILY_COMPLETED, event.name)
        assertEquals(AnalyticsValues.MODE_DAILY, event.params[AnalyticsParams.MODE])
        assertEquals(dailyDayNumber(today), event.params[AnalyticsParams.DAY_NUMBER])
        assertEquals(1, event.params[AnalyticsParams.MOVES])
        assertEquals(1, event.params[AnalyticsParams.PAR])
    }

    @Test
    fun aBlockedMoveBeforeSolvingLogsNothing() = runTest {
        val analytics = FakeAnalyticsLogger()
        val store = DailyStore(clock = FixedClock(today), puzzle = trivialPuzzle(), analytics = analytics)

        // RIGHT on "16 16 _ _" slides them right but does not merge into the target in one move?
        // Actually RIGHT merges too; use UP which is a true no-op for the single top row.
        store.accept(DailyIntent.Move(Direction.UP)) // blocked (already at top)

        assertTrue(analytics.loggedEvents.isEmpty(), "a blocked move logs no daily_completed")
    }
}
