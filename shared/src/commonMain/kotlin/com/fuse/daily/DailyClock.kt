package com.fuse.daily

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * DLY-1 — the single impure seam of the Daily Challenge: "what is today?".
 *
 * The seed/number functions ([dateToSeed], [dailyDayNumber]) are pure and take a
 * [LocalDate]. This interface is the *only* place the system clock is read, so:
 *  - production resolves today's UTC calendar day through [SystemDailyClock];
 *  - tests inject a fixed-date fake and exercise the pure functions directly.
 *
 * Crucially the day is always the **UTC** calendar day, so a player's device
 * timezone never shifts which daily they get: the seed changes exactly at UTC
 * midnight, identically for everyone.
 *
 * DLY-3 (generator) and DLY-4 (daily mode) resolve this from Koin to learn the
 * current day, then feed [todayUtc] into [dateToSeed] / [dailyDayNumber].
 */
interface DailyClock {
    /** The current calendar day in UTC. Changes at UTC midnight. */
    fun todayUtc(): LocalDate
}

/**
 * Default [DailyClock] backed by the device clock, read as a UTC calendar day.
 *
 * `Clock.System.todayIn(TimeZone.UTC)` is local-only (no network) — matching the
 * Daily's local-first design — and projects the device's current instant onto
 * the UTC day, so two devices in different timezones observing the same instant
 * agree on the day (and therefore on the seed).
 */
class SystemDailyClock(
    private val clock: Clock = Clock.System,
) : DailyClock {
    override fun todayUtc(): LocalDate = clock.todayIn(TimeZone.UTC)
}
