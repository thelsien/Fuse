package com.fuse.daily

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * DLY-6 — the pure core of the **Daily reset countdown**.
 *
 * The Daily Challenge re-seeds at **UTC midnight** (the same boundary the puzzle
 * generator and [DailyStore]'s new-day reset key on). These two functions answer
 * "how long until the next Daily?" purely — no clock is read here; "now" is passed
 * in (sourced via the injectable [DailyClock.now] seam), so every boundary is
 * deterministically testable.
 */

/**
 * Time from [now] to the next 00:00 **UTC** (the next Daily reset).
 *
 * Computed as `nextUtcMidnight(now) - now`, where the next UTC midnight is the
 * start-of-day of *tomorrow's* UTC date. Consequences at the boundary:
 *  - **Exactly at** UTC midnight ([now] == some day's 00:00:00Z) the result is a
 *    full **24h** (tomorrow's midnight), never `0` — the just-reset Daily has a
 *    fresh ~24h window. (We always target the *next* day's start, so the value is
 *    in the half-open range `(0h, 24h]`.)
 *  - **One second before** midnight → ~1s.
 *  - **Just after** midnight → ~24h.
 */
fun durationUntilNextUtcMidnight(now: Instant): Duration {
    val todayUtc = now.toLocalDateTime(TimeZone.UTC).date
    val nextMidnight = todayUtc.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)
    return nextMidnight - now
}

/**
 * Formats a countdown [d] as zero-padded **"HH:MM:SS"**.
 *
 * Negative durations clamp to `"00:00:00"` (defensive — a countdown should never
 * show negative time). Sub-second remainders are floored to the whole second
 * (e.g. 7h 3m 4.9s → `"07:03:04"`), so the displayed second matches a 1s tick.
 * Hours are NOT capped at 24 (a value > 24h, though not expected for this reset,
 * would render its true hour count).
 */
fun formatCountdown(d: Duration): String {
    val clamped = if (d < ZERO) ZERO else d
    val totalSeconds = clamped.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}"
}

/** Two-digit zero-pad for a non-negative value (values ≥ 100 render in full). */
private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()
