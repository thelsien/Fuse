package com.fuse.ads

import org.koin.core.module.Module

/**
 * ADS-1 (Sprint 8) — the GENERALIZED cross-platform ad seam.
 *
 * ADS-0 proved a single Google-test **rewarded** ad renders on both platforms through a minimal
 * `expect`/`actual` provider. ADS-1 generalizes that spike into a clean, **format-aware**,
 * **fakeable** contract that exposes the three steps a real placement needs — **load**, **isReady**,
 * **show** — for BOTH ad formats we will use ([AdFormat.REWARDED] and [AdFormat.INTERSTITIAL]). The
 * native SDKs stay hidden behind this boundary, so the feature stories (ADS-2/3/4) are built and
 * unit-tested against [FakeAdProvider] with no SDK on the classpath.
 *
 * ## Surface (one coherent shape — per-format via an [AdFormat] argument)
 *  - [initialize] — start the underlying Mobile Ads SDK once (idempotent, best-effort, never throws).
 *  - [load] — preload one ad of the given [format]; returns `true` when an ad is ready to show,
 *    `false` on no-fill/failure. Suspends until the network responds.
 *  - [isReady] — whether a previously [load]ed ad of that [format] is cached and ready to [show].
 *    (`load` populates the cache; a successful [show] consumes it.)
 *  - [show] — present the cached ad of the given [format], suspending until it is dismissed (or the
 *    show fails), returning an [AdResult]. If nothing is loaded, returns [AdResult.NotReady] — it
 *    does NOT implicitly load (callers preload, then show; or use [AdManager] for load-then-show).
 *
 * The spike's [showRewardedTestAd] is retained as a thin convenience that does the old load+show in
 * one call (it delegates to `load(REWARDED)` then `show(REWARDED)`), so the existing debug trigger
 * keeps working unchanged. New code should use the format-aware API (or [AdManager]).
 *
 * ## Result semantics per format (see [AdResult])
 *  - **REWARDED**: a fully-watched ad yields [AdResult.Rewarded] (the ONLY signal ADS-2/3 may grant a
 *    reward on); closing early yields [AdResult.Dismissed]; no inventory → [AdResult.NoFill]; any SDK
 *    error → [AdResult.Failed]; nothing loaded → [AdResult.NotReady].
 *  - **INTERSTITIAL**: there is no reward, so a normally-watched-and-closed ad yields
 *    [AdResult.Completed] (== "shown and dismissed"); [AdResult.NoFill] / [AdResult.Failed] /
 *    [AdResult.NotReady] carry the same meaning. ADS-4 treats anything other than `Completed` as
 *    "not shown".
 *
 * ## Test-only ad units
 * Implementations use ONLY Google's PUBLIC test ad unit IDs, centralised in [AdUnitIds] — the single
 * place where real (gitignored) unit IDs get injected at release time. NO real IDs / keys in the repo.
 *
 * ## Behind a flag
 * Still gated by the debug trigger ([AdsDebug]); NOT wired into game-over/daily yet (that's ADS-2/3/4).
 * All implementations are DEFENSIVE — every load/show failure surfaces as an [AdResult], never a crash.
 */
interface AdProvider {
    /**
     * Initialises the underlying Mobile Ads SDK. Idempotent and best-effort — safe to call more than
     * once, and a failure here must not crash the app (it simply means later [load]/[show] calls
     * report [AdResult.Failed] / `false`).
     */
    fun initialize()

    /**
     * Preloads one ad of [format]. Suspends until the ad network responds. Returns `true` when an ad
     * is cached and ready (a subsequent [isReady] is `true` and [show] can present it), `false` on
     * no-fill or any failure. Never throws.
     */
    suspend fun load(format: AdFormat): Boolean

    /**
     * Whether an ad of [format] has been [load]ed and is ready to [show]. A successful [show] consumes
     * the cached ad (so [isReady] returns `false` again until the next [load]).
     */
    fun isReady(format: AdFormat): Boolean

    /**
     * Presents the cached ad of [format], suspending until it is dismissed (or the show fails), and
     * returns the [AdResult]. Does NOT implicitly load: with nothing ready, returns
     * [AdResult.NotReady]. See the per-format result semantics on [AdProvider]. Never throws.
     */
    suspend fun show(format: AdFormat): AdResult

    /**
     * Convenience retained from ADS-0: load AND show one Google-**test** rewarded ad in a single call
     * (used by the debug "Show test ad" trigger). Equivalent to `load(REWARDED)` then `show(REWARDED)`,
     * collapsing a failed/empty load to [AdResult.NoFill]/[AdResult.Failed]. Never throws.
     */
    suspend fun showRewardedTestAd(): AdResult {
        return if (load(AdFormat.REWARDED)) show(AdFormat.REWARDED) else AdResult.NoFill
    }
}

