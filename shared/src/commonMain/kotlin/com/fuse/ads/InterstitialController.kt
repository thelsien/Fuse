package com.fuse.ads

import com.fuse.data.AdsRepository

/**
 * ADS-4 (Sprint 8) — the thin, testable glue between the Classic game-over → replay tap and the
 * pure [InterstitialPolicy], so [com.fuse.ui.game.GameScreen] stays clean and the count/persist/decide
 * logic is exercised WITHOUT Compose.
 *
 * The UI calls [onReplay] exactly ONCE per game-over → replay (i.e. on the lose overlay's "Restart"
 * tap, never on revive / win / mid-run). The controller:
 *  1. reads the persisted [InterstitialState] from the [AdsRepository],
 *  2. reads the ambient suppressors — the Remove-Ads [Entitlements] hook and the repository's
 *     first-session marker ([AdsRepository.isFirstSession]),
 *  3. asks the pure [InterstitialPolicy] for a [InterstitialPolicy.Decision],
 *  4. PERSISTS the advanced counter (so the every-Nth cadence is real across relaunches),
 *  5. returns whether the UI should present an interstitial.
 *
 * The controller does NOT show the ad itself — keeping the actual `adManager.showInterstitial()`
 * call (and its result handling) in the UI, mirroring how ADS-2's rewarded revive launches the ad
 * from `GameScreen`. The game must replay regardless of the ad's visual outcome, so [onReplay] only
 * decides "should we attempt to show one"; a no-fill/failed show never blocks the replay.
 *
 * `single` in Koin (one shared, stateful cadence). Default-constructible with NoOp collaborators for
 * tests/previews ([NoOpAdsRepository] + [NoOpEntitlements]).
 */
class InterstitialController(
    private val repository: AdsRepository = com.fuse.data.NoOpAdsRepository,
    private val entitlements: Entitlements = NoOpEntitlements,
) {
    /**
     * Records one Classic game-over → replay and decides whether to present an interstitial,
     * persisting the advanced cap counter as a side effect. Returns `true` iff the UI should call
     * `adManager.showInterstitial()` before starting the new game.
     *
     * Suppressed (returns `false`, but still advances + persists the counter) when Remove-Ads is
     * owned or the app is in its first session; otherwise shows on every [InterstitialPolicy.CAP_EVERY_NTH]th
     * replay.
     */
    fun onReplay(): Boolean {
        val decision = InterstitialPolicy.decide(
            state = repository.loadInterstitialState(),
            removeAdsOwned = entitlements.removeAdsOwned,
            isFirstSession = repository.isFirstSession(),
        )
        repository.saveInterstitialState(decision.nextState)
        return decision.shouldShow
    }
}
