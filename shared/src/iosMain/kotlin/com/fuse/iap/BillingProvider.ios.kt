package com.fuse.iap

import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * IAP-0 (iOS) — binds [BillingProvider] through a Swift-implemented [IosBillingBridge] registered at
 * app launch, mirroring how `AdProvider.ios.kt` inverts the ad seam to a Swift `AdsBridge`.
 *
 * ## Why a Swift bridge (and not Kotlin/Native StoreKit cinterop)
 * StoreKit 2's modern async API (`Product.products(for:)`, `product.purchase()`,
 * `Transaction.currentEntitlements`) is a Swift-first API built on `async`/`await` and Swift
 * concurrency. It is awkward-to-impossible to call cleanly from Kotlin/Native's Foundation/StoreKit
 * cinterop (the StoreKit 2 `Product`/`Transaction` types are Swift value types, not Obj-C classes).
 * Referencing them from Kotlin would also be fragile across Xcode versions and would break
 * `:shared:linkDebugFrameworkIosSimulatorArm64` / `:shared:iosSimulatorArm64Test` on CI.
 *
 * So the seam is INVERTED, exactly like the ads spike: this Kotlin `actual` exposes the tiny
 * [IosBillingBridge] Obj-C protocol + the process-wide [IosBilling] registration point, and the
 * **Swift** `BillingBridge` (which uses StoreKit 2) implements it and registers itself at launch via
 * `IosBilling.shared.register(bridge:)`. Until a bridge is registered (e.g. in commonTest), the
 * provider behaves like [NoOpBillingProvider] — never crashes.
 *
 * Result strings ("Purchased"/"Cancelled"/"Pending"/"AlreadyOwned"/"Failed") map to [PurchaseResult]
 * (see [toPurchaseResult]) to keep the bridge free of Kotlin enums. Bound as a `single`.
 */
actual val platformBillingModule: Module = module {
    single<BillingProvider> { IosBillingProvider() }
}

/**
 * The Swift-side contract the iOS app implements (with StoreKit 2). Kotlin sees this as the Obj-C
 * protocol `IosBillingBridge`; Swift conforms to it. All methods are async, so they take completion
 * closures (the Swift side hops off its `async` context into these), keeping the protocol Obj-C-clean.
 *
 *  - [products] reports localized id/title/price triples via [onResult] (a parallel-array shape so
 *    the bridge avoids defining a shared struct type).
 *  - [purchase] reports one of the [PurchaseResult] name strings.
 *  - [restore] / [ownedProductIds] report the owned product ids.
 */
interface IosBillingBridge {
    /** Fetch products for [ids]; report parallel arrays of the SAME length (ids/titles/prices). */
    fun products(ids: List<String>, onResult: (List<String>, List<String>, List<String>) -> Unit)

    /** Run the purchase flow for [id]; report one of the [PurchaseResult] name strings via [onResult]. */
    fun purchase(id: String, onResult: (String) -> Unit)

    /** Re-sync and report the owned product ids via [onResult]. */
    fun restore(onResult: (List<String>) -> Unit)

    /** Report the currently-entitled product ids (from `Transaction.currentEntitlements`) via [onResult]. */
    fun ownedProductIds(onResult: (List<String>) -> Unit)
}

/**
 * Process-wide registration point for the Swift [IosBillingBridge]. The iOS app calls
 * `IosBilling.register(...)` at launch; the Kotlin [IosBillingProvider] delegates to whatever is
 * registered, mirroring [com.fuse.ads.IosAds].
 */
object IosBilling {
    @kotlin.concurrent.Volatile
    var bridge: IosBillingBridge? = null
        private set

    /** Registers the Swift-implemented [bridge]. Called once from the iOS app at launch. */
    fun register(bridge: IosBillingBridge) {
        this.bridge = bridge
    }
}

/**
 * Kotlin [BillingProvider] that forwards to the registered Swift [IosBillingBridge]. With no bridge
 * registered it behaves exactly like [NoOpBillingProvider] — empty products/ownership and a
 * [PurchaseResult.Failed] purchase — so the framework is fully functional (and testable) before the
 * Swift bridge exists.
 */
private class IosBillingProvider : BillingProvider {
    override suspend fun products(ids: List<String>): List<Product> {
        val bridge = IosBilling.bridge ?: return emptyList()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.products(ids) { gotIds, titles, prices ->
                    val products = gotIds.indices.mapNotNull { i ->
                        val id = gotIds.getOrNull(i) ?: return@mapNotNull null
                        Product(
                            id = id,
                            title = titles.getOrNull(i) ?: id,
                            price = prices.getOrNull(i) ?: "",
                        )
                    }
                    if (cont.isActive) cont.resume(products)
                }
            }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
        }
    }

    override suspend fun purchase(id: String): PurchaseResult {
        val bridge = IosBilling.bridge ?: return PurchaseResult.Failed
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.purchase(id) { result ->
                    if (cont.isActive) cont.resume(toPurchaseResult(result))
                }
            }.onFailure { if (cont.isActive) cont.resume(PurchaseResult.Failed) }
        }
    }

    override suspend fun restore(): List<String> {
        val bridge = IosBilling.bridge ?: return emptyList()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.restore { owned -> if (cont.isActive) cont.resume(owned) }
            }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
        }
    }

    override suspend fun ownedProductIds(): Set<String> {
        val bridge = IosBilling.bridge ?: return emptySet()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                bridge.ownedProductIds { owned -> if (cont.isActive) cont.resume(owned.toSet()) }
            }.onFailure { if (cont.isActive) cont.resume(emptySet()) }
        }
    }

    private fun toPurchaseResult(raw: String): PurchaseResult = when (raw) {
        "Purchased" -> PurchaseResult.Purchased
        "Cancelled" -> PurchaseResult.Cancelled
        "Pending" -> PurchaseResult.Pending
        "AlreadyOwned" -> PurchaseResult.AlreadyOwned
        else -> PurchaseResult.Failed
    }
}
