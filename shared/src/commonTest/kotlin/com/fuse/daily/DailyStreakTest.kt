package com.fuse.daily

import com.fuse.data.SettingsDailyStreakRepository
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DLY-5 — pure unit tests for the daily streak model ([DailyStreak], [recordCompletion],
 * [liveCurrent]). All pure (no clock, no persistence), so they run on JVM + iOS. Days are
 * plain `dailyDayNumber`s, so "yesterday" is `today - 1`.
 */
class DailyStreakTest {

    @Test
    fun firstCompletionStartsStreakAtOne() {
        val s = DailyStreak().recordCompletion(dayNumber = 100)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertEquals(100, s.lastCompletedDay)
    }

    @Test
    fun consecutiveDayIncrementsCurrent() {
        val s = DailyStreak()
            .recordCompletion(100)
            .recordCompletion(101)
            .recordCompletion(102)
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
        assertEquals(102, s.lastCompletedDay)
    }

    @Test
    fun sameDayTwiceDoesNotDoubleCount() {
        val once = DailyStreak().recordCompletion(100)
        val twice = once.recordCompletion(100)
        assertEquals(once, twice, "re-recording the same day is idempotent")
        assertEquals(1, twice.current)
    }

    @Test
    fun gapResetsCurrentToOne() {
        // Build a 3-day streak, then a 2-day gap (skip 103, 104) → next completion resets to 1.
        val built = DailyStreak()
            .recordCompletion(100)
            .recordCompletion(101)
            .recordCompletion(102)
        assertEquals(3, built.current)

        val afterGap = built.recordCompletion(105)
        assertEquals(1, afterGap.current, "a missed day resets the running streak to 1")
        assertEquals(3, afterGap.longest, "longest is preserved across the gap")
        assertEquals(105, afterGap.lastCompletedDay)
    }

    @Test
    fun longestTracksMaxAndNeverDecreases() {
        // A 4-day streak, gap, then a 2-day streak: longest stays 4.
        var s = DailyStreak()
        s = s.recordCompletion(1).recordCompletion(2).recordCompletion(3).recordCompletion(4)
        assertEquals(4, s.longest)
        s = s.recordCompletion(10) // gap → current 1
        s = s.recordCompletion(11) // current 2
        assertEquals(2, s.current)
        assertEquals(4, s.longest, "longest never decreases below a past best")
    }

    @Test
    fun backwardsDayIsIgnoredAndDoesNotCorrupt() {
        val s = DailyStreak().recordCompletion(100).recordCompletion(101)
        // A day STRICTLY before lastCompletedDay (e.g. clock moved back) is ignored.
        val after = s.recordCompletion(99)
        assertEquals(s, after, "a backwards day leaves the streak untouched")
        assertEquals(2, after.current)
        assertEquals(101, after.lastCompletedDay)
    }

    @Test
    fun liveCurrentEqualsCurrentWhenLastIsTodayOrYesterday() {
        val s = DailyStreak().recordCompletion(100).recordCompletion(101) // current 2, last 101
        assertEquals(2, s.liveCurrent(today = 101), "solved today → alive")
        assertEquals(2, s.liveCurrent(today = 102), "solved yesterday → still alive, extendable")
    }

    @Test
    fun liveCurrentIsZeroWhenADayWasMissed() {
        val s = DailyStreak().recordCompletion(100).recordCompletion(101) // last 101
        assertEquals(0, s.liveCurrent(today = 103), "a day skipped → broken → 0")
        assertEquals(0, s.liveCurrent(today = 200), "long gap → 0")
    }

    @Test
    fun liveCurrentIsZeroWhenNeverPlayed() {
        assertEquals(0, DailyStreak().liveCurrent(today = 100))
    }

    @Test
    fun missedDayThenCompletionStartsFreshAtOne() {
        // Alive 2-streak ending day 101; the player skips day 102; on day 103 they solve again.
        val s = DailyStreak().recordCompletion(100).recordCompletion(101)
        assertEquals(0, s.liveCurrent(today = 103), "broken before the new solve")
        val resumed = s.recordCompletion(103)
        assertEquals(1, resumed.current, "a fresh streak starts at 1 after a missed day")
        assertEquals(1, resumed.liveCurrent(today = 103))
    }

    @Test
    fun recordCompletionIsDeterministic() {
        val a = DailyStreak().recordCompletion(5).recordCompletion(6).recordCompletion(8)
        val b = DailyStreak().recordCompletion(5).recordCompletion(6).recordCompletion(8)
        assertEquals(a, b, "same inputs → same streak")
    }

