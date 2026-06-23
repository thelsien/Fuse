package com.fuse.presentation

import com.fuse.analytics.AnalyticsEvents
import com.fuse.analytics.AnalyticsParams
import com.fuse.analytics.AnalyticsValues
import com.fuse.analytics.FakeAnalyticsLogger
import com.fuse.iap.FakeBillingProvider
import com.fuse.iap.Iap
import com.fuse.iap.PurchaseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ANL-2 — instrumentation of [RemoveAdsStore]: `iap_purchase` (product_id=remove_ads) fires on a
 * successful (Purchased) purchase — NOT on AlreadyOwned, a refresh that finds prior ownership, or a
 * restore. The only param is the product id; no PII (no order/account id).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoveAdsStoreAnalyticsTest {

    private fun TestScope.store(
        billing: FakeBillingProvider,
        analytics: FakeAnalyticsLogger,
    ): RemoveAdsStore = RemoveAdsStore(
        billing = billing,
        scope = this,
        analytics = analytics,
    )

    @Test
    fun successfulPurchaseLogsIapPurchaseWithProductId() = runTest {
        val analytics = FakeAnalyticsLogger()
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        val store = store(billing, analytics)
        advanceUntilIdle()
        assertTrue(analytics.loggedEvents.isEmpty(), "refresh logs no purchase")

        store.purchase()
        advanceUntilIdle()

        val event = analytics.loggedEvents.single()
        assertEquals(AnalyticsEvents.IAP_PURCHASE, event.name)
        assertEquals(AnalyticsValues.PRODUCT_REMOVE_ADS, event.params[AnalyticsParams.PRODUCT_ID])
    }

    @Test
    fun alreadyOwnedPurchaseLogsNoNewSale() = runTest {
        val analytics = FakeAnalyticsLogger()
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.AlreadyOwned)
        }
        val store = store(billing, analytics)
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        assertTrue(
            analytics.loggedEvents.none { it.name == AnalyticsEvents.IAP_PURCHASE },
            "AlreadyOwned is not a new purchase conversion",
        )
    }

    @Test
    fun cancelledPurchaseLogsNothing() = runTest {
        val analytics = FakeAnalyticsLogger()
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Cancelled)
        }
        val store = store(billing, analytics)
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        assertTrue(analytics.loggedEvents.isEmpty(), "a cancelled purchase logs nothing")
    }

    @Test
    fun refreshThatFindsPriorOwnershipLogsNoPurchase() = runTest {
        val analytics = FakeAnalyticsLogger()
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        store(billing, analytics)
        advanceUntilIdle()

        assertTrue(
            analytics.loggedEvents.none { it.name == AnalyticsEvents.IAP_PURCHASE },
            "discovering prior ownership on refresh is not a purchase event",
        )
    }
}
