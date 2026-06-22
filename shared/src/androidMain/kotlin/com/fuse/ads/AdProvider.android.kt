package com.fuse.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * ADS-1 (Android) — binds [AdProvider] backed by the Google Mobile Ads SDK
 * (`com.google.android.gms:play-services-ads`), wired to Google's PUBLIC **test** units (rewarded AND
 * interstitial) read from [AdUnitIds].
 *
 * The application `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)`). A full-screen ad must be SHOWN from an `Activity` (not the
 * application context), so the current foreground Activity is read live from [AdActivityHolder], which
 * `MainActivity` keeps up to date. Bound as a `single` — one stateful wrapper caches the loaded ads.
 */
actual val platformAdsModule: Module = module {
    single<AdProvider> { AndroidAdProvider(context = get()) }
}

/**
 * Google Mobile Ads–backed [AdProvider] supporting both [AdFormat]s. [load] preloads + caches one ad
 * per format; [isReady] reflects the cache; [show] presents the cached ad and maps the SDK callbacks
 * to an [AdResult].
 *
 * Fully defensive: a missing Activity, a no-fill, or any SDK failure surfaces as `false`/[AdResult]
 * rather than a crash — this is behind a flag, never a hard dependency of the game.
 */
private class AndroidAdProvider(private val context: Context) : AdProvider {

    @Volatile
    private var initialized = false

    /** Cached, ready-to-show ads per format. A [show] consumes the entry. */
    private val rewardedAds = ConcurrentHashMap<AdFormat, RewardedAd>()
    private val interstitialAds = ConcurrentHashMap<AdFormat, InterstitialAd>()

    override fun initialize() {
        if (initialized) return
        runCatching {
            // The App ID comes from the `com.google.android.gms.ads.APPLICATION_ID` <meta-data> in
            // AndroidManifest (the SAMPLE App ID). MobileAds reads it at init; an empty listener
            // keeps init fire-and-forget.
            MobileAds.initialize(context) { }
            initialized = true
        }
    }

    override fun isReady(format: AdFormat): Boolean = when (format) {
        AdFormat.REWARDED -> rewardedAds.containsKey(format)
        AdFormat.INTERSTITIAL -> interstitialAds.containsKey(format)
    }

    override suspend fun load(format: AdFormat): Boolean {
        initialize()
        val activity = AdActivityHolder.current ?: context
        return when (format) {
            AdFormat.REWARDED -> loadRewarded(format, activity)
            AdFormat.INTERSTITIAL -> loadInterstitial(format, activity)
        }
    }

    private suspend fun loadRewarded(format: AdFormat, context: Context): Boolean =
        suspendCancellableCoroutine { cont ->
            runCatching {
                RewardedAd.load(
                    context,
                    AdUnitIds.android(format),
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            rewardedAds[format] = ad
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }

    private suspend fun loadInterstitial(format: AdFormat, context: Context): Boolean =
        suspendCancellableCoroutine { cont ->
            runCatching {
                InterstitialAd.load(
                    context,
                    AdUnitIds.android(format),
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAds[format] = ad
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }

    override suspend fun show(format: AdFormat): AdResult {
        val activity = AdActivityHolder.current ?: return AdResult.Failed
        return when (format) {
            AdFormat.REWARDED -> showRewarded(format, activity)
            AdFormat.INTERSTITIAL -> showInterstitial(format, activity)
        }
    }

    private suspend fun showRewarded(format: AdFormat, activity: Activity): AdResult {
        val ad = rewardedAds.remove(format) ?: return AdResult.NotReady
        return suspendCancellableCoroutine { cont ->
            var rewarded = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(if (rewarded) AdResult.Rewarded else AdResult.Dismissed)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(AdResult.Failed)
                }
            }
            runCatching {
                ad.show(activity) { rewarded = true }
            }.onFailure { if (cont.isActive) cont.resume(AdResult.Failed) }
        }
    }

    private suspend fun showInterstitial(format: AdFormat, activity: Activity): AdResult {
        val ad = interstitialAds.remove(format) ?: return AdResult.NotReady
        return suspendCancellableCoroutine { cont ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                // Interstitials carry no reward: a normal show+dismiss is Completed.
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(AdResult.Completed)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(AdResult.Failed)
                }
            }
            runCatching {
                ad.show(activity)
            }.onFailure { if (cont.isActive) cont.resume(AdResult.Failed) }
        }
    }
}
