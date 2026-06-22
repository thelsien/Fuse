package com.fuse.ads

import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * ADS-1 (iOS) — binds [AdProvider] through a Swift-implemented delegate registered at app launch.
 *
 * ## Why a delegate (and not a Kotlin/Native cinterop)
 * `GoogleMobileAds` is added to the **Xcode app** via Swift Package Manager (an SPM dependency of
 * `iosApp`, not of the `:shared` Kotlin framework). Kotlin/Native therefore has NO cinterop bindings
 * for it — referencing `platform.GoogleMobileAds.*` here would break
 * `:shared:linkDebugFrameworkIosSimulatorArm64` / `:shared:iosSimulatorArm64Test` on any machine/CI.
 *
 * So the seam is INVERTED on iOS: this Kotlin `actual` exposes a tiny [IosAdProviderBridge] interface
 * and the process-wide [IosAds] registration point. The **Swift** `AdsBridge` (which DOES see
 * `GoogleMobileAds`) implements the bridge — initialise `MobileAds`, load + present rewarded AND
 * interstitial test ads on the top view controller — and registers it at launch via
 * `IosAds.shared.register(bridge:)`. Until a bridge is registered (e.g. in commonTest), the provider
 * behaves like [NoOpAdProvider] — never crashes.
 *
 * Bound as a `single`, mirroring `platformSharerModule`.
 */
actual val platformAdsModule: Module = module {
    single<AdProvider> { IosAdProvider() }
}

/**
 * The Swift-side contract the iOS app implements (with `GoogleMobileAds`). Kotlin sees this as the
 * Obj-C protocol `IosAdProviderBridge`; Swift conforms to it. The [format] string is one of
 * "REWARDED"/"INTERSTITIAL" (the [AdFormat.name]); result strings map to [AdResult]
 * (see [IosAdProvider.toAdResult]) to keep the bridge free of Kotlin enums.
 *
 * `onResult` receives one of: "Shown", "Rewarded", "Dismissed", "Completed", "NoFill", "NotReady",
 * "Failed".
 */
interface IosAdProviderBridge {
    /** Initialise the Mobile Ads SDK (idempotent, best-effort). */
    fun initialize()

    /** Preload one ad of [format]; report `true` when an ad is cached and ready, `false` otherwise. */
    fun load(format: String, onLoaded: (Boolean) -> Unit)

    /** Whether an ad of [format] is currently loaded and ready to present. */
    fun isReady(format: String): Boolean

    /** Present the cached ad of [format]; report the coarse outcome string via [onResult]. */
    fun show(format: String, onResult: (String) -> Unit)
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
 * registered it behaves exactly like [NoOpAdProvider] — [initialize] no-ops, [load] reports `false`,
 * [isReady] is `false`, and [show] returns [AdResult.NotReady] — so the framework is fully functional
 * (and testable) before the SPM package / Swift bridge exists.
 */
private class IosAdProvider : AdProvider {
    override fun initialize() {
        IosAds.bridge?.initialize()
    }

    override suspend fun load(format: AdFormat): Boolean {
        val bridge = IosAds.bridge ?: return false
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.load(format.name) { ready ->
                    if (cont.isActive) cont.resume(ready)
                }
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }
    }

    override fun isReady(format: AdFormat): Boolean =
        IosAds.bridge?.isReady(format.name) ?: false

    override suspend fun show(format: AdFormat): AdResult {
        val bridge = IosAds.bridge ?: return AdResult.NotReady
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.show(format.name) { result ->
                    if (cont.isActive) cont.resume(toAdResult(result))
                }
            }.onFailure { if (cont.isActive) cont.resume(AdResult.Failed) }
        }
    }

    private fun toAdResult(raw: String): AdResult = when (raw) {
        "Shown" -> AdResult.Shown
        "Rewarded" -> AdResult.Rewarded
        "Dismissed" -> AdResult.Dismissed
        "Completed" -> AdResult.Completed
        "NoFill" -> AdResult.NoFill
        "NotReady" -> AdResult.NotReady
        else -> AdResult.Failed
    }
}
