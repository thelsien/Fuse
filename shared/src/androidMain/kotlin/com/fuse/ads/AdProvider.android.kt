package com.fuse.ads

import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * ADS-0 (Android) — binds [AdProvider] backed by the Google Mobile Ads SDK
 * (`com.google.android.gms:play-services-ads`), wired to Google's PUBLIC **test** rewarded unit.
 *
 * The application `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)`), mirroring `platformSharerModule` /
 * `platformSettingsModule`. The CURRENT `Activity` (a rewarded ad must be SHOWN from an Activity,
 * not the application context) is read live from [AdActivityHolder], which `MainActivity`
 * keeps up to date. Bound as a `single` — stateless wrapper, one is enough.
 */
actual val platformAdsModule: Module = module {
    single<AdProvider> { AndroidAdProvider(context = get()) }
}

/** Google's PUBLIC test rewarded ad unit (Android). Serves only test ads — safe to commit. */
private const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

/** Internal load outcome so the load callback maps cleanly onto an [AdResult] on failure. */
private sealed interface LoadOutcome {
    data class Loaded(val ad: RewardedAd) : LoadOutcome
    data class Failed(val result: AdResult) : LoadOutcome
}

/**
 * Google Mobile Ads–backed [AdProvider]. Initialises `MobileAds`, then loads + shows ONE TEST
 * rewarded ad and maps the SDK callbacks to a coarse [AdResult].
 *
 * Fully defensive: initialisation and the load/show are guarded so a missing Activity, a no-fill,
 * or any SDK failure surfaces as an [AdResult] rather than a crash — this is a behind-a-flag spike
 * trigger, never a hard dependency of the game.
 */
private class AndroidAdProvider(private val context: Context) : AdProvider {

    @Volatile
    private var initialized = false

    override fun initialize() {
        if (initialized) return
        runCatching {
            // The App ID itself comes from the `com.google.android.gms.ads.APPLICATION_ID`
            // <meta-data> in AndroidManifest (the SAMPLE App ID — see the manifest). MobileAds
            // reads it at init; passing an empty listener keeps init fire-and-forget.
            MobileAds.initialize(context) { }
            initialized = true
        }
    }

    override suspend fun showRewardedTestAd(): AdResult {
        initialize()
        val activity = AdActivityHolder.current
            ?: return AdResult.Failed // no Activity to present from

        // 1) Load the test rewarded ad.
        val outcome: LoadOutcome = suspendCancellableCoroutine { cont ->
            runCatching {
                RewardedAd.load(
                    activity,
                    TEST_REWARDED_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(rewardedAd: RewardedAd) {
                            if (cont.isActive) cont.resume(LoadOutcome.Loaded(rewardedAd))
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            val result =
                                if (error.code == AdRequest.ERROR_CODE_NO_FILL) AdResult.NoFill
                                else AdResult.Failed
                            if (cont.isActive) cont.resume(LoadOutcome.Failed(result))
                        }
                    },
                )
            }.onFailure {
                if (cont.isActive) cont.resume(LoadOutcome.Failed(AdResult.Failed))
            }
        }

        val ad = when (outcome) {
            is LoadOutcome.Loaded -> outcome.ad
            is LoadOutcome.Failed -> return outcome.result
        }

        // 2) Show it and collect the outcome.
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
}
