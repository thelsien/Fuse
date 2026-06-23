package com.fuse.presentation

import com.fuse.daily.DailyClock
import com.fuse.daily.DailyStreak
import com.fuse.daily.canRestore
import com.fuse.daily.dailyDayNumber
import com.fuse.daily.liveCurrent
import com.fuse.daily.recordCompletion
import com.fuse.daily.restore
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

    /**
     * ADS-3 (streak-saver) — "saves" a BROKEN daily streak (the player missed a day): load-fresh →
     * [restore] → save → republish. The UI calls this ONLY after a verified rewarded ad completion
     * (the ad is orchestrated in `DailyScreen`, mirroring ADS-2's revive — the store stays sync/pure
     * and grants the save only when asked).
     *
     * No-op when the streak is not restorable ([DailyStreak.canRestore] is false — alive, already
     * solved today, or never played) and naturally idempotent: after a successful restore the streak
     * is alive again (`canRestore` false), so a second call (or a re-tap) does nothing. Reflected in
     * [state] via [DailyStreakState.canRestore], which the prompt observes.
     */
    fun restore() {
        val today = dailyDayNumber(clock.todayUtc())
        streak = repository.loadStreak().restore(today)
        repository.saveStreak(streak)
        _state.value = project()
    }

    /** Projects the stored [streak] into its displayable form against today's day number. */
    private fun project(): DailyStreakState {
        val today = dailyDayNumber(clock.todayUtc())
        val canRestore = streak.canRestore(today)
        return DailyStreakState(
            current = streak.liveCurrent(today),
            longest = streak.longest,
            canRestore = canRestore,
            // The length of the broken streak that a restore would rescue (the stored `current`
            // before the gap). Only meaningful when `canRestore`; `0` otherwise.
            restorableLength = if (canRestore) streak.current else 0,
        )
    }
}

/**
 * DLY-5 — the immutable, displayable projection of the daily streak.
 *
 * @property current the CURRENT streak to display — `liveCurrent(today)`, i.e. `0` once a
 *   day has been missed (the streak is broken) and the consecutive-day count otherwise.
 * @property longest the best streak ever achieved (all-time), never decreasing.
 * @property canRestore ADS-3 — whether a broken-but-non-empty streak can be RESTORED via a
 *   rewarded ad right now (had a streak AND it's now broken). Drives the streak-saver prompt on
 *   `DailyScreen`; goes `false` once restored (natural idempotency) or when the streak is alive.
 * @property restorableLength ADS-3 — the length of the broken streak a restore would rescue (the
 *   stored consecutive-day count before the missed day); only meaningful when [canRestore], else
 *   `0`. Used by the prompt copy ("Your N-day streak broke…") since [current] is `0` while broken.
 */
data class DailyStreakState(
    val current: Int = 0,
    val longest: Int = 0,
    val canRestore: Boolean = false,
    val restorableLength: Int = 0,
)
