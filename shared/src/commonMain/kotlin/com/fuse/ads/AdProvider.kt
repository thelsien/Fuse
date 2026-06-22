package com.fuse.ads

import org.koin.core.module.Module

/**
 * ADS-0 (Sprint 8 spike) — the MINIMAL cross-platform ad seam.
 *
 * This is the FIRST `AdProvider` `expect`/`actual` platform service and mirrors the existing
 * platform seams ([com.fuse.daily.platformSharerModule],
 * [com.fuse.feedback.platformHapticsModule], [com.fuse.feedback.platformSoundModule],
 * [com.fuse.data.platformSettingsModule]): a thin `interface` in `commonMain` with per-platform
 * `actual` Koin modules ([platformAdsModule]) wiring a real implementation.
 *
 * ## Scope — this is a SPIKE, deliberately tiny
 * The single goal of ADS-0 is to retire the native-SDK risk by proving a **test ad renders on
 * both platforms** through this seam, and to document the integration gotchas. So the surface is
 * intentionally minimal:
 *  - [initialize] — start the underlying Mobile Ads SDK once (idempotent; safe to call repeatedly).
 *  - [showRewardedTestAd] — load AND show ONE Google-test rewarded ad, returning a coarse
 *    [AdResult]. Always wired to Google's PUBLIC test ad unit (no real placement, no account ID).
 *
 * ADS-1 GENERALIZES this (load/show as separate steps, rewarded + interstitial, a real result
 * channel, fakeable in tests). ADS-2/3/4 wire real placements (game-over rewarded continue,
 * interstitial cadence). DO NOT extend this surface here.
 *
 * ## Test-only ad units
 * Implementations MUST use only Google's PUBLIC test ad unit IDs (rewarded:
 * `ca-app-pub-3940256099942544/5224354917` on Android, `…/1712485313` on iOS). Those serve only
 * test ads and are safe to commit. Real AdMob unit IDs and the app's AdMob App ID are a
 * release-time concern injected later from a gitignored config — see the ADS-1 notes / the
 * spike's gotchas doc. NO real IDs, keys, or credentials live in this repo.
 *
 * ## Behind a flag
 * The spike is gated behind a debug trigger (a debug-only "Show test ad" button in Settings),
 * so it cannot destabilise the normal app flow. It is NOT wired into game-over/replay yet
 * (that is ADS-2/4). Implementations are DEFENSIVE: any SDK/load/show failure surfaces as an
 * [AdResult] (never a crash).
 */
interface AdProvider {
    /**
     * Initialises the underlying Mobile Ads SDK. Idempotent and best-effort — safe to call more
     * than once, and a failure here must not crash the app (it simply means a later
     * [showRewardedTestAd] returns [AdResult.Failed]).
     */
    fun initialize()

    /**
     * Loads AND shows ONE Google-**test** rewarded ad, suspending until the ad is dismissed (or
     * the load/show fails), and returns the coarse outcome.
     *
     * Always best-effort: implementations never throw — every failure path (no SDK, no-fill,
     * present failure, no view controller on iOS) maps to an [AdResult] so the caller (the debug
     * trigger) can report what happened.
     */
    suspend fun showRewardedTestAd(): AdResult
}

/**
 * The coarse outcome of [AdProvider.showRewardedTestAd]. Kept deliberately small for the spike;
 * ADS-1 will refine this (e.g. separate load vs show errors, the reward amount/type).
 */
enum class AdResult {
    /** The ad was presented to the user (it appeared on screen). */
    Shown,

    /** The user watched the rewarded ad to the point that the reward was earned. */
    Rewarded,

    /** The ad was dismissed by the user (shown, but closed before/after earning the reward). */
    Dismissed,

    /** The ad network had no ad to serve (no-fill). Expected occasionally even for test ads. */
    NoFill,

    /** The SDK/load/show failed for any other reason (not initialised, present error, etc.). */
    Failed,
}

/**
 * A no-op [AdProvider] — the safe default for tests/previews and for any build where ads are
 * disabled. [initialize] does nothing and [showRewardedTestAd] reports [AdResult.Failed] (there
 * is no real ad to show), so callers/tests are fully deterministic with no SDK on the classpath.
 */
object NoOpAdProvider : AdProvider {
    override fun initialize() { /* no-op */ }
    override suspend fun showRewardedTestAd(): AdResult = AdResult.Failed
}

/**
 * Per-platform Koin module binding the [AdProvider] `single`, mirroring [com.fuse.daily.platformSharerModule].
 *
 *  - **Android** (`AdProvider.android.kt`): wraps the Google Mobile Ads SDK
 *    (`com.google.android.gms:play-services-ads`). Initialises `MobileAds`, then loads + shows a
 *    `RewardedAd` from Google's TEST rewarded unit using the current `Activity` (resolved from the
 *    Koin graph). Defensive — every callback failure maps to an [AdResult].
 *  - **iOS** (`AdProvider.ios.kt`): wraps the `GoogleMobileAds` SDK (added via Swift Package
 *    Manager to `iosApp.xcodeproj`). Initialises `MobileAds`, then loads + presents a rewarded ad
 *    from the iOS TEST unit on the top view controller. ⚠️ Compiles only once the SPM package is
 *    present in the Xcode project — see the spike's gotchas doc for the one manual Xcode step.
 *
 * Registered in `appModules` (see `com.fuse.di.Modules`).
 */
expect val platformAdsModule: Module
