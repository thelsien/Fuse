package com.fuse.ads

import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * ADS-0 (iOS) — binds [AdProvider] through a Swift-implemented delegate registered at app launch.
 *
 * ## Why a delegate (and not a Kotlin/Native cinterop)
 * The `GoogleMobileAds` SDK is added to the **Xcode app** via Swift Package Manager (it is an
 * SPM dependency of `iosApp`, not of the `:shared` Kotlin framework). Kotlin/Native therefore has
 * NO cinterop bindings for `GoogleMobileAds` — referencing `platform.GoogleMobileAds.*` from this
 * file would break `:shared:linkDebugFrameworkIosSimulatorArm64` and `:shared:iosSimulatorArm64Test`
 * on every machine/CI that has not added the package.
 *
 * So the seam is INVERTED on iOS: this Kotlin `actual` exposes a tiny [IosAdProviderBridge]
 * interface and a process-wide [IosAds] registration point. The **Swift** side (which DOES see
 * `GoogleMobileAds`) implements the bridge — initialise `MobileAds`, load + present a rewarded
 * test ad from the top view controller — and registers it at launch (in `iOSApp` / `AppDelegate`)
 * via `IosAds.shared.register(bridge:)`. Until Swift registers a bridge (e.g. in commonTest, or
 * before the SPM package exists), the provider safely reports [AdResult.Failed] — never crashes.
 *
 * This keeps the Kotlin framework compiling with ZERO third-party iOS deps, while the real ad code
 * lives in Swift where the SDK is available. See the spike's gotchas doc for the exact Swift
 * snippet + the one manual Xcode step (File ▸ Add Package Dependencies… the swift-package-manager
 * -google-mobile-ads URL ▸ add `GoogleMobileAds` to the iosApp target).
 *
 * Bound as a `single`, mirroring `platformSharerModule`.
 */
actual val platformAdsModule: Module = module {
    single<AdProvider> { IosAdProvider() }
}

/**
 * The Swift-side contract the iOS app implements (with `GoogleMobileAds`). Kotlin sees this as the
 * Obj-C protocol `IosAdProviderBridge`; Swift conforms to it. Result strings map to [AdResult]
 * (see [IosAdProvider.toAdResult]) to keep the bridge free of Kotlin enums.
 *
 * `onResult` receives one of: "Shown", "Rewarded", "Dismissed", "NoFill", "Failed".
 */
interface IosAdProviderBridge {
    /** Initialise the Mobile Ads SDK (idempotent, best-effort). */
    fun initialize()

    /** Load + present a rewarded TEST ad; report the coarse outcome string via [onResult]. */
    fun showRewardedTestAd(onResult: (String) -> Unit)
}

/**
 * Process-wide registration point for the Swift [IosAdProviderBridge]. The iOS app calls
 * `IosAds.register(...)` at launch; the Kotlin [IosAdProvider] delegates to whatever is registered.
 */
object IosAds {
    @kotlin.concurrent.Volatile
    var bridge: IosAdProviderBridge? = null
        private set

    /** Registers the Swift-implemented [bridge]. Called once from the iOS app at launch. */
    fun register(bridge: IosAdProviderBridge) {
        this.bridge = bridge
    }
}

/**
 * Kotlin [AdProvider] that forwards to the registered Swift [IosAdProviderBridge]. With no bridge
 * registered it behaves exactly like [NoOpAdProvider] — [initialize] no-ops and
 * [showRewardedTestAd] returns [AdResult.Failed] — so the framework is fully functional (and
 * testable) before the SPM package / Swift bridge exists.
 */
private class IosAdProvider : AdProvider {
    override fun initialize() {
        IosAds.bridge?.initialize()
    }

    override suspend fun showRewardedTestAd(): AdResult {
        val bridge = IosAds.bridge ?: return AdResult.Failed
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.showRewardedTestAd { result ->
                    if (cont.isActive) cont.resume(toAdResult(result))
                }
            }.onFailure { if (cont.isActive) cont.resume(AdResult.Failed) }
        }
    }

    private fun toAdResult(raw: String): AdResult = when (raw) {
        "Shown" -> AdResult.Shown
        "Rewarded" -> AdResult.Rewarded
        "Dismissed" -> AdResult.Dismissed
        "NoFill" -> AdResult.NoFill
        else -> AdResult.Failed
    }
}
