package com.fuse.iap

/**
 * IAP-0 — a configurable, fully deterministic [BillingProvider] for tests/previews.
 *
 * Lives in `commonMain` (not `commonTest`) so the feature stories (IAP-1..4) can inject it from their
 * own test source sets and from previews without depending on `:shared` test fixtures, mirroring
 * [com.fuse.ads.FakeAdProvider]. It holds NO store SDK reference, so it runs on JVM and
 * Kotlin/Native alike.
 *
 * ## What it does
 *  - **Catalog.** [products] returns the [catalog] entries whose id is in the requested list, so a
 *    test can assert "the paywall got `remove_ads` at the configured localized price".
 *  - **Scripted purchase results.** [scriptPurchase] queues per-id outcomes that [purchase] returns
 *    in order; once a product's queue drains, [defaultPurchaseResult] is returned. A
 *    [PurchaseResult.Purchased] (or [PurchaseResult.AlreadyOwned]) marks the id owned so
 *    [ownedProductIds]/[restore] reflect it — mirroring how a real store records the entitlement.
 *  - **Ownership.** [owned] can be pre-seeded (e.g. to test IAP-2's "already entitled" path);
 *    [ownedProductIds] and [restore] both return it.
 *  - **Call recording.** Every call is recorded ([productsCalls], [purchaseCalls], [restoreCount],
 *    [ownedCount], [calls]) so a test can assert what the view-model requested.
 *
 * All mutation is single-threaded test usage; not synchronised.
 *
 * ### Example (IAP-2 entitlement)
 * ```
 * val billing = FakeBillingProvider().apply { scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased) }
 * assertEquals(PurchaseResult.Purchased, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
 * assertTrue(Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds()) // entitlement recorded
 * ```
 */
class FakeBillingProvider(
    /** The products [products] can return, keyed by id. Defaults to the spike's `remove_ads` at $3.99. */
    val catalog: MutableMap<String, Product> = mutableMapOf(
        Iap.PRODUCT_REMOVE_ADS to Product(Iap.PRODUCT_REMOVE_ADS, "Remove Ads", "$3.99"),
    ),
    /** Result returned by [purchase] once a product's scripted queue drains. */
    var defaultPurchaseResult: PurchaseResult = PurchaseResult.Purchased,
) : BillingProvider {

    /** The product ids currently owned. Pre-seed to test "already entitled"; a purchase adds to it. */
    val owned: MutableSet<String> = mutableSetOf()

    private val scripted: MutableMap<String, ArrayDeque<PurchaseResult>> = mutableMapOf()

    /** A recorded call against the fake. */
    sealed interface Call {
        data class Products(val ids: List<String>) : Call
        data class Purchase(val id: String) : Call
        data object Restore : Call
        data object Owned : Call
    }

    /** Every call in order — for fine-grained assertions on the request sequence. */
    val calls: MutableList<Call> = mutableListOf()

    /** Id lists passed to [products], in order. */
    val productsCalls: MutableList<List<String>> = mutableListOf()

    /** Ids passed to [purchase], in order. */
    val purchaseCalls: MutableList<String> = mutableListOf()

    /** How many times [restore] was called. */
    var restoreCount: Int = 0
        private set

    /** How many times [ownedProductIds] was called. */
    var ownedCount: Int = 0
        private set

    /** Queues [results] for [id]; [purchase] returns them in order before falling back to the default. */
    fun scriptPurchase(id: String, vararg results: PurchaseResult) {
        scripted.getOrPut(id) { ArrayDeque() }.addAll(results)
    }

    override suspend fun products(ids: List<String>): List<Product> {
        productsCalls += ids
        calls += Call.Products(ids)
        return ids.mapNotNull { catalog[it] }
    }

    override suspend fun purchase(id: String): PurchaseResult {
        purchaseCalls += id
        calls += Call.Purchase(id)
        if (id in owned) return PurchaseResult.AlreadyOwned
        val queue = scripted[id]
        val result = if (queue != null && queue.isNotEmpty()) queue.removeFirst() else defaultPurchaseResult
        if (result == PurchaseResult.Purchased || result == PurchaseResult.AlreadyOwned) {
            owned += id // a successful purchase records the entitlement, like the real stores
        }
        return result
    }

    override suspend fun restore(): List<String> {
        restoreCount++
        calls += Call.Restore
        return owned.toList()
    }

    override suspend fun ownedProductIds(): Set<String> {
        ownedCount++
        calls += Call.Owned
        return owned.toSet()
    }
}
