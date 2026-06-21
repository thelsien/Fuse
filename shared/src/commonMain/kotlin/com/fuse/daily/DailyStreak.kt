package com.fuse.daily

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * DLY-5 — the pure, persistable **daily streak** model: how many consecutive UTC days the
 * player has solved the Daily Challenge.
 *
 * "Completing" the Daily means SOLVING today's seed-derived puzzle. This type records that
 * the day was completed and tracks the running streak — kept entirely pure (no clock, no
 * persistence inside) so the day-boundary / missed-day rules are exhaustively unit-testable
 * on JVM + iOS. The impure parts (what day is it, where to store this) live in
 * [DailyClock] and `DailyStreakRepository` respectively.
 *
 * ## The day axis
 * A "day" here is a [dailyDayNumber] — the monotonic Daily #N (`+1` per UTC calendar day,
 * from `DAILY_EPOCH`). Using the day NUMBER (not a `LocalDate`) makes "consecutive" and
 * "gap" trivial integer arithmetic: yesterday is exactly `today - 1`.
 *
 * ## current vs longest vs lastCompletedDay
 *  - [current] — the length of the streak as of [lastCompletedDay]. This is the STORED
 *    value advanced by [recordCompletion]. It is NOT decayed when a day is missed (no
 *    background job runs to zero it); instead [liveCurrent] computes the displayable value
 *    against today, returning 0 once a day has been skipped. So a stored `current` of 5 with
 *    `lastCompletedDay` three days ago is a *historical* 5 that displays as 0 (broken) until
 *    the next completion restarts it at 1.
 *  - [longest] — the best [current] ever reached; monotonic non-decreasing.
 *  - [lastCompletedDay] — the day number of the most recent completion, or `null` if the
 *    Daily has never been completed.
 *
 * @property current the consecutive-day count as of [lastCompletedDay] (see [liveCurrent]
 *   for the display value that accounts for missed days). Defaults to `0` (never played).
 * @property longest the best [current] ever achieved; never decreases. Defaults to `0`.
 * @property lastCompletedDay the [dailyDayNumber] of the most recent completion, or `null`.
 */
@Serializable
data class DailyStreak(
    val current: Int = 0,
    val longest: Int = 0,
    val lastCompletedDay: Long? = null,
)

/**
 * DLY-5 — records that the Daily for [dayNumber] was solved, returning the advanced streak.
 *
 * Pure and total — the four cases (and the safety guard) are:
 *  - **Same day already recorded** (`lastCompletedDay == dayNumber`) → returned UNCHANGED.
 *    Idempotent: re-observing the same solve (recomposition, a re-emitted effect) never
 *    double-counts.
 *  - **Consecutive** (`lastCompletedDay == dayNumber - 1`) → `current + 1` (the streak
 *    extends across the day boundary).
 *  - **First ever** (`lastCompletedDay == null`) OR **a gap**
 *    (`dayNumber - lastCompletedDay > 1`, i.e. one or more days were missed) → `current = 1`
 *    (a fresh streak that starts today).
 *  - **[longest]** is then `max(longest, current)` and **[lastCompletedDay] = [dayNumber]**.
 *
 * ### Backwards / out-of-order guard
 * If [dayNumber] is STRICTLY LESS than [lastCompletedDay] (e.g. the device clock moved
 * backwards, or a stale event arrives late), the completion is IGNORED — the streak is
 * returned unchanged. Advancing on a backwards day could corrupt the streak (an older day
 * looking like a "gap" would wrongly reset a live streak, or an off-by-one could
 * double-count). The conservative, non-corrupting choice is to never let time run backwards
 * on the recorded streak; the worst case is a missed credit for a clock anomaly, which
 * [liveCurrent] still reflects correctly against the real today.
 */
fun DailyStreak.recordCompletion(dayNumber: Long): DailyStreak {
    val last = lastCompletedDay
    val nextCurrent = when {
        // Backwards / out-of-order day: ignore (never corrupt the streak).
        last != null && dayNumber < last -> return this
        // Same day already recorded: idempotent no-op.
        last == dayNumber -> return this
        // Consecutive day: extend.
        last != null && dayNumber == last + 1 -> current + 1
        // First ever, or a gap (a day was missed): fresh streak starting today.
        else -> 1
    }
    return DailyStreak(
        current = nextCurrent,
        longest = max(longest, nextCurrent),
        lastCompletedDay = dayNumber,
    )
}

/**
 * DLY-5 — the streak's DISPLAY value for [today], accounting for missed days WITHOUT a
 * background job.
 *
 * The stored [current] is only meaningful relative to [lastCompletedDay]; whether it is
 * still "alive" depends on how long ago that was. This function answers "what current streak
 * should the player see right now?":
 *  - [lastCompletedDay] is [today] (already solved today) OR `today - 1` (solved yesterday,
 *    so the streak is still alive and extendable by solving today) → [current].
 *  - otherwise (a day was missed, or never played) → `0`: the streak is broken and the next
 *    completion will start a fresh `1`.
 *
 * This is how "a missed day breaks the streak" is reflected purely from data + the current
 * day, so no timer/worker is needed to decay the streak at midnight — the display simply
 * recomputes against [today].
 */
fun DailyStreak.liveCurrent(today: Long): Int {
    val last = lastCompletedDay ?: return 0
    return if (last == today || last == today - 1) current else 0
}
