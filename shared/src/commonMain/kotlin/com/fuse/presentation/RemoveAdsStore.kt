package com.fuse.presentation

import com.fuse.iap.BillingProvider
import com.fuse.iap.Iap
import com.fuse.iap.NoOpBillingProvider
import com.fuse.iap.Product
import com.fuse.iap.PurchaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * IAP-1 (Sprint 9) — the presentation store over [BillingProvider] for the single **Remove Ads**
 * non-consumable. It loads the store-configured product (with its STORE-LOCALIZED price) and exposes
 * a purchase flow, so the paywall (IAP-4) can render `state` and trigger [purchase], and IAP-2 can
 * turn `owned` into a persisted entitlement.
 *
 * ## What this story IS (and is NOT)
 * IAP-1 is the product-load + purchase-flow view-model only. It does NOT persist the entitlement or
 * gate ads — that is IAP-2, which will observe `owned` here and flip
 * [com.fuse.ads.Entitlements.removeAdsOwned] (suppressing interstitials; rewarded stays ungated).
 * `owned` here is purely in-memory store state DERIVED from the provider
 * ([BillingProvider.ownedProductIds] on [refresh], plus a successful [purchase]); the seam IAP-2
 * adds is "react to `state.owned == true`" (e.g. a repository write-through + entitlement binding),
 * mirroring how [CosmeticsStore] is seeded from + writes through a repository. No restore UX (IAP-3)
 * and no paywall screen (IAP-4) are built here.
 *
 * ## State + the localized price
 * [state] is a [RemoveAdsUiState]:
 *  - [RemoveAdsUiState.product] — the loaded [Product] (`id`/`title`/localized `price`) or `null`
 *    when the store reports no product (e.g. [NoOpBillingProvider], or an unconfigured store). The
 *    price is the provider's localized string surfaced VERBATIM — we never format it ourselves; the
 *    paywall renders [Product.price] as-is.
 *  - [RemoveAdsUiState.owned] — whether `remove_ads` is currently owned.
 *  - [RemoveAdsUiState.loading] — a product/ownership [refresh] is in flight.
 *  - [RemoveAdsUiState.purchaseInProgress] — a [purchase] is in flight (drives a disabled/spinner CTA
 *    and guards against double-invocation).
 *  - [RemoveAdsUiState.lastResult] — the most recent [PurchaseResult], or `null` before any attempt
 *    (sticky, for a "purchased"/"cancelled" label that survives recomposition).
 *
 * ## One-shot purchase outcome
 * [outcomes] emits each [PurchaseResult] exactly once (a non-replaying hot flow), so IAP-4 can show a
 * fire-once "Purchased!"/"Cancelled"/"Failed" snackbar that never re-shows on recomposition —
 * mirroring [GameEffect]/[DailyEffect].
 *
 * ## Async off the UI (injected scope)
 * The provider calls suspend (network/store round-trip), so the non-suspend [refresh]/[purchase]
 * launch onto an injected [scope] (the Koin singleton passes a long-lived app scope on
 * `Dispatchers.Main`, like [CosmeticsStore]). Tests pass a `TestScope`. Construction kicks off an
 * initial [refresh] when [refreshOnInit] (the default), so the product/ownership is loaded eagerly.
 *
 * Resilient by contract: [BillingProvider] never throws (empty list / [PurchaseResult.Failed] on any
 * failure), so a missing product simply leaves `product == null` / `owned == false` and the paywall
 * shows "unavailable"; a double [purchase] is guarded to a single provider call.
 *
 * @param billing the billing seam; defaults to [NoOpBillingProvider] so previews/tests construct
 *   without a real store. The Koin singleton injects the platform provider.
 * @param scope the coroutine scope the suspend provider work runs on.
 * @param refreshOnInit whether to load the product + ownership at construction (default `true`).
 */
class RemoveAdsStore(
    private val billing: BillingProvider = NoOpBillingProvider,
    private val scope: CoroutineScope,
    refreshOnInit: Boolean = true,
) {
    private val _state = MutableStateFlow(RemoveAdsUiState())

    /** The Remove-Ads UI state the paywall (IAP-4) collects. */
    val state: StateFlow<RemoveAdsUiState> = _state.asStateFlow()

    private val _outcomes = MutableSharedFlow<PurchaseResult>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One-shot purchase outcomes (consume once). Hot, non-replaying. */
    val outcomes: Flow<PurchaseResult> = _outcomes.asSharedFlow()

    init {
        if (refreshOnInit) refresh()
    }

    /**
     * (Re)loads the `remove_ads` [Product] (with its localized price) and current ownership from the
     * [billing] provider, off the UI via [scope]. Sets [RemoveAdsUiState.loading] while in flight.
     * Safe to call repeatedly (e.g. on screen entry / app foreground). Never throws.
     */
    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        scope.launch {
            val product = billing.products(listOf(Iap.PRODUCT_REMOVE_ADS))
                .firstOrNull { it.id == Iap.PRODUCT_REMOVE_ADS }
            val owned = Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds()
            _state.value = _state.value.copy(
                product = product,
                owned = owned,
                loading = false,
            )
        }
    }

    /**
     * Starts the purchase flow for `remove_ads` off the UI via [scope]. GUARDED: a no-op when a
     * purchase is already in flight ([RemoveAdsUiState.purchaseInProgress]) — so a double-tap makes a
     * single [BillingProvider.purchase] call. On [PurchaseResult.Purchased] / [PurchaseResult.AlreadyOwned]
     * sets `owned = true`; on [PurchaseResult.Cancelled] / [PurchaseResult.Failed] / [PurchaseResult.Pending]
     * leaves `owned` unchanged. Always records [RemoveAdsUiState.lastResult] and emits the outcome once
     * on [outcomes]. Never throws.
     */
    fun purchase() {
        if (_state.value.purchaseInProgress) return
        _state.value = _state.value.copy(purchaseInProgress = true)
        scope.launch {
            val result = billing.purchase(Iap.PRODUCT_REMOVE_ADS)
            val nowOwned = _state.value.owned ||
                result == PurchaseResult.Purchased ||
                result == PurchaseResult.AlreadyOwned
            _state.value = _state.value.copy(
                owned = nowOwned,
                purchaseInProgress = false,
                lastResult = result,
            )
            _outcomes.tryEmit(result)
        }
    }
}

/**
 * IAP-1 — the immutable Remove-Ads UI projection.
 *
 * @property product the loaded `remove_ads` [Product] (localized `price`) or `null` when the store
 *   reports no product (NoOp / unconfigured) — IAP-4 then shows "unavailable".
 * @property owned whether `remove_ads` is currently owned (in-memory; IAP-2 persists this).
 * @property loading a product/ownership [RemoveAdsStore.refresh] is in flight.
 * @property purchaseInProgress a [RemoveAdsStore.purchase] is in flight (disable the CTA).
 * @property lastResult the most recent purchase outcome, or `null` before any attempt (sticky label).
 */
data class RemoveAdsUiState(
    val product: Product? = null,
    val owned: Boolean = false,
    val loading: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val lastResult: PurchaseResult? = null,
) {
    /**
     * Whether the purchase CTA can be offered: a product exists, it isn't already owned, and no
     * purchase is in flight. IAP-4 reads this to enable/disable the buy button.
     */
    val canPurchase: Boolean
        get() = product != null && !owned && !purchaseInProgress

    /** The localized price to render, or `null` when no product is available. Never reformatted. */
    val price: String?
        get() = product?.price
}
