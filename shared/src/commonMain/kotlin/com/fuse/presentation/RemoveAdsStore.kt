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
 * ## IAP-3 — restore
 * [restore] is the store-review-mandated "Restore Purchases" action (the button lives in the IAP-4
 * paywall). It asks the store what the signed-in account already owns ([BillingProvider.restore]) and,
 * if `remove_ads` is among them, re-grants the entitlement via the SAME ownership/grant path purchase
 * uses (`owned` false → true → [onOwned] → `grantRemoveAds`), so a FRESH INSTALL whose local
 * entitlement cache is `false` is re-entitled from the store with no purchase. Its outcome is a
 * [RestoreResult] emitted once on [restoreOutcomes] (separate from purchase [outcomes] so IAP-4 can
 * label "Purchases restored" / "Nothing to restore" / "Restore failed" distinctly).
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
 * ## IAP-2 — flip the persisted entitlement on ownership
 * IAP-2 wires the [onOwned] callback so that whenever this store observes ownership — a refresh that
 * finds `remove_ads` already owned, OR a [purchase] resolving to [PurchaseResult.Purchased] /
 * [PurchaseResult.AlreadyOwned] — it invokes [onOwned] EXACTLY when `owned` transitions
 * `false → true`. The Koin singleton passes `entitlements::grantRemoveAds` (idempotent persist +
 * flip), so a purchase suppresses interstitials and survives relaunch with NO circular dependency:
 * the store depends on the entitlement's grant lambda, the entitlement knows nothing about the store.
 * (IAP-3's restore reuses the same `grantRemoveAds` path.) Default is a no-op so previews/tests
 * construct without an entitlement.
 *
 * @param billing the billing seam; defaults to [NoOpBillingProvider] so previews/tests construct
 *   without a real store. The Koin singleton injects the platform provider.
 * @param scope the coroutine scope the suspend provider work runs on.
 * @param onOwned invoked once whenever ownership is first observed (`owned` `false → true`), via
 *   refresh or a successful purchase. The Koin singleton passes the persisted entitlement's grant.
 * @param refreshOnInit whether to load the product + ownership at construction (default `true`).
 */
class RemoveAdsStore(
    private val billing: BillingProvider = NoOpBillingProvider,
    private val scope: CoroutineScope,
    private val onOwned: () -> Unit = {},
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

    private val _restoreOutcomes = MutableSharedFlow<RestoreResult>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** IAP-3: one-shot restore outcomes (consume once). Hot, non-replaying. */
    val restoreOutcomes: Flow<RestoreResult> = _restoreOutcomes.asSharedFlow()

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
            val wasOwned = _state.value.owned
            _state.value = _state.value.copy(
                product = product,
                owned = owned,
                loading = false,
            )
            // IAP-2: a refresh that discovers ownership (e.g. a returning owner) grants the
            // persisted entitlement once, on the false → true transition.
            if (owned && !wasOwned) onOwned()
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
            val wasOwned = _state.value.owned
            val nowOwned = wasOwned ||
                result == PurchaseResult.Purchased ||
                result == PurchaseResult.AlreadyOwned
            _state.value = _state.value.copy(
                owned = nowOwned,
                purchaseInProgress = false,
                lastResult = result,
            )
            _outcomes.tryEmit(result)
            // IAP-2: a successful purchase (Purchased / AlreadyOwned) grants the persisted
            // entitlement once, on the false → true transition — suppressing interstitials and
            // surviving relaunch. Rewarded ads are never gated by this.
            if (nowOwned && !wasOwned) onOwned()
        }
    }

    /**
     * IAP-3 — the store-review-mandated **Restore Purchases** action (the button lives in IAP-4).
     * Asks the store what this account already owns ([BillingProvider.restore]) off the UI via
     * [scope]. GUARDED: a no-op when a restore OR a purchase is already in flight, so a double-tap
     * makes a single provider call. Outcomes (emitted once on [restoreOutcomes]):
     *  - `remove_ads` owned per the store → sets `owned = true`, which fires the SAME [onOwned]
     *    ([com.fuse.ads.PersistedEntitlements.grantRemoveAds]) purchase uses on the `false → true`
     *    transition (persist + suppress interstitials, surviving relaunch). This is the FRESH-INSTALL
     *    re-grant: the local entitlement cache may be `false`, the store still reports ownership, and
     *    restore re-entitles. Emits [RestoreResult.Restored].
     *  - store reports it un-owned → `owned` left unchanged, emits [RestoreResult.NothingToRestore].
     * The provider never throws (empty list on any failure), so a store/network error surfaces as
     * [RestoreResult.NothingToRestore]; an unexpected error is caught and emits [RestoreResult.Failed].
     * Never throws.
     */
    fun restore() {
        if (_state.value.restoreInProgress || _state.value.purchaseInProgress) return
        _state.value = _state.value.copy(restoreInProgress = true)
        scope.launch {
            val result = try {
                val owned = Iap.PRODUCT_REMOVE_ADS in billing.restore()
                val wasOwned = _state.value.owned
                val nowOwned = wasOwned || owned
                _state.value = _state.value.copy(owned = nowOwned)
                // Reuse the purchase grant path: grant once on the false → true transition.
                if (nowOwned && !wasOwned) onOwned()
                if (owned) RestoreResult.Restored else RestoreResult.NothingToRestore
            } catch (e: Throwable) {
                RestoreResult.Failed
            }
            _state.value = _state.value.copy(restoreInProgress = false)
            _restoreOutcomes.tryEmit(result)
        }
    }
}

