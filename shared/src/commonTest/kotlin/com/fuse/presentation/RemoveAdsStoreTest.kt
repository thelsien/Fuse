package com.fuse.presentation

import com.fuse.iap.FakeBillingProvider
import com.fuse.iap.Iap
import com.fuse.iap.NoOpBillingProvider
import com.fuse.iap.Product
import com.fuse.iap.PurchaseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IAP-1 — unit tests for [RemoveAdsStore] over the deterministic [FakeBillingProvider] (no real
 * store), so the suite runs on JVM + iOS. The store launches its suspend provider work onto the
 * injected [TestScope], so each test drives it with [advanceUntilIdle].
 *
 * Covers the acceptance criteria + the design contract:
 *  - the configured product loads with its localized price; `owned` reflects `ownedProductIds()`;
 *  - `purchase()` → scripted `Purchased` marks `owned = true` and surfaces success;
 *  - scripted `Cancelled` / `Failed` → not owned + graceful outcome (no crash);
 *  - pre-owned / `AlreadyOwned` → `owned = true`;
 *  - a double `purchase()` is guarded to a single provider call;
 *  - the NoOp provider leaves `product == null` / `owned == false` (paywall "unavailable").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoveAdsStoreTest {

    private fun TestScope.store(
        billing: com.fuse.iap.BillingProvider,
        refreshOnInit: Boolean = true,
    ): RemoveAdsStore = RemoveAdsStore(billing = billing, scope = this, refreshOnInit = refreshOnInit)

    // --- product load -----------------------------------------------------------

    @Test
    fun refreshLoadsTheConfiguredProductWithItsLocalizedPrice() = runTest {
        val billing = FakeBillingProvider(
            catalog = mutableMapOf(
                Iap.PRODUCT_REMOVE_ADS to Product(Iap.PRODUCT_REMOVE_ADS, "Remove Ads", "€4,49"),
            ),
        )
        val store = store(billing)
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(Iap.PRODUCT_REMOVE_ADS, state.product?.id)
        // The localized price is surfaced VERBATIM (not reformatted).
        assertEquals("€4,49", state.product?.price)
        assertEquals("€4,49", state.price)
        assertFalse(state.loading)
        assertFalse(state.owned)
        assertTrue(state.canPurchase)
        // It queried exactly the remove_ads id.
        assertEquals(listOf(listOf(Iap.PRODUCT_REMOVE_ADS)), billing.productsCalls)
    }

    @Test
    fun ownedReflectsOwnedProductIdsOnRefresh() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        val store = store(billing)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.owned)
        // Already owned ⇒ no purchase CTA.
        assertFalse(state.canPurchase)
    }

    @Test
    fun noProductFromNoOpProviderLeavesUnavailable() = runTest {
        val store = store(NoOpBillingProvider)
        advanceUntilIdle()

        val state = store.state.value
        assertNull(state.product)
        assertNull(state.price)
        assertFalse(state.owned)
        assertFalse(state.loading)
        assertFalse(state.canPurchase)
    }

    // --- purchase flow ----------------------------------------------------------

    @Test
    fun purchasePurchasedMarksOwnedAndSurfacesSuccess() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        val store = store(billing)
        advanceUntilIdle()
        assertFalse(store.state.value.owned)

        store.purchase()
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.owned)
        assertEquals(PurchaseResult.Purchased, state.lastResult)
        assertFalse(state.purchaseInProgress)
        assertFalse(state.canPurchase) // owned now
        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), billing.purchaseCalls)
    }

    @Test
    fun purchaseEmitsOneShotOutcome() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        val store = store(billing)
        advanceUntilIdle()

        var captured: PurchaseResult? = null
        // Collect on an UnconfinedTestDispatcher so the subscriber registers EAGERLY (before the
        // non-replaying emission); the store itself still runs on the test scheduler.
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            captured = store.outcomes.first()
        }

        store.purchase()
        advanceUntilIdle()
        job.cancel()

        assertEquals(PurchaseResult.Purchased, captured)
    }

    @Test
    fun purchaseCancelledLeavesNotOwnedAndSurfacesOutcomeGracefully() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Cancelled)
        }
        val store = store(billing)
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.owned)
        assertEquals(PurchaseResult.Cancelled, state.lastResult)
        assertFalse(state.purchaseInProgress)
        // Still purchasable (product exists, not owned).
        assertTrue(state.canPurchase)
    }

    @Test
    fun purchaseFailedLeavesNotOwnedAndSurfacesOutcomeGracefully() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Failed)
        }
        val store = store(billing)
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.owned)
        assertEquals(PurchaseResult.Failed, state.lastResult)
        assertFalse(state.purchaseInProgress)
    }

    @Test
    fun purchasePendingLeavesNotOwned() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Pending)
        }
        val store = store(billing)
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.owned)
        assertEquals(PurchaseResult.Pending, state.lastResult)
    }

    @Test
    fun purchaseAlreadyOwnedMarksOwned() = runTest {
        // Pre-seed ownership so the fake returns AlreadyOwned for the purchase call.
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        // Don't refresh on init so we exercise the AlreadyOwned PATH through purchase() itself.
        val store = store(billing, refreshOnInit = false)

        store.purchase()
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.owned)
        assertEquals(PurchaseResult.AlreadyOwned, state.lastResult)
    }

    @Test
    fun doublePurchaseIsGuardedToASingleProviderCall() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        val store = store(billing)
        advanceUntilIdle()

        // Two synchronous taps before the first launched purchase resolves: the second is guarded
        // out by purchaseInProgress, so only ONE provider.purchase() happens.
        store.purchase()
        store.purchase()
        advanceUntilIdle()

        assertEquals(listOf(Iap.PRODUCT_REMOVE_ADS), billing.purchaseCalls)
        assertTrue(store.state.value.owned)
    }
}