/** The ad formats Fuse uses. REWARDED backs ADS-2/3 (revive, streak-saver); INTERSTITIAL backs ADS-4. */
enum class AdFormat {
    /** Full-screen opt-in video that grants a reward when watched to completion (the revive/streak ads). */
    REWARDED,

    /** Full-screen ad shown at a natural break with no reward (the cadence interstitial). */
    INTERSTITIAL,
}

/**
 * The outcome of [AdProvider.show] (and [AdProvider.load]'s downstream show). Expressive enough for
 * both formats — see the per-format semantics documented on [AdProvider].
 *
 * Kept as an `enum` (not a sealed class) so it crosses the Kotlin/Native bridge cleanly and the
 * debug UI can render `result.name`. A failure *reason* string is NOT modelled here — the thin native
 * actuals log details; callers only branch on these coarse cases.
 */
enum class AdResult {
    /**
     * The ad was presented to the user (it appeared on screen). A transient signal; for terminal
     * outcomes prefer [Rewarded]/[Dismissed]/[Completed]. Retained from ADS-0 for the debug trigger.
     */
    Shown,

    /** REWARDED only — the user watched to the point the reward was earned. The ONLY grant signal. */
    Rewarded,

    /** REWARDED — the ad was closed before the reward was earned (shown but not rewarded). */
    Dismissed,

    /** INTERSTITIAL — the ad was shown and dismissed normally (there is no reward to earn). */
    Completed,

    /** The ad network had no ad to serve (no-fill). Expected occasionally even for test ads. */
    NoFill,

    /** [show] was called but no ad of that format was [load]ed (or it was already consumed). */
    NotReady,

    /** The SDK/load/show failed for any other reason (not initialised, present error, etc.). */
    Failed,
}

/**
 * The PUBLIC Google test ad unit IDs, per platform and [AdFormat] — the single seam where real,
 * gitignored unit IDs are injected at release time.
 *
 * Today every getter returns a Google PUBLIC TEST unit (under publisher `ca-app-pub-3940256099942544`),
 * which serves only test ads and is safe to commit. At release, this object (or a per-flavour
 * `actual`/build-config value feeding it) is swapped for one returning the real AdMob unit IDs from a
 * gitignored config — callers and the native actuals read IDs exclusively through here, so no real ID
 * is ever hardcoded in provider code. The app's AdMob *App* ID stays in the Android manifest /
 * iOS Info.plist (also SAMPLE today), out of band from these unit IDs.
 */
object AdUnitIds {
    /** Google PUBLIC test units (Android). */
    const val ANDROID_REWARDED: String = "ca-app-pub-3940256099942544/5224354917"
    const val ANDROID_INTERSTITIAL: String = "ca-app-pub-3940256099942544/1033173712"

    /** Google PUBLIC test units (iOS). */
    const val IOS_REWARDED: String = "ca-app-pub-3940256099942544/1712485313"
    const val IOS_INTERSTITIAL: String = "ca-app-pub-3940256099942544/4411468910"

    /** The Android unit for [format]. */
    fun android(format: AdFormat): String = when (format) {
        AdFormat.REWARDED -> ANDROID_REWARDED
        AdFormat.INTERSTITIAL -> ANDROID_INTERSTITIAL
    }

    /** The iOS unit for [format]. */
    fun ios(format: AdFormat): String = when (format) {
        AdFormat.REWARDED -> IOS_REWARDED
        AdFormat.INTERSTITIAL -> IOS_INTERSTITIAL
    }
}

/**
 * A no-op [AdProvider] — the safe default for previews and for any build where ads are disabled.
 * [initialize] does nothing, [load] always reports `false`, [isReady] is always `false`, and [show]
 * returns [AdResult.NotReady], so callers/tests are fully deterministic with no SDK on the classpath.
 * (For SCRIPTED outcomes in feature-story tests, use [FakeAdProvider] instead.)
 */
object NoOpAdProvider : AdProvider {
    override fun initialize() { /* no-op */ }
    override suspend fun load(format: AdFormat): Boolean = false
    override fun isReady(format: AdFormat): Boolean = false
    override suspend fun show(format: AdFormat): AdResult = AdResult.NotReady
}

/**
 * Per-platform Koin module binding the [AdProvider] `single`, mirroring [com.fuse.daily.platformSharerModule].
 *
 *  - **Android** (`AdProvider.android.kt`): Google Mobile Ads (`play-services-ads`). Loads + shows
 *    rewarded AND interstitial test ads from the current `Activity`. Defensive — failures map to [AdResult].
 *  - **iOS** (`AdProvider.ios.kt`): inverts the seam to a Swift `AdsBridge` (GoogleMobileAds via SPM),
 *    which loads + presents rewarded AND interstitial test ads on the top view controller.
 *
 * Tests inject [FakeAdProvider] (or [NoOpAdProvider]) instead. Registered in `appModules` (see
 * `com.fuse.di.Modules`).
 */
expect val platformAdsModule: Module