    // ---- ADS-3 streak-saver: canRestore / restore ----

    @Test
    fun canRestoreIsTrueOnlyWhenAStreakExistedAndIsNowBroken() {
        // A 3-day streak ending day 102; on day 105 it is broken (days 103, 104 missed).
        val broken = DailyStreak().recordCompletion(100).recordCompletion(101).recordCompletion(102)
        assertEquals(0, broken.liveCurrent(today = 105), "precondition: broken on day 105")
        assertTrue(broken.canRestore(today = 105), "had a streak + now broken → restorable")
    }

    @Test
    fun canRestoreIsFalseWhenStreakIsStillAlive() {
        val alive = DailyStreak().recordCompletion(100).recordCompletion(101) // last 101
        assertFalse(alive.canRestore(today = 101), "solved today → alive, nothing to restore")
        assertFalse(alive.canRestore(today = 102), "solved yesterday → still extendable, not broken")
    }

    @Test
    fun canRestoreIsFalseWhenNeverPlayed() {
        assertFalse(DailyStreak().canRestore(today = 100), "never played → nothing to restore")
    }

    @Test
    fun restoreBridgesToYesterdayKeepingCurrentAndLongest() {
        val broken = DailyStreak(current = 3, longest = 7, lastCompletedDay = 102)
        val saved = broken.restore(today = 105)
        assertEquals(104, saved.lastCompletedDay, "lastCompletedDay bridged to today - 1")
        assertEquals(3, saved.current, "current preserved")
        assertEquals(7, saved.longest, "longest preserved")
        assertEquals(3, saved.liveCurrent(today = 105), "streak is live (extendable) again")
    }

    @Test
    fun restoreIsANoOpWhenNotRestorable() {
        val alive = DailyStreak().recordCompletion(100).recordCompletion(101)
        assertEquals(alive, alive.restore(today = 101), "alive → restore is a no-op")
        assertEquals(DailyStreak(), DailyStreak().restore(today = 100), "never played → no-op")
    }

    @Test
    fun secondRestoreIsANoOp() {
        val broken = DailyStreak(current = 4, longest = 4, lastCompletedDay = 100)
        val once = broken.restore(today = 105)
        assertTrue(broken.canRestore(today = 105))
        assertFalse(once.canRestore(today = 105), "after restore the break is gone (natural idempotency)")
        assertEquals(once, once.restore(today = 105), "a second restore changes nothing")
    }

    @Test
    fun restoreThenSolvingTodayContinuesTheStreak() {
        // A broken 3-streak, restored on day 105, then solving day 105 → 4 (continued, not reset to 1).
        val broken = DailyStreak(current = 3, longest = 3, lastCompletedDay = 102)
        val saved = broken.restore(today = 105)
        val solved = saved.recordCompletion(dayNumber = 105)
        assertEquals(4, solved.current, "solving today after a restore CONTINUES the streak (3 → 4)")
        assertEquals(4, solved.longest)
        assertEquals(4, solved.liveCurrent(today = 105))
    }

    @Test
    fun restoredStreakSurvivesJsonRoundTrip() {
        // A restored streak (any new fields) persists; and an OLD blob (no new fields) still decodes.
        val settings = MapSettings()
        val repo = SettingsDailyStreakRepository(settings)
        val restored = DailyStreak(current = 5, longest = 9, lastCompletedDay = 200).restore(today = 201)
        repo.saveStreak(restored)
        assertEquals(restored, repo.loadStreak(), "restored streak survives a round trip")

        // Back-compat: an old blob with only the original three fields still decodes (no added field).
        settings.putString(
            SettingsDailyStreakRepository.KEY_STREAK,
            "{\"current\":2,\"longest\":4,\"lastCompletedDay\":50}",
        )
        assertEquals(
            DailyStreak(current = 2, longest = 4, lastCompletedDay = 50),
            repo.loadStreak(),
            "an old blob (no new fields) decodes unchanged",
        )
    }

    @Test
    fun streakSurvivesJsonRoundTrip() {
        // Round-trip through the repository's JSON to prove persistence fidelity (JVM + iOS).
        val settings = MapSettings()
        val repo = SettingsDailyStreakRepository(settings)
        val original = DailyStreak(current = 3, longest = 7, lastCompletedDay = 42)
        repo.saveStreak(original)
        assertEquals(original, repo.loadStreak(), "streak survives a save/load round trip")
    }
}
