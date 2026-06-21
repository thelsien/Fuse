package com.fuse.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.fuse.daily.DailyClock
import com.fuse.daily.durationUntilNextUtcMidnight
import com.fuse.daily.formatCountdown
import kotlinx.coroutines.delay

/**
 * DLY-6 — the **live-ticking** Daily-reset countdown string, scoped to composition.
 *
 * Produces the formatted "HH:MM:SS" time until the next UTC midnight (the next Daily
 * reset) and refreshes it once per second. "now" comes from the injectable
 * [DailyClock] seam ([DailyClock.now]), so:
 *  - the boundary is the real UTC-midnight reset (never device-local), and
 *  - tests can pin a fixed clock and assert the exact rendered string.
 *
 * Implemented as a [produceState] whose coroutine loops `recompute → delay(1s)`. Because
 * the producer is keyed on [clock] and tied to the composition, the loop is **cancelled
 * automatically** when Home leaves the composition (and restarts if the clock changes) —
 * no leaked timer, battery-safe for a single visible screen.
 *
 * The initial value is computed eagerly (the first frame already shows the right time,
 * before the first `delay`), then the loop keeps it current.
 *
 * @param clock the Daily clock seam (Koin-bound `SystemDailyClock`); the source of "now".
 * @param tickMillis the refresh cadence; 1000ms (one second) in production.
 * @return a Compose [State] holding the current "HH:MM:SS" countdown.
 */
@Composable
fun rememberDailyCountdown(
    clock: DailyClock,
    tickMillis: Long = 1000L,
): State<String> = produceState(
    initialValue = formatCountdown(durationUntilNextUtcMidnight(clock.now())),
    clock,
    tickMillis,
) {
    while (true) {
        value = formatCountdown(durationUntilNextUtcMidnight(clock.now()))
        delay(tickMillis)
    }
}
