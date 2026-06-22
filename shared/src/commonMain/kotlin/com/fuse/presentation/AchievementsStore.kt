package com.fuse.presentation

import com.fuse.cosmetics.PlayerAchievements
import com.fuse.data.AchievementsRepository
import com.fuse.data.NoOpAchievementsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * COS-1 — the small store that owns the persisted [PlayerAchievements] record and exposes it
 * as a [StateFlow] so the cosmetics layer can recompute unlocks when a milestone is earned.
 *
 * It mirrors [DailyStreakStore]: seeded from its [repository] on init, it turns one-shot game
 * signals into a persisted record. Here the signal is `GameEffect.Won` (Classic first reaching
 * 2048): `GameScreen` collects the store's existing `effects` flow and, on `Won`, calls
 * [markReached2048] — exactly the way `DailyScreen` calls `DailyStreakStore.recordSolved` on
 * `DailyEffect.Solved`. [markReached2048] is IDEMPOTENT: once `reached2048` is already true it
 * neither re-persists nor re-emits, so a re-collected effect / a later win can't churn state.
 *
 * [CosmeticsStore] observes [state] to keep its unlocked set live.
 *
 * @param repository persistence for the achievements record; defaults to
 *   [NoOpAchievementsRepository] so tests/previews need no `Settings`.
 */
class AchievementsStore(
    private val repository: AchievementsRepository = NoOpAchievementsRepository,
) {
    private val _state = MutableStateFlow(repository.load())

    /** The current achievements record. Drives cosmetic unlocking. */
    val state: StateFlow<PlayerAchievements> = _state.asStateFlow()

    /**
     * COS-1 — records that the player reached the 2048 tile in Classic. Wired to the one-shot
     * `GameEffect.Won` (collected in `GameScreen`). Idempotent: a no-op once already set, so a
     * re-emitted effect, a recomposition, or a later win never re-persists or re-emits.
     */
    fun markReached2048() {
        if (_state.value.reached2048) return
        val updated = _state.value.copy(reached2048 = true)
        repository.save(updated)
        _state.value = updated
    }
}