/**
 * IAP-3 — the coarse outcome of [RemoveAdsStore.restore], emitted once on
 * [RemoveAdsStore.restoreOutcomes] so the paywall (IAP-4) can show a fire-once snackbar. Kept as an
 * `enum` so it crosses the Kotlin/Native bridge cleanly, mirroring [PurchaseResult].
 */
enum class RestoreResult {
    /** The store reported `remove_ads` owned; the entitlement was (re-)granted. */
    Restored,

    /** The store reported nothing to restore (or a graceful store/network failure). */
    NothingToRestore,

    /** An unexpected error occurred while restoring. Graceful — the entitlement is untouched. */
    Failed,
}

/**
 * IAP-1 — the immutable Remove-Ads UI projection.
 *
 * @property product the loaded `remove_ads` [Product] (localized `price`) or `null` when the store
 *   reports no product (NoOp / unconfigured) — IAP-4 then shows "unavailable".
 * @property owned whether `remove_ads` is currently owned (in-memory; IAP-2 persists this).
 * @property loading a product/ownership [RemoveAdsStore.refresh] is in flight.
 * @property purchaseInProgress a [RemoveAdsStore.purchase] is in flight (disable the CTA).
 * @property restoreInProgress a [RemoveAdsStore.restore] is in flight (disable the Restore button).
 * @property lastResult the most recent purchase outcome, or `null` before any attempt (sticky label).
 */
data class RemoveAdsUiState(
    val product: Product? = null,
    val owned: Boolean = false,
    val loading: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val restoreInProgress: Boolean = false,
    val lastResult: PurchaseResult? = null,
) {
    /**
     * Whether the purchase CTA can be offered: a product exists, it isn't already owned, and no
     * purchase is in flight. IAP-4 reads this to enable/disable the buy button.
     */
    val canPurchase: Boolean
        get() = product != null && !owned && !purchaseInProgress && !restoreInProgress

    /**
     * Whether the Restore button can be offered (IAP-3): not already owned and no
     * purchase/restore in flight. Available even when `product == null` so a returning owner on a
     * fresh install (where the store may report no live product) can still restore.
     */
    val canRestore: Boolean
        get() = !owned && !purchaseInProgress && !restoreInProgress

    /** The localized price to render, or `null` when no product is available. Never reformatted. */
    val price: String?
        get() = product?.price
}
