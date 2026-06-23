package com.fuse.ads

import com.fuse.analytics.AnalyticsLogger
import com.fuse.analytics.AnalyticsValues
import com.fuse.analytics.NoOpAnalyticsLogger
import com.fuse.analytics.logAdImpression
import com.fuse.analytics.logAdRewardGranted

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
class AdManager(
    private val provider: AdProvider,
    /**
     * ANL-2 — the analytics seam. This ONE coordinator is the single coherent place every real
     * placement (ADS-2 revive, ADS-3 streak-saver, ADS-4 game-over interstitial) flows through, so
     * impression/reward are logged here rather than re-instrumented at three call sites. The caller
     * passes the [placement] (only it knows where the ad fires); [AdManager] maps the outcome:
     *  - the ad was actually PRESENTED ([AdResult.isImpression]) → `ad_impression` (format, placement),
     *  - a rewarded ad GRANTED its reward ([AdResult.isRewardEarned]) → `ad_reward_granted` (placement).
     * A NoFill/NotReady/Failed (the ad never appeared) logs nothing. Defaults to
     * [NoOpAnalyticsLogger] so existing constructors/tests are unchanged; Koin injects the real one.
     */
    private val analytics: AnalyticsLogger = NoOpAnalyticsLogger,
) {

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
     *
     * ANL-2: pass the [placement] (`revive` / `streak_saver`) so an actual presentation logs
     * `ad_impression` and a rewarded completion logs `ad_reward_granted`. The default keeps existing
     * call sites/tests compiling; real call sites pass their placement.
     */
    suspend fun showRewarded(placement: String = AnalyticsValues.PLACEMENT_REVIVE): AdResult =
        loadThenShow(AdFormat.REWARDED, placement)

    /**
     * Load (if needed) then show an INTERSTITIAL ad. Returns [AdResult.Completed] when shown and
     * dismissed normally; [AdResult.NoFill] if no ad could be loaded; otherwise the failure outcome.
     *
     * ANL-2: pass the [placement] (`game_over`) so an actual presentation logs `ad_impression`.
     */
    suspend fun showInterstitial(placement: String = AnalyticsValues.PLACEMENT_GAME_OVER): AdResult =
        loadThenShow(AdFormat.INTERSTITIAL, placement)

    private suspend fun loadThenShow(format: AdFormat, placement: String): AdResult {
        val ready = provider.isReady(format) || provider.load(format)
        val result = if (ready) provider.show(format) else AdResult.NoFill
        // ANL-2 — log only when the ad was actually presented; NoFill/NotReady/Failed log nothing.
        if (result.isImpression) {
            analytics.logAdImpression(format = format.analyticsFormat, placement = placement)
        }
        if (result.isRewardEarned) {
            analytics.logAdRewardGranted(placement = placement)
        }
        return result
    }
}

/**
 * ANL-2 — true when an [AdResult] means the ad was actually PRESENTED to the user (an impression):
 * [AdResult.Rewarded] / [AdResult.Completed] / [AdResult.Shown]. The "didn't appear" outcomes
 * ([AdResult.NoFill], [AdResult.NotReady], [AdResult.Failed], [AdResult.Dismissed]) are NOT
 * impressions — `Dismissed` means the rewarded ad was closed before earning, which still appeared,
 * so it IS counted as an impression.
 */
val AdResult.isImpression: Boolean
    get() = this == AdResult.Rewarded ||
        this == AdResult.Completed ||
        this == AdResult.Shown ||
        this == AdResult.Dismissed

/** ANL-2 — the analytics value-constant for this [AdFormat] (no enum name leakage). */
private val AdFormat.analyticsFormat: String
    get() = when (this) {
        AdFormat.REWARDED -> AnalyticsValues.FORMAT_REWARDED
        AdFormat.INTERSTITIAL -> AnalyticsValues.FORMAT_INTERSTITIAL
    }

/**
 * True only for [AdResult.Rewarded] — the single outcome on which a rewarded placement (ADS-2 revive,
 * ADS-3 streak-saver) may grant its reward. A convenience so callers never compare against the enum
 * directly and accidentally reward on `Dismissed`/`Completed`.
 */
val AdResult.isRewardEarned: Boolean
    get() = this == AdResult.Rewarded
