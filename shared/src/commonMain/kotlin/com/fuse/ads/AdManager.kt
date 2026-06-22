package com.fuse.ads

/**
 * ADS-1 — a thin, fully testable coordinator over [AdProvider] that collapses the common
 * **load-then-show** dance into a single suspend call, so ADS-2/3/4 don't each re-implement it.
 *
 * It deliberately holds NO placement policy — no frequency caps, no entitlement/"remove-ads"
 * suppression, no game wiring. That belongs to the feature stories (caps/entitlement are ADS-4 / IAP).
 * `AdManager` only orchestrates the provider calls and maps the outcome:
 *
 *  - [showRewarded] → `load(REWARDED)`; if not ready, [AdResult.NoFill]; else `show(REWARDED)`.
 *    ADS-2 (revive) and ADS-3 (streak-saver) grant their reward ONLY when this returns
 *    [AdResult.Rewarded] (use [AdResult.isRewardEarned]).
 *  - [showInterstitial] → `load(INTERSTITIAL)`; if not ready, [AdResult.NoFill]; else
 *    `show(INTERSTITIAL)`. ADS-4 treats anything other than [AdResult.Completed] as "not shown".
 *
 * If an ad of the requested format is ALREADY ready (e.g. preloaded earlier), the redundant load is
 * skipped. Every path returns an [AdResult]; nothing throws (the underlying provider is defensive).
 */
class AdManager(private val provider: AdProvider) {

    /** Initialise the underlying SDK (idempotent). Call once early; safe to call again. */
    fun initialize() = provider.initialize()

    /** Preload an ad of [format] without showing it (for warm placements). Returns load success. */
    suspend fun preload(format: AdFormat): Boolean =
        if (provider.isReady(format)) true else provider.load(format)

    /** Whether an ad of [format] is ready to show right now. */
    fun isReady(format: AdFormat): Boolean = provider.isReady(format)

    /**
     * Load (if needed) then show a REWARDED ad. Returns the raw [AdResult]; callers grant a reward
     * only when [AdResult.isRewardEarned]. [AdResult.NoFill] if no ad could be loaded.
     */
    suspend fun showRewarded(): AdResult = loadThenShow(AdFormat.REWARDED)

    /**
     * Load (if needed) then show an INTERSTITIAL ad. Returns [AdResult.Completed] when shown and
     * dismissed normally; [AdResult.NoFill] if no ad could be loaded; otherwise the failure outcome.
     */
    suspend fun showInterstitial(): AdResult = loadThenShow(AdFormat.INTERSTITIAL)

    private suspend fun loadThenShow(format: AdFormat): AdResult {
        val ready = provider.isReady(format) || provider.load(format)
        return if (ready) provider.show(format) else AdResult.NoFill
    }
}

/**
 * True only for [AdResult.Rewarded] — the single outcome on which a rewarded placement (ADS-2 revive,
 * ADS-3 streak-saver) may grant its reward. A convenience so callers never compare against the enum
 * directly and accidentally reward on `Dismissed`/`Completed`.
 */
val AdResult.isRewardEarned: Boolean
    get() = this == AdResult.Rewarded
