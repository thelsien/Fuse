package com.fuse.iap

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IAP-0 — the [NoOpBillingProvider] default + [Iap] constants contract.
 *
 * The no-op default must be inert and crash-free with no store SDK: no products, no ownership, and a
 * [PurchaseResult.Failed] purchase — so previews and any "billing disabled" build are deterministic.
 * The single `remove_ads` product id is the seam the native actuals, the `.storekit` config, and
 * IAP-1..4 all share.
 */
class BillingProviderTest {

    @Test
    fun noOpReportsNoProductsNoOwnershipAndFailedPurchase() = runTest {
        val billing: BillingProvider = NoOpBillingProvider
        assertTrue(billing.products(Iap.ALL_PRODUCT_IDS).isEmpty())
        assertEquals(PurchaseResult.Failed, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        assertTrue(billing.restore().isEmpty())
        assertTrue(billing.ownedProductIds().isEmpty())
    }

    @Test
    fun removeAdsProductIdIsTheSpikeConstant() {
        assertEquals("remove_ads", Iap.PRODUCT_REMOVE_ADS)
        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), Iap.ALL_PRODUCT_IDS)
    }
}
