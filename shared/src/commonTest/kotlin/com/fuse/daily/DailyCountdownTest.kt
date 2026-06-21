package com.fuse.daily

import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DLY-6 — the pure countdown core (UTC-midnight reset).
 *
 * [durationUntilNextUtcMidnight] and [formatCountdown] are clock-free: "now" is an
 * injected [Instant], so every boundary (mid-day, 1s before midnight, exactly
 * midnight, just after) is deterministic. Runs on JVM + iOS (commonTest).
 */
class DailyCountdownTest {

    // --- durationUntilNextUtcMidnight -------------------------------------------

    @Test
    fun midDayLeavesTheRemainderOfTheUtcDay() {
        // 12:00:00Z → 12h until the next UTC midnight.
        val now = Instant.parse("2026-06-21T12:00:00Z")
        assertEquals(12.hours, durationUntilNextUtcMidnight(now))
    }

    @Test
    fun arbitraryTimeComputesTheExactRemainder() {
        // 16:56:56Z → 7h 3m 4s left until 00:00Z.
        val now = Instant.parse("2026-06-21T16:56:56Z")
        assertEquals(7.hours + 3.minutes + 4.seconds, durationUntilNextUtcMidnight(now))
    }

    @Test
    fun oneSecondBeforeMidnightIsOneSecond() {
        val now = Instant.parse("2026-06-21T23:59:59Z")
        assertEquals(1.seconds, durationUntilNextUtcMidnight(now))
    }

    @Test
    fun exactlyMidnightIsAFull24Hours() {
        // Documented choice: at 00:00:00Z the next reset is TOMORROW's midnight, so a
        // full 24h window — never 0. The result is always in (0h, 24h].
        val now = Instant.parse("2026-06-22T00:00:00Z")
        assertEquals(24.hours, durationUntilNextUtcMidnight(now))
    }

    @Test
    fun justAfterMidnightIsNearlyAFullDay() {
        val now = Instant.parse("2026-06-22T00:00:01Z")
        assertEquals(24.hours - 1.seconds, durationUntilNextUtcMidnight(now))
    }

    @Test
    fun resultIsIndependentOfWhichUtcDayItIs() {
        // The boundary is the next UTC midnight regardless of the calendar date.
        val a = durationUntilNextUtcMidnight(Instant.parse("2026-06-21T18:30:00Z"))
        val b = durationUntilNextUtcMidnight(Instant.parse("2030-12-31T18:30:00Z"))
        assertEquals(a, b)
        assertEquals(5.hours + 30.minutes, a)
    }

    // --- formatCountdown ---------------------------------------------------------

    @Test
    fun formatsHoursMinutesSecondsZeroPadded() {
        assertEquals("07:03:04", formatCountdown(7.hours + 3.minutes + 4.seconds))
    }

    @Test
    fun formatsAFullDayAndZero() {
        assertEquals("24:00:00", formatCountdown(24.hours))
        assertEquals("00:00:00", formatCountdown(0.seconds))
    }

    @Test
    fun padsSingleDigitComponents() {
        assertEquals("01:02:03", formatCountdown(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun floorsSubSecondRemainderToTheWholeSecond() {
        // 4.9s still reads "...:04" — matches a 1s tick.
        assertEquals("00:00:04", formatCountdown(4.seconds + 900.milliseconds))
        assertEquals("00:00:00", formatCountdown(900.milliseconds))
    }

    @Test
    fun clampsNegativeDurationsToZero() {
        assertEquals("00:00:00", formatCountdown((-5).seconds))
        assertEquals("00:00:00", formatCountdown(-(2.hours)))
    }

    @Test
    fun endToEndFormatOfAComputedCountdown() {
        val now = Instant.parse("2026-06-21T16:56:56Z")
        assertEquals("07:03:04", formatCountdown(durationUntilNextUtcMidnight(now)))
    }

    @Test
    fun computedCountdownIsAlwaysInTheExpectedRange() {
        val now = Instant.parse("2026-06-21T00:00:30Z")
        val d = durationUntilNextUtcMidnight(now)
        assertTrue(d > 0.seconds)
        assertTrue(d <= 24.hours)
    }
}
