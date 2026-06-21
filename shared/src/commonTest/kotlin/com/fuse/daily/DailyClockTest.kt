package com.fuse.daily

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * DLY-1 — the clock seam: UTC-day stability across timezones and day boundaries.
 *
 * The pure functions are covered by [DailySeedTest]; here we prove that the
 * *resolution of "today"* is a function of the UTC calendar day only:
 *  - two instants on the SAME UTC day -> same date -> same seed, no matter what
 *    wall-clock timezone the device is in;
 *  - an instant just before vs just after UTC midnight -> different UTC days ->
 *    different seeds (the seed flips exactly at UTC midnight).
 *
 * We drive [SystemDailyClock] with a fixed [Clock] returning a chosen instant —
 * no real wall-clock, fully deterministic — and also exercise a fake
 * [DailyClock] to mirror how DLY-3/DLY-4 will inject "today" in their tests.
 */
class DailyClockTest {

    private fun clockAt(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    @Test
    fun twoInstantsOnSameUtcDayMapToSameDateAndSeed() {
        // 2026-06-21 at 00:30 UTC and at 23:30 UTC — same UTC calendar day.
        val morning = SystemDailyClock(clockAt(Instant.parse("2026-06-21T00:30:00Z"))).todayUtc()
        val evening = SystemDailyClock(clockAt(Instant.parse("2026-06-21T23:30:00Z"))).todayUtc()

        assertEquals(LocalDate(2026, 6, 21), morning)
        assertEquals(morning, evening)
        assertEquals(dateToSeed(morning), dateToSeed(evening))
    }

    @Test
    fun instantsAcrossUtcMidnightMapToDifferentDaysAndSeeds() {
        val beforeMidnight = SystemDailyClock(clockAt(Instant.parse("2026-06-21T23:59:59Z"))).todayUtc()
        val afterMidnight = SystemDailyClock(clockAt(Instant.parse("2026-06-22T00:00:00Z"))).todayUtc()

        assertEquals(LocalDate(2026, 6, 21), beforeMidnight)
        assertEquals(LocalDate(2026, 6, 22), afterMidnight)
        assertNotEquals(beforeMidnight, afterMidnight)
        assertNotEquals(dateToSeed(beforeMidnight), dateToSeed(afterMidnight))
    }

    @Test
    fun utcDayIsIndependentOfDeviceLocalTime() {
        // Same physical instant. A device in UTC+14 would read its LOCAL wall
        // clock as 2026-06-22, but todayUtc() must still report the UTC day
        // (2026-06-21), so everyone worldwide gets the same daily for that
        // instant. We assert against the UTC projection directly.
        val instant = Instant.parse("2026-06-21T12:00:00Z")
        val today = SystemDailyClock(clockAt(instant)).todayUtc()
        assertEquals(LocalDate(2026, 6, 21), today)
        // Sanity: the seed equals the pure mapping of that UTC date.
        assertEquals(dateToSeed(LocalDate(2026, 6, 21)), dateToSeed(today))
    }

    @Test
    fun fakeDailyClockInjectsAFixedDayForDownstreamTests() {
        // Mirrors how DLY-3/DLY-4 will stub "today". The seed/number are then a
        // pure function of the injected date.
        val fixed = LocalDate(2026, 1, 1)
        val fake = object : DailyClock {
            override fun todayUtc(): LocalDate = fixed
            override fun now(): Instant = Instant.parse("2026-01-01T00:00:00Z")
        }
        assertEquals(fixed, fake.todayUtc())
        assertEquals(2620070580895364124L, dateToSeed(fake.todayUtc()))
        assertEquals(1L, dailyDayNumber(fake.todayUtc()))
    }
}
