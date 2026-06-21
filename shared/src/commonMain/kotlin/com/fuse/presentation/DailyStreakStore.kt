package com.fuse.presentation

import com.fuse.daily.DailyClock
import com.fuse.daily.DailyStreak
import com.fuse.daily.dailyDayNumber
import com.fuse.daily.liveCurrent
import com.fuse.daily.recordCompletion
import com.fuse.data.DailyStreakRepository
import com.fuse.data.NoOpDailyStreakRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DLY-5 — the small store that owns the persisted **daily streak** and exposes it to the UI.
 *
 * It is deliberately SEPARATE from [DailyStore] (the in-progress puzzle): the streak's
 * lifetime spans the whole history of completions and is persisted under its own key, while
 * the puzzle slot resets every UTC day. [DailyStore] stays free of streak counters and only
 * emits the one-shot [DailyEffect.Solved] seam; this store turns that signal into the
 * recorded/persisted streak (see [recordSolved]).
 *
 * ## What it exposes
 * [state] is a [StateFlow] of the DISPLAYABLE streak ([DailyStreakState]): the current value
 * is `liveCurrent(today)` (so a broken streak — a day was missed — shows `0` without any
 * background job), and `longest` is the all-time best. The HUD/overlay/Home read this.
 *
 * ## Recording a solve (idempotent, once per solve)
 * [recordSolved] is wired to [DailyStore]'s one-shot [DailyEffect.Solved] (collected in
 * `DailyScreen`). It runs **load → [recordCompletion] → save**, then republishes [state].
 * Because [recordCompletion] is idempotent for the same day, observing the same solve twice
 * (a re-emitted effect, a recomposition) never double-counts. And since `Solved` fires only
 * on the LIVE winning move (not on resume of an already-solved run), reopening a solved day
 * never re-records. The repository is the source of truth re-read on every record, so a
 * record always builds on the latest persisted streak.
 *
 * ## "today" for the live value
 * The current-streak DISPLAY needs today's day number to decide whether the stored streak is
 * still alive. This store reads it from the injected [clock] each time it projects, so the
 * display is always evaluated against the real current UTC day (the same clock [DailyStore]
 * uses) — a relaunch on a later day correctly shows a broken streak as `0`.
 *
 * @param clock the daily clock seam (Koin-bound [com.fuse.daily.SystemDailyClock]); the
 *   source of "today" for [liveCurrent]. Injected so tests can pin a date.
 * @param repository the streak's persistence; defaults to [NoOpDailyStreakRepository] so
 *   tests/previews need no `Settings`.
 */
class DailyStreakStore(
    private val clock: DailyClock,
    private val repository: DailyStreakRepository = NoOpDailyStreakRepository,
) {
    /** The persisted streak; the single mutable source, seeded from the repository on init. */
    private var streak: DailyStreak = repository.loadStreak()

    private val _state = MutableStateFlow(project())

    /** The displayable streak (current = live value vs today; longest = all-time best). */
    val state: StateFlow<DailyStreakState> = _state.asStateFlow()

    /**
     * Records that the Daily for [dayNumber] was solved: load-fresh → [recordCompletion] →
     * save → republish. Idempotent for the same day (the same-day guard in
     * [recordCompletion]) so re-observing a solve never double-counts.
     */
    fun recordSolved(dayNumber: Long) {
        streak = repository.loadStreak().recordCompletion(dayNumber)
        repository.saveStreak(streak)
        _state.value = project()
    }

    /** Projects the stored [streak] into its displayable form against today's day number. */
    private fun project(): DailyStreakState {
        val today = dailyDayNumber(clock.todayUtc())
        return DailyStreakState(
            current = streak.liveCurrent(today),
            longest = streak.longest,
        )
    }
}

/**
 * DLY-5 — the immutable, displayable projection of the daily streak.
 *
 * @property current the CURRENT streak to display — `liveCurrent(today)`, i.e. `0` once a
 *   day has been missed (the streak is broken) and the consecutive-day count otherwise.
 * @property longest the best streak ever achieved (all-time), never decreasing.
 */
data class DailyStreakState(
    val current: Int = 0,
    val longest: Int = 0,
)
