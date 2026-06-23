package com.fuse.presentation

import com.fuse.ads.PersistedEntitlements
import com.fuse.data.SettingsEntitlementsRepository
import com.fuse.iap.BillingProvider
import com.fuse.iap.FakeBillingProvider
import com.fuse.iap.Iap
import com.fuse.iap.NoOpBillingProvider
import com.fuse.iap.Product
import com.fuse.iap.PurchaseResult
import com.russhwolf.settings.MapSettings
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

    // --- IAP-2: onOwned grant wiring -------------------------------------------

    private fun TestScope.store(
        billing: com.fuse.iap.BillingProvider,
        onOwned: () -> Unit,
        refreshOnInit: Boolean = true,
    ): RemoveAdsStore = RemoveAdsStore(
        billing = billing,
        scope = this,
        onOwned = onOwned,
        refreshOnInit = refreshOnInit,
    )

    @Test
    fun purchasedFiresOnOwnedOnceForTheEntitlementGrant() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        var grants = 0
        val store = store(billing, onOwned = { grants++ })
        advanceUntilIdle()
        assertEquals(0, grants, "no grant before purchase")

        store.purchase()
        advanceUntilIdle()

        assertTrue(store.state.value.owned)
        assertEquals(1, grants, "a successful purchase grants exactly once (false → true)")
    }

    @Test
    fun alreadyOwnedPurchaseFiresOnOwned() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        var grants = 0
        // refreshOnInit=false so the grant comes from the purchase() AlreadyOwned path, not refresh.
        val store = store(billing, onOwned = { grants++ }, refreshOnInit = false)

        store.purchase()
        advanceUntilIdle()

        assertEquals(PurchaseResult.AlreadyOwned, store.state.value.lastResult)
        assertEquals(1, grants, "AlreadyOwned also grants the entitlement")
    }

    @Test
    fun refreshThatDiscoversOwnershipFiresOnOwned() = runTest {
        // A returning owner: the store is owned per the provider on the very first refresh.
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        var grants = 0
        val store = store(billing, onOwned = { grants++ })
        advanceUntilIdle()

        assertTrue(store.state.value.owned)
        assertEquals(1, grants, "a refresh that finds ownership grants once")
    }

    @Test
    fun cancelledPurchaseDoesNotFireOnOwned() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Cancelled)
        }
        var grants = 0
        val store = store(billing, onOwned = { grants++ })
        advanceUntilIdle()

        store.purchase()
        advanceUntilIdle()

        assertFalse(store.state.value.owned)
        assertEquals(0, grants, "a cancelled purchase never grants")
    }

    @Test
    fun onOwnedFiresOnlyOnceAcrossRefreshThenPurchase() = runTest {
        // Already owned, refreshOnInit=true: refresh grants once; a later (redundant) purchase
        // observes owned was already true and does NOT re-grant.
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        var grants = 0
        val store = store(billing, onOwned = { grants++ })
        advanceUntilIdle()
        assertEquals(1, grants)

        store.purchase()
        advanceUntilIdle()
        assertEquals(1, grants, "ownership already observed → no second grant")
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

    // --- IAP-3: restore ---------------------------------------------------------

    /** A [BillingProvider] whose [restore] throws — to exercise the graceful Failed path. */
    private class ThrowingRestoreProvider : BillingProvider {
        override suspend fun products(ids: List<String>): List<Product> = emptyList()
        override suspend fun purchase(id: String): PurchaseResult = PurchaseResult.Failed
        override suspend fun restore(): List<String> = throw IllegalStateException("boom")
        override suspend fun ownedProductIds(): Set<String> = emptySet()
    }

    @Test
    fun restoreThatFindsOwnershipMarksOwnedAndSurfacesRestored() = runTest {
        // The store reports remove_ads owned (e.g. bought on another device / before this install).
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        // refreshOnInit=false so we isolate the restore() path (refresh would also discover ownership).
        val store = store(billing, refreshOnInit = false)

        var outcome: RestoreResult? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            outcome = store.restoreOutcomes.first()
        }

        store.restore()
        advanceUntilIdle()
        job.cancel()

        assertTrue(store.state.value.owned, "restore re-marks ownership")
        assertFalse(store.state.value.restoreInProgress)
        assertEquals(RestoreResult.Restored, outcome)
        assertEquals(1, billing.restoreCount, "restore asked the store exactly once")
    }

    @Test
    fun restoreWithNothingOwnedSurfacesNothingToRestoreAndStaysUnowned() = runTest {
        val billing = FakeBillingProvider() // store owns nothing
        val store = store(billing, refreshOnInit = false)

        var outcome: RestoreResult? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            outcome = store.restoreOutcomes.first()
        }

        store.restore()
        advanceUntilIdle()
        job.cancel()

        assertFalse(store.state.value.owned, "nothing to restore leaves un-owned")
        assertEquals(RestoreResult.NothingToRestore, outcome)
    }

    @Test
    fun restoreErrorSurfacesFailedGracefullyAndDoesNotGrant() = runTest {
        var grants = 0
        val store = store(ThrowingRestoreProvider(), onOwned = { grants++ }, refreshOnInit = false)

        var outcome: RestoreResult? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            outcome = store.restoreOutcomes.first()
        }

        store.restore() // must not crash
        advanceUntilIdle()
        job.cancel()

        assertEquals(RestoreResult.Failed, outcome)
        assertFalse(store.state.value.owned)
        assertFalse(store.state.value.restoreInProgress)
        assertEquals(0, grants, "a failed restore grants nothing")
    }

    @Test
    fun restoreFiresOnOwnedOnceForTheEntitlementGrant() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        var grants = 0
        val store = store(billing, onOwned = { grants++ }, refreshOnInit = false)

        store.restore()
        advanceUntilIdle()

        assertTrue(store.state.value.owned)
        assertEquals(1, grants, "restore re-grants via the SAME onOwned path (false → true)")
    }

    @Test
    fun restoreDoesNotReGrantWhenAlreadyOwned() = runTest {
        // Already owned via an init refresh; a later restore observes owned was already true.
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        var grants = 0
        val store = store(billing, onOwned = { grants++ }) // refreshOnInit=true → grants once
        advanceUntilIdle()
        assertEquals(1, grants)

        store.restore()
        advanceUntilIdle()
        assertEquals(1, grants, "ownership already observed → restore does not re-grant")
    }

    @Test
    fun doubleRestoreIsGuardedToASingleProviderCall() = runTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        val store = store(billing, refreshOnInit = false)

        // Two synchronous taps: the second is guarded out by restoreInProgress.
        store.restore()
        store.restore()
        advanceUntilIdle()

        assertEquals(1, billing.restoreCount, "concurrent restore guarded to one provider call")
    }

    @Test
    fun restoreIsGuardedWhileAPurchaseIsInFlight() = runTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        val store = store(billing, refreshOnInit = false)

        // Start a purchase, then attempt restore before it resolves: restore is a no-op.
        store.purchase()
        store.restore()
        advanceUntilIdle()

        assertEquals(0, billing.restoreCount, "restore is suppressed while a purchase is in flight")
    }

    @Test
    fun restoreReGrantsFromFreshInstallEntitlementCacheAndPersists() = runTest {
        // FRESH-INSTALL re-grant proof: the local entitlement cache starts FALSE (fresh install) while
        // the STORE still reports remove_ads owned. After restore() the entitlement is true AND
        // persisted — survives a "relaunch" (a new PersistedEntitlements over the same Settings).
        val settings = MapSettings()
        val entitlements = PersistedEntitlements(SettingsEntitlementsRepository(settings))
        assertFalse(entitlements.removeAdsOwned, "fresh install: local entitlement cache is false")

        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        val store = store(billing, onOwned = entitlements::grantRemoveAds, refreshOnInit = false)

        store.restore()
        advanceUntilIdle()

        assertTrue(entitlements.removeAdsOwned, "restore re-granted the entitlement from the store")
        // PERSISTED: a fresh entitlement over the same Settings ("relaunch") reads true.
        val reloaded = PersistedEntitlements(SettingsEntitlementsRepository(settings))
        assertTrue(reloaded.removeAdsOwned, "the re-granted entitlement survives relaunch")
        assertTrue(
            SettingsEntitlementsRepository(settings).loadRemoveAdsOwned(),
            "restore persisted the entitlement",
        )
    }
}
