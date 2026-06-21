package com.fuse.daily

import kotlinx.datetime.LocalDate

/**
 * DLY-1 — the date->seed foundation of the Daily Challenge.
 *
 * The Daily is a DETERMINISTIC, NO-SPAWN puzzle: everyone playing on a given UTC
 * calendar day gets the *same* seed-derived starting board and target. The
 * generator (DLY-3) and the daily mode (DLY-4) build on the two pure functions
 * here plus the [DailyClock] seam — they never read the system clock directly,
 * so the seed is reproducible and identical on every platform.
 *
 * Everything in this file is PURE:
 *  - [dateToSeed] and [dailyDayNumber] take a [LocalDate] and return a value.
 *  - No `Clock`, no `TimeZone`, no platform calls.
 * That makes the cross-platform-determinism guarantee trivial to prove with the
 * golden assertions in `DailySeedTest`, and it keeps the only impure part (what
 * "today" is) isolated behind [DailyClock].
 */

/**
 * The fixed launch epoch for Daily numbering. Daily #1 is **this date** (the
 * epoch itself is day number 1, not 0 — the first challenge is "#1"). Earlier
 * dates yield zero or negative numbers, which the mode treats as "no daily yet".
 *
 * Chosen as 2026-01-01 (UTC), the Daily Challenge launch reference. This is a
 * product constant, not derived from anything — if it ever changes, every
 * historical "Daily #N" label shifts, so it is pinned here and covered by a test.
 */
val DAILY_EPOCH: LocalDate = LocalDate(2026, 1, 1)

/**
 * Maps a UTC calendar day to a deterministic 64-bit puzzle seed.
 *
 * Construction (documented so DLY-2/DLY-3 and any external verifier can
 * reproduce it byte-for-byte):
 *
 *  1. Take the date's `toEpochDays()` — a `Long` count of days since the Unix
 *     epoch (1970-01-01). This collapses the calendar day to a single integer
 *     that is, by definition, identical for the same UTC date on every device
 *     regardless of timezone (the caller resolves the date in [TimeZone.UTC]).
 *  2. XOR in a fixed domain-separation salt ([SEED_SALT]) so Daily seeds occupy
 *     a different region of seed-space than any raw epoch-day value used
 *     elsewhere, and so consecutive days don't start from tiny adjacent inputs.
 *  3. Run the value through the SplitMix64 finalizer ("mix") — the exact same
 *     avalanche function the engine's `SeededRng` uses. This is what turns
 *     adjacent epoch days (which differ by 1) into well-distributed, unrelated
 *     64-bit seeds: a one-bit input change flips ~half the output bits.
 *
 * All operations (`Long` add/mul wrap mod 2^64, `ushr` logical shift, `xor`) are
 * pinned by the Kotlin language spec and identical on JVM and Kotlin/Native, so
 * the same date yields the same seed everywhere. Proven by golden (date, seed)
 * pairs asserted in `commonTest` (which runs on both targets).
 *
 * @param date a UTC calendar day (resolve via `DailyClock.todayUtc()` or
 *   `Clock.System.todayIn(TimeZone.UTC)`); only the calendar day matters.
 * @return a 64-bit seed; any [Long] is valid input to `SeededRng(seed)`.
 */
fun dateToSeed(date: LocalDate): Long {
    val epochDay = date.toEpochDays().toLong()
    return splitMix64Finalizer(epochDay xor SEED_SALT)
}

/**
 * The "Daily #N" number for the share card / header: the count of days since
 * [DAILY_EPOCH], with the epoch itself being **#1**.
 *
 *  - `dailyDayNumber(DAILY_EPOCH) == 1`
 *  - `dailyDayNumber(DAILY_EPOCH + 1 day) == 2`
 *  - it increases by exactly 1 per calendar day, monotonically, and correctly
 *    crosses month, year, and leap-day boundaries (epoch-day arithmetic handles
 *    these — no special-casing).
 *
 * Dates before [DAILY_EPOCH] return zero or negative numbers; callers (DLY-4)
 * decide how to present those (the mode simply won't have launched yet).
 *
 * @param date a UTC calendar day (same source as [dateToSeed]).
 * @return the 1-based daily number `N` for `date`.
 */
fun dailyDayNumber(date: LocalDate): Long =
    (date.toEpochDays().toLong() - DAILY_EPOCH.toEpochDays().toLong()) + 1L

// ---------------------------------------------------------------------------

/**
 * Domain-separation salt XORed into the epoch day before mixing. An arbitrary
 * fixed 64-bit constant (0x9E3779B97F4A7C15, the SplitMix64 "golden gamma" bit
 * pattern reused here purely as a nothing-up-my-sleeve constant). It is part of
 * the seed construction, not a secret; documented so the mapping is reproducible.
 */
private const val SEED_SALT: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

// SplitMix64 finalizer multipliers — the same avalanche constants the engine's
// SeededRng uses. Reused here (rather than depending on the RNG type) to mix an
// epoch-day input into a well-distributed seed. These ARE the algorithm.
private const val MIX_1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
private const val MIX_2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB

/**
 * The SplitMix64 finalizer ("mix"): a fixed-point avalanche over a 64-bit value.
 * Pure bit math, identical on every Kotlin target. Identical in spirit to the
 * step inside `SeededRng.nextLong()`, applied here to a date-derived input.
 */
private fun splitMix64Finalizer(value: Long): Long {
    var z = value
    z = (z xor (z ushr 30)) * MIX_1
    z = (z xor (z ushr 27)) * MIX_2
    return z xor (z ushr 31)
}
