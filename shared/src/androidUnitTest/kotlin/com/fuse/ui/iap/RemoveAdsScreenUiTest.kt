package com.fuse.ui.iap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.iap.FakeBillingProvider
import com.fuse.iap.Iap
import com.fuse.iap.PurchaseResult
import com.fuse.presentation.RemoveAdsStore
import com.fuse.presentation.RemoveAdsUiState
import com.fuse.ui.theme.FuseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * IAP-4 — headless Compose UI tests for the [RemoveAdsScreen] paywall (androidUnitTest Robolectric,
 * runs in `:shared:testDebugUnitTest`).
 *
 * The stateful overload is driven by a real [RemoveAdsStore] over a scripted [FakeBillingProvider]
 * (no Koin), so the assertions hit the real product-load + purchase/restore flow and the one-shot
 * outcome → message wiring. The store runs on `Dispatchers.Main` (the Compose test main), so
 * `waitForIdle()` settles its launched work.
 *
 * Covers the acceptance criteria:
 *  - the paywall shows the title + the store-localized price + Buy + Restore (always present),
 *  - Buy → `purchase()` → owned (Buy replaced by the owned message) + "Ads removed — thanks!",
 *  - Restore (scripted owned) → "Purchases restored"; (scripted nothing) → "Nothing to restore",
 *  - owned hides Buy / shows the owned acknowledgement,
 *  - product unavailable (no catalog) → "Store unavailable" graceful state, Restore still present.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RemoveAdsScreenUiTest {

    private fun store(billing: FakeBillingProvider): RemoveAdsStore = RemoveAdsStore(
        billing = billing,
        scope = CoroutineScope(Dispatchers.Main),
        // The screen reads `state` which `refresh()` populates; load eagerly on the Compose main.
        refreshOnInit = true,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTitlePriceBuyAndRestore() = runComposeUiTest {
        val billing = FakeBillingProvider() // default catalog: remove_ads "Remove Ads" "$3.99"
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = store(billing)) } }
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.TITLE).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.BENEFIT).assertExists()
        // The localized price is rendered VERBATIM from the product.
        onNodeWithTag(RemoveAdsScreenTags.PRICE).assertTextEquals("$3.99")
        onNodeWithTag(RemoveAdsScreenTags.BUY).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.RESTORE).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buyPurchasesAndShowsOwnedPlusMessage() = runComposeUiTest {
        val billing = FakeBillingProvider().apply {
            scriptPurchase(Iap.PRODUCT_REMOVE_ADS, PurchaseResult.Purchased)
        }
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = store(billing)) } }
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.BUY).performClick()
        waitForIdle()

        // Purchased → owned acknowledgement replaces Buy, and the fire-once message shows.
        onNodeWithTag(RemoveAdsScreenTags.OWNED).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.BUY).assertDoesNotExist()
        onNodeWithTag(RemoveAdsScreenTags.MESSAGE).assertTextEquals("Ads removed — thanks!")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restoreWithOwnedShowsRestored() = runComposeUiTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        // Pre-owned: the store starts owned after refresh, so Restore is disabled and Buy hidden.
        // To exercise the restore action itself, start un-owned but have the store NOT know yet:
        // construct without an initial refresh and let the screen's restore call discover it.
        val freshStore = RemoveAdsStore(
            billing = billing,
            scope = CoroutineScope(Dispatchers.Main),
            refreshOnInit = false,
        )
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = freshStore) } }
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.RESTORE).performClick()
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.MESSAGE).assertTextEquals("Purchases restored")
        onNodeWithTag(RemoveAdsScreenTags.OWNED).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restoreWithNothingShowsNothingToRestore() = runComposeUiTest {
        val billing = FakeBillingProvider() // nothing owned
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = store(billing)) } }
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.RESTORE).performClick()
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.MESSAGE).assertTextEquals("Nothing to restore")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ownedHidesBuyAndShowsOwnedMessage() = runComposeUiTest {
        val billing = FakeBillingProvider().apply { owned += Iap.PRODUCT_REMOVE_ADS }
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = store(billing)) } }
        waitForIdle()

        // refresh() discovers ownership → owned acknowledgement, no Buy.
        onNodeWithTag(RemoveAdsScreenTags.OWNED).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.BUY).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unavailableShowsGracefulStateWithRestore() = runComposeUiTest {
        // Empty catalog → products() returns nothing → product == null → "Store unavailable".
        val billing = FakeBillingProvider(catalog = mutableMapOf())
        setContent { FuseTheme { RemoveAdsScreen(onBack = {}, store = store(billing)) } }
        waitForIdle()

        onNodeWithTag(RemoveAdsScreenTags.UNAVAILABLE).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.PRICE).assertDoesNotExist()
        // Restore stays present even when no live product (a returning owner can re-entitle).
        onNodeWithTag(RemoveAdsScreenTags.RESTORE).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.BUY).assertExists() // not owned → Buy shown (disabled)
    }

    /** The presentational overload renders directly from a value state (no store/Koin). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun presentationalOwnedStateRendersOwnedNoBuy() = runComposeUiTest {
        setContent {
            FuseTheme {
                RemoveAdsScreen(
                    state = RemoveAdsUiState(owned = true),
                    message = null,
                    onBuy = {},
                    onRestore = {},
                    onBack = {},
                )
            }
        }
        onNodeWithTag(RemoveAdsScreenTags.OWNED).assertExists()
        onNodeWithTag(RemoveAdsScreenTags.BUY).assertDoesNotExist()
        onNodeWithTag(RemoveAdsScreenTags.RESTORE).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backAffordanceInvokesOnBack() = runComposeUiTest {
        var backs by mutableStateOf(0)
        setContent {
            FuseTheme {
                RemoveAdsScreen(
                    state = RemoveAdsUiState(),
                    message = null,
                    onBuy = {},
                    onRestore = {},
                    onBack = { backs++ },
                )
            }
        }
        onNodeWithTag(RemoveAdsScreenTags.BACK).performClick()
        assert(backs == 1)
    }
}
