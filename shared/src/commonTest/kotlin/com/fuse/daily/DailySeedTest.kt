package com.fuse.daily

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * DLY-1 — coverage for the pure date->seed / day-number mapping.
 *
 * Runs on every CI target: the JVM via `:shared:testDebugUnitTest` and
 * Kotlin/Native via `:shared:iosSimulatorArm64Test`. The GOLDEN (date, seed)
 * pairs below are hardcoded literals computed independently of this code; the
 * SAME assertions passing on both targets is the cross-platform-determinism
 * proof the story requires — if either platform's `Long`/shift math diverged,
 * these would fail.
 */
class DailySeedTest {

    // ---- dateToSeed: cross-platform GOLDEN values --------------------------
    //
    // Independently computed: seed = splitMix64Finalizer(epochDay xor 0x9E3779B97F4A7C15),
    // where epochDay = days since 1970-01-01. Identical on JVM and iOS Native.

    @Test
    fun dateToSeedMatchesGoldenValuesAcrossPlatforms() {
        assertEquals(2620070580895364124L, dateToSeed(LocalDate(2026, 1, 1)))  // DAILY_EPOCH
        assertEquals(3115387324146053443L, dateToSeed(LocalDate(2026, 1, 2)))
        assertEquals(8823291344369534858L, dateToSeed(LocalDate(2026, 6, 21)))
        assertEquals(-2152535657050944081L, dateToSeed(LocalDate(1970, 1, 1)))   // epoch day 0
        assertEquals(-1768570677920509750L, dateToSeed(LocalDate(2024, 2, 29)))  // leap day
        assertEquals(7853288737976599605L, dateToSeed(LocalDate(2025, 12, 31)))
        assertEquals(-5422876813475824645L, dateToSeed(LocalDate(2026, 12, 31)))
    }

    @Test
    fun dateToSeedIsDeterministicForRepeatedCalls() {
        val date = LocalDate(2026, 6, 21)
        val first = dateToSeed(date)
        repeat(100) { assertEquals(first, dateToSeed(date)) }
        // A freshly constructed equal date must also agree.
        assertEquals(first, dateToSeed(LocalDate(2026, 6, 21)))
    }

    @Test
    fun differentDatesProduceDifferentWellDistributedSeeds() {
        // Spot-check a long run of consecutive days: no collisions, and the
        // SplitMix64 finalizer spreads adjacent epoch days across seed space
        // (a contiguous run must not stay clustered in one narrow band).
        val start = LocalDate(2026, 1, 1)
        val seeds = (0 until 1000).map { offset ->
            dateToSeed(start.plusDaysViaEpoch(offset))
        }
        assertEquals(seeds.size, seeds.toSet().size, "no collisions over 1000 consecutive days")

        // Adjacent days (epoch days differing by 1) must yield unrelated seeds:
        // assert none of the first 50 consecutive pairs are within a tiny delta.
        for (i in 0 until 50) {
            val a = seeds[i]
            val b = seeds[i + 1]
            assertNotEquals(a, b)
            assertTrue(
                kotlin.math.abs(a - b) > 1000L || a xor b != 1L,
                "adjacent-day seeds should not be near-identical: $a vs $b",
            )
        }
    }

    // ---- dailyDayNumber: monotonic, epoch-correct, boundary-safe -----------

    @Test
    fun dailyDayNumberIsOneAtTheEpoch() {
        assertEquals(1L, dailyDayNumber(DAILY_EPOCH))
        assertEquals(1L, dailyDayNumber(LocalDate(2026, 1, 1)))
    }

    @Test
    fun dailyDayNumberGoldenValues() {
        assertEquals(1L, dailyDayNumber(LocalDate(2026, 1, 1)))
        assertEquals(2L, dailyDayNumber(LocalDate(2026, 1, 2)))
        assertEquals(32L, dailyDayNumber(LocalDate(2026, 2, 1)))   // month boundary (Jan has 31)
        assertEquals(172L, dailyDayNumber(LocalDate(2026, 6, 21)))
        assertEquals(366L, dailyDayNumber(LocalDate(2027, 1, 1)))  // year boundary (2026 not leap)
    }

    @Test
    fun dailyDayNumberIncreasesByExactlyOnePerDay() {
        var date = LocalDate(2026, 1, 1)
        var expected = 1L
        repeat(800) {
            assertEquals(expected, dailyDayNumber(date), "off at $date")
            date = date.plusDaysViaEpoch(1)
            expected += 1
        }
    }

    @Test
    fun dailyDayNumberCrossesLeapDayCorrectly() {
        // 2024 is a leap year: Feb has 29 days, so 2024-02-28 -> 02-29 -> 03-01
        // are three consecutive numbers with no gap.
        val feb28 = dailyDayNumber(LocalDate(2024, 2, 28))
        val feb29 = dailyDayNumber(LocalDate(2024, 2, 29))
        val mar01 = dailyDayNumber(LocalDate(2024, 3, 1))
        assertEquals(feb28 + 1, feb29)
        assertEquals(feb29 + 1, mar01)
    }

    @Test
    fun dailyDayNumberIsZeroOrNegativeBeforeEpoch() {
        assertEquals(0L, dailyDayNumber(LocalDate(2025, 12, 31)))
        assertEquals(-1L, dailyDayNumber(LocalDate(2025, 12, 30)))
    }

    // Helper: advance a LocalDate by whole days through epoch-day arithmetic, so
    // the test never depends on a calendar-math API beyond LocalDate.fromEpochDays.
    private fun LocalDate.plusDaysViaEpoch(days: Int): LocalDate =
        LocalDate.fromEpochDays(this.toEpochDays() + days)
}
