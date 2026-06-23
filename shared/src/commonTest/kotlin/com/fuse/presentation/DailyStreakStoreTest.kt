package com.fuse.presentation

import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.dailyDayNumber
import com.fuse.data.SettingsDailyRepository
import com.fuse.data.SettingsDailyStreakRepository
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DLY-5 — integration tests for [DailyStreakStore] over an in-memory [MapSettings] (so the
 * suite runs on JVM + iOS). Exercises:
 *  - recording a solve updates AND persists the streak;
 *  - a NEW store over the same `Settings` sees it (survives "relaunch");
 *  - two consecutive simulated days increment the streak;
 *  - a skipped day breaks it (liveCurrent 0) and the next completion restarts at 1;
 *  - end-to-end: driving a real [DailyStore] to emit `Solved` and feeding it to the recorder
 *    records the streak exactly once (idempotent).
 */
class DailyStreakStoreTest {

    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date

        // DLY-6 — instant seam (unused by the streak store; pinned to the date's UTC noon).
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC).plus(12.hours)
    }

    // Three consecutive UTC days (well past the DAILY_EPOCH).
    private val day1 = LocalDate(2026, 6, 21)
    private val day2 = LocalDate(2026, 6, 22)
    private val day3 = LocalDate(2026, 6, 23)
    private val day4 = LocalDate(2026, 6, 24)

    private fun streakRepo(settings: Settings) = SettingsDailyStreakRepository(settings)

    @Test
    fun recordingASolvePersistsAndIsVisibleToANewStore() = runTest {
        val settings = MapSettings()
        val store = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))

        store.recordSolved(dailyDayNumber(day1))
        assertEquals(1, store.state.value.current)
        assertEquals(1, store.state.value.longest)

        // "Relaunch": a fresh store over the SAME settings (and same day) sees the streak.
        val reopened = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
        assertEquals(1, reopened.state.value.current, "streak survives relaunch")
        assertEquals(1, reopened.state.value.longest)
    }

    @Test
    fun twoConsecutiveDaysIncrementTheStreak() = runTest {
        val settings = MapSettings()
        // Day 1: solve.
        DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day1))

        // Day 2 ("relaunch" on the next UTC day): the streak is still alive (yesterday) and a
        // solve extends it to 2.
        val day2Store = DailyStreakStore(clock = FixedClock(day2), repository = streakRepo(settings))
        assertEquals(1, day2Store.state.value.current, "yesterday's streak is still alive on day 2")
        day2Store.recordSolved(dailyDayNumber(day2))
        assertEquals(2, day2Store.state.value.current)
        assertEquals(2, day2Store.state.value.longest)
    }

    @Test
    fun aSkippedDayBreaksTheStreakThenNextCompletionRestartsAtOne() = runTest {
        val settings = MapSettings()
        DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day1))

        // Day 2 is SKIPPED (no solve). On day 3 the displayed current is 0 (broken).
        val day3Store = DailyStreakStore(clock = FixedClock(day3), repository = streakRepo(settings))
        assertEquals(0, day3Store.state.value.current, "a skipped day breaks the displayed streak")
        assertEquals(1, day3Store.state.value.longest, "longest is preserved")

        // Solving on day 3 starts a fresh streak at 1.
        day3Store.recordSolved(dailyDayNumber(day3))
        assertEquals(1, day3Store.state.value.current, "next completion restarts the streak at 1")
    }

    @Test
    fun recordingTheSameDayTwiceDoesNotDoubleCount() = runTest {
        val settings = MapSettings()
        val store = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
        store.recordSolved(dailyDayNumber(day1))
        store.recordSolved(dailyDayNumber(day1)) // idempotent
        assertEquals(1, store.state.value.current, "same-day re-record never double counts")
    }

    // ---- ADS-3 streak-saver: canRestore projection + restore() ----

    /** Builds a BROKEN streak in [settings]: solve a 2-streak ending day2, observed on day4. */
    private fun seedBrokenStreak(settings: Settings) {
        DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day1))
        DailyStreakStore(clock = FixedClock(day2), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day2)) // current 2, last = day2
    }

    @Test
    fun canRestoreProjectionIsTrueWhenBrokenFalseWhenAlive() = runTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)

        // Day 4: day3 was skipped → broken → restorable.
        val day4Store = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        assertEquals(0, day4Store.state.value.current, "broken → live current 0")
        assertTrue(day4Store.state.value.canRestore, "broken-with-streak → canRestore")
        assertEquals(2, day4Store.state.value.restorableLength, "the 2-day streak is rescuable")

        // Day 3 (still alive: solved yesterday) → not restorable.
        val day3Store = DailyStreakStore(clock = FixedClock(day3), repository = streakRepo(settings))
        assertFalse(day3Store.state.value.canRestore, "alive (solved yesterday) → not restorable")
    }

    @Test
    fun canRestoreProjectionIsFalseWhenNeverPlayed() = runTest {
        val store = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(MapSettings()))
        assertFalse(store.state.value.canRestore, "never played → nothing to restore")
        assertEquals(0, store.state.value.restorableLength)
    }

    @Test
    fun restoreAppliesPersistsAndRepublishesThenSolvingContinues() = runTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)

        val day4Store = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        assertTrue(day4Store.state.value.canRestore)

        day4Store.restore()
        // Republished: the streak is live again (2) and the offer is gone.
        assertEquals(2, day4Store.state.value.current, "restored streak shows its value")
        assertFalse(day4Store.state.value.canRestore, "offer disappears after restore")

        // Persisted: a fresh store over the same settings (same day) sees the restored streak.
        val reopened = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        assertEquals(2, reopened.state.value.current, "restore persisted across relaunch")
        assertFalse(reopened.state.value.canRestore)

        // And solving day4 CONTINUES the streak (2 → 3), not a reset to 1.
        day4Store.recordSolved(dailyDayNumber(day4))
        assertEquals(3, day4Store.state.value.current, "solving after a restore continues the streak")
    }

    @Test
    fun restoreIsANoOpWhenNotRestorable() = runTest {
        val settings = MapSettings()
        // Alive streak (solved today) — restore must change nothing.
        val store = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
        store.recordSolved(dailyDayNumber(day1))
        assertFalse(store.state.value.canRestore)
        store.restore()
        assertEquals(1, store.state.value.current, "restore on an alive streak is a no-op")
        assertFalse(store.state.value.canRestore)
    }

    @Test
    fun secondRestoreIsANoOp() = runTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)
        val store = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        store.restore()
        val after = store.state.value
        store.restore() // already alive → no-op
        assertEquals(after, store.state.value, "a second restore changes nothing")
    }

    @Test
    fun streakIsSeparateFromTheInProgressPuzzleSlot() = runTest {
        // Record a streak, then write/clear the DAILY PROGRESS slot — the streak must be
        // untouched (distinct keys: fuse.daily.streak vs fuse.daily.progress).
        val settings = MapSettings()
        val streakStore = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
        streakStore.recordSolved(dailyDayNumber(day1))

        val progressRepo = SettingsDailyRepository(settings)
        progressRepo.save(
            com.fuse.data.DailyProgress(
                dayNumber = dailyDayNumber(day2),
                moves = emptyList(),
                solved = false,
            ),
        )
        progressRepo.clear()

        // A fresh streak store still sees the streak after the progress slot churned.
        val reopened = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
        assertEquals(1, reopened.state.value.current, "streak survives progress-slot writes/clears")
    }

    // ---- End-to-end: a real DailyStore solve drives the recorder ----

    /** Two 16s top-left → swipe LEFT merges to a 32 (target 32, par 1). */
    private fun trivialPuzzle() = DailyPuzzle(
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
    fun solvingARealDailyStoreRecordsTheStreakOnce() = runTest {
        val settings = MapSettings()
        val clock = FixedClock(day1)
        val daily = DailyStore(
            clock = clock,
            repository = SettingsDailyRepository(settings),
            puzzle = trivialPuzzle(),
        )
        val streakStore = DailyStreakStore(clock = clock, repository = streakRepo(settings))

        // Mirror DailyScreen's wiring: collect the one-shot Solved effect → recordSolved.
        val job = launch {
            daily.effects.collect { effect ->
                if (effect is DailyEffect.Solved) streakStore.recordSolved(effect.dayNumber)
            }
        }
        yield()
        daily.accept(DailyIntent.Move(Direction.LEFT)) // solves (32 formed) → emits Solved
        yield()
        job.cancel()

        assertEquals(1, streakStore.state.value.current, "a real solve records the streak")
        assertEquals(1, streakStore.state.value.longest)
        // And it persisted: a fresh store sees it.
        assertEquals(
            1,
            DailyStreakStore(clock = clock, repository = streakRepo(settings)).state.value.current,
        )
    }
}
