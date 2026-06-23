package com.fuse.iap

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.fuse.ads.AdActivityHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * IAP-0 (Android) — binds [BillingProvider] backed by the Google Play Billing Library
 * (`com.android.billingclient:billing`), querying the single non-consumable [Iap.PRODUCT_REMOVE_ADS].
 *
 * The application `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)`). [BillingClient.launchBillingFlow] requires an `Activity`,
 * so the current foreground Activity is read live from [com.fuse.ads.AdActivityHolder] (the same weak
 * holder ADS-0 introduced and `MainActivity` keeps up to date). Bound as a `single` — one stateful
 * client owns the connection + the in-flight purchase continuation.
 *
 * **Spike scope:** this COMPILES and CONNECTS and runs the product-query + launch-flow paths. A real
 * sandbox round-trip needs a Play Console (internal-testing track, the `remove_ads` product, a
 * license tester) — see `docs/iap/IAP-0-gotchas.md`. Until then `queryProductDetailsAsync` returns no
 * products for an unpublished id, so [products] is empty and [purchase] reports
 * [PurchaseResult.Failed] — never a crash.
 */
actual val platformBillingModule: Module = module {
    single<BillingProvider> { AndroidBillingProvider(context = get()) }
}

/**
 * Play-Billing–backed [BillingProvider]. Connects lazily, queries one-time products, launches the
 * purchase flow from the foreground Activity, acknowledges verified purchases, and reads current
 * entitlements via `queryPurchasesAsync`. Fully defensive: a failed connection, an unconfigured
 * product, a missing Activity, or any SDK error surfaces as an empty list / [PurchaseResult.Failed].
 */
private class AndroidBillingProvider(private val context: Context) : BillingProvider {

    /**
     * The continuation for the in-flight [purchase], completed by the [purchasesUpdatedListener]
     * (Play delivers purchase results out of band, not from `launchBillingFlow`'s return value).
     */
    @Volatile
    private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val deferred = pendingPurchase ?: return@PurchasesUpdatedListener
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                if (purchase == null) {
                    deferred.complete(PurchaseResult.Failed)
                } else {
                    // Acknowledge then resolve. We acknowledge a PURCHASED, not-yet-acked purchase so
                    // Play does not auto-refund it after 3 days. PENDING resolves later out of band.
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            acknowledgeIfNeeded(purchase)
                            deferred.complete(PurchaseResult.Purchased)
                        }
                        Purchase.PurchaseState.PENDING -> deferred.complete(PurchaseResult.Pending)
                        else -> deferred.complete(PurchaseResult.Failed)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> deferred.complete(PurchaseResult.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> deferred.complete(PurchaseResult.AlreadyOwned)
            else -> deferred.complete(PurchaseResult.Failed)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Ensures the client is connected; suspends until the connection resolves. Returns success. */
    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) {
                            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }
    }

    override suspend fun products(ids: List<String>): List<Product> {
        if (!ensureConnected()) return emptyList()
        val productList = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.queryProductDetailsAsync(params) { result, queryResult ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        if (cont.isActive) cont.resume(emptyList())
                        return@queryProductDetailsAsync
                    }
                    val products = queryResult.productDetailsList.mapNotNull { it.toProduct() }
                    if (cont.isActive) cont.resume(products)
                }
            }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
        }
    }

    override suspend fun purchase(id: String): PurchaseResult {
        if (!ensureConnected()) return PurchaseResult.Failed
        val activity = AdActivityHolder.current ?: return PurchaseResult.Failed
        val details = queryProductDetails(id) ?: return PurchaseResult.Failed

        val deferred = CompletableDeferred<PurchaseResult>()
        pendingPurchase = deferred
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val launch = runCatching { client.launchBillingFlow(activity, flowParams) }.getOrNull()
        if (launch == null || launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            // ITEM_ALREADY_OWNED can come back synchronously here for an owned non-consumable.
            return if (launch?.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                PurchaseResult.AlreadyOwned
            } else {
                PurchaseResult.Failed
            }
        }
        return runCatching { deferred.await() }.getOrDefault(PurchaseResult.Failed)
            .also { pendingPurchase = null }
    }

    /**
     * IAP-3 — restore on Play Billing IS `queryPurchasesAsync(INAPP)`: Play has no separate "restore"
     * API; the account's purchases are authoritative and available offline-cached on a fresh install
     * once Play services sync. We map PURCHASED entries to their product ids AND re-acknowledge any
     * that arrived un-acknowledged (e.g. a purchase made on another device, restored here before this
     * install acked it) so Play does not auto-refund them. Defensive: empty list on any failure.
     */
    override suspend fun restore(): List<String> = queryOwned(acknowledge = true).toList()

    override suspend fun ownedProductIds(): Set<String> = queryOwned(acknowledge = false)

    /**
     * Queries the PURCHASED non-consumables. When [acknowledge] (the IAP-3 restore path), also
     * re-acknowledges any purchased-but-unacknowledged entitlement so Play finalizes it.
     */
    private suspend fun queryOwned(acknowledge: Boolean): Set<String> {
        if (!ensureConnected()) return emptySet()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.queryPurchasesAsync(params) { result, purchases ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        if (cont.isActive) cont.resume(emptySet())
                        return@queryPurchasesAsync
                    }
                    val purchased = purchases
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    if (acknowledge) purchased.forEach { acknowledgeIfNeeded(it) }
                    val owned = purchased.flatMap { it.products }.toSet()
                    if (cont.isActive) cont.resume(owned)
                }
            }.onFailure { if (cont.isActive) cont.resume(emptySet()) }
        }
    }

    private suspend fun queryProductDetails(id: String): ProductDetails? {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.queryProductDetailsAsync(params) { result, queryResult ->
                    val details = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryResult.productDetailsList.firstOrNull { it.productId == id }
                    } else {
                        null
                    }
                    if (cont.isActive) cont.resume(details)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        runCatching {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* best-effort; failure is non-fatal for the spike */ }
        }
    }

    private fun ProductDetails.toProduct(): Product? {
        val price = oneTimePurchaseOfferDetails?.formattedPrice ?: return null
        return Product(id = productId, title = title, price = price)
    }
}
