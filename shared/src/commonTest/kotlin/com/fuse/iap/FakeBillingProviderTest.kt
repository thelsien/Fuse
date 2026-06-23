package com.fuse.iap

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * IAP-0 — the contract the FAKE must honour so IAP-1..4 can drive billing deterministically:
 * [FakeBillingProvider.products] returns the configured catalog (localized price), [purchase] returns
 * the scripted results in order (incl. Cancelled / AlreadyOwned / Failed) then a per-default,
 * ownership reflects a successful purchase (and pre-seeding), and every call is recorded. No store
 * SDK on the classpath — runs on JVM + iOS.
 */
class FakeBillingProviderTest {

    @Test
    fun productsReturnsConfiguredCatalogWithLocalizedPrice() = runTest {
        val billing = FakeBillingProvider()
        val products = billing.products(Iap.ALL_PRODUCT_IDS)

        assertEquals(1, products.size)
        val removeAds = products.single()
        assertEquals(Iap.PRODUCT_REMOVE_ADS, removeAds.id)
        assertEquals("Remove Ads", removeAds.title)
        assertEquals("$3.99", removeAds.price, "the localized price string is returned verbatim")
    }

    @Test
    fun productsOnlyReturnsKnownIds() = runTest {
        val billing = FakeBillingProvider()
        assertTrue(billing.products(listOf("unknown_product")).isEmpty())
    }

    @Test
    fun purchaseReturnsScriptedResultsInOrderThenDefault() = runTest {
        val billing = FakeBillingProvider(defaultPurchaseResult = PurchaseResult.Cancelled)
        billing.scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Failed, PurchaseResult.Pending)

        assertEquals(PurchaseResult.Failed, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        assertEquals(PurchaseResult.Pending, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        // Queue drained → the configured default.
        assertEquals(PurchaseResult.Cancelled, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
    }

    @Test
    fun aSuccessfulPurchaseRecordsTheEntitlement() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        assertFalse(Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds(), "not owned before purchase")

        assertEquals(PurchaseResult.Purchased, billing.purchase(Iap.PRODUCT_REMOVE_ADS))

        assertTrue(Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds(), "owned after Purchased")
        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), billing.restore(), "restore reflects the entitlement")
    }

    @Test
    fun aFailedOrCancelledPurchaseDoesNotRecordOwnership() = runTest {
        val billing = FakeBillingProvider(defaultPurchaseResult = PurchaseResult.Failed)
        assertEquals(PurchaseResult.Failed, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        assertTrue(billing.ownedProductIds().isEmpty(), "a failed purchase grants nothing")

        billing.scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Cancelled)
        assertEquals(PurchaseResult.Cancelled, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        assertTrue(billing.ownedProductIds().isEmpty(), "a cancelled purchase grants nothing")
    }

    @Test
    fun purchasingAnAlreadyOwnedProductReportsAlreadyOwned() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        // Even though Purchased is scripted, the pre-existing ownership short-circuits to AlreadyOwned.
        billing.scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        assertEquals(PurchaseResult.AlreadyOwned, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
    }

    @Test
    fun alreadyOwnedResultAlsoRecordsTheEntitlement() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.AlreadyOwned)
        }
        assertEquals(PurchaseResult.AlreadyOwned, billing.purchase(Iap.PRODUCT_REMOVE_ADS))
        assertTrue(Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds())
    }

    @Test
    fun preSeededOwnershipIsReportedByRestoreAndOwnedProductIds() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        assertEquals(setOf(Iap.PRODUCT_REMOVE_ADS), billing.ownedProductIds())
        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), billing.restore())
    }

    @Test
    fun recordsEveryCall() = runTest {
        val billing = FakeBillingProvider()
        billing.products(Iap.ALL_PRODUCT_IDS)
        billing.purchase(Iap.PRODUCT_REMOVE_ADS)
        billing.ownedProductIds()
        billing.restore()

        assertEquals(listOf(Iap.ALL_PRODUCT_IDS), billing.productsCalls)
        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), billing.purchaseCalls)
        assertEquals(1, billing.ownedCount)
        assertEquals(1, billing.restoreCount)
        assertEquals(
            listOf(
                FakeBillingProvider.Call.Products(Iap.ALL_PRODUCT_IDS),
                FakeBillingProvider.Call.Purchase(Iap.PRODUCT_REMOVE_ADS),
                FakeBillingProvider.Call.Owned,
                FakeBillingProvider.Call.Restore,
            ),
            billing.calls,
        )
    }
}
