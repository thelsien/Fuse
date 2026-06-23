package com.fuse.iap

import org.koin.core.module.Module

/**
 * IAP-0 (Sprint 9 spike) — the GENERALIZED cross-platform in-app-purchase seam.
 *
 * This spike retires the native-billing risk before IAP-1..4 by proving a **sandbox purchase
 * round-trips** through a minimal `expect`/`actual` provider — iOS via **StoreKit 2** (verified
 * locally with a committed `.storekit` configuration, no App Store Connect needed) and Android via
 * the **Play Billing Library** (builds + connects + queries; the real sandbox round-trip awaits a
 * Play Console). The native SDKs stay hidden behind this boundary, exactly as Sprint 8's
 * [com.fuse.ads.AdProvider] hides the ad SDKs, so the follow-on stories (IAP-1..4) are built and
 * unit-tested against [FakeBillingProvider] with no store SDK on the classpath.
 *
 * ## Surface (tiny — just enough to prove the round-trip + support IAP-1..4 later)
 *  - [products] — fetch the localized [Product]s for the given store ids (title + price string).
 *    IAP-1 feeds these into the paywall view-model.
 *  - [purchase] — start the native purchase flow for one id and suspend until it resolves to a
 *    [PurchaseResult] (`Purchased`/`Cancelled`/`Pending`/`AlreadyOwned`/`Failed`).
 *  - [restore] — re-sync and return the owned product ids (minimal for the spike; IAP-3 fleshes out
 *    the restore UX). On both stores a restore is "ask the store what this account already owns".
 *  - [ownedProductIds] — the currently-entitled product ids, read from the store's current
 *    entitlements (StoreKit `Transaction.currentEntitlements` / Play `queryPurchases`). IAP-2 maps
 *    `PRODUCT_REMOVE_ADS in ownedProductIds()` to `Entitlements.removeAdsOwned`.
 *
 * Provide [NoOpBillingProvider] (the safe default) + a scriptable [FakeBillingProvider] for tests.
 * `expect val platformBillingModule: Module` is registered in `com.fuse.di.Modules.appModules`.
 *
 * All implementations are DEFENSIVE: a disconnected client, an unconfigured store, or any SDK error
 * surfaces as an empty list / [PurchaseResult.Failed], never a crash — billing is never a hard
 * dependency of the game.
 */
interface BillingProvider {
    /**
     * Fetches the store [Product]s for [ids] (localized title + price string). Suspends until the
     * store responds; returns an empty list on any failure or when the store is not configured.
     * Never throws.
     */
    suspend fun products(ids: List<String>): List<Product>

    /**
     * Starts the native purchase flow for [id] and suspends until it resolves. Returns
     * [PurchaseResult.Purchased] on a verified, acknowledged purchase; [PurchaseResult.Cancelled]
     * when the user backs out; [PurchaseResult.AlreadyOwned] for an already-entitled non-consumable;
     * [PurchaseResult.Pending] for a deferred purchase (e.g. Ask-to-Buy); [PurchaseResult.Failed]
     * for any error. Never throws.
     */
    suspend fun purchase(id: String): PurchaseResult

    /**
     * Re-syncs with the store and returns the owned (entitled) product ids. Minimal for the spike;
     * IAP-3 builds the restore UX on top of this. Returns an empty list on failure. Never throws.
     */
    suspend fun restore(): List<String>

    /**
     * The product ids the account currently owns (active entitlements), read from the store without
     * launching any UI. IAP-2 reads this to flip `Entitlements.removeAdsOwned`. Empty on failure.
     * Never throws.
     */
    suspend fun ownedProductIds(): Set<String>
}

/**
 * A purchasable store product, as returned by [BillingProvider.products].
 *
 * @property id the store product id (e.g. [Iap.PRODUCT_REMOVE_ADS]).
 * @property title the store-localized display title.
 * @property price the store-localized, currency-formatted price string (e.g. "$3.99") — IAP-4
 *   renders this verbatim; we never format prices ourselves.
 */
data class Product(
    val id: String,
    val title: String,
    val price: String,
)

/**
 * The coarse outcome of [BillingProvider.purchase]. Kept as an `enum` (not a sealed class) so it
 * crosses the Kotlin/Native bridge cleanly, mirroring [com.fuse.ads.AdResult].
 */
enum class PurchaseResult {
    /** The purchase completed and was verified + acknowledged. The ONLY signal IAP-2 grants on. */
    Purchased,

    /** The user dismissed the purchase sheet without buying. */
    Cancelled,

    /** A deferred purchase (e.g. Ask-to-Buy / SCA) — not yet owned; resolves later out of band. */
    Pending,

    /** The non-consumable is already owned by this account (treat as entitled, like Purchased). */
    AlreadyOwned,

    /** The purchase failed for any other reason (store disconnected, verification failed, error). */
    Failed,
}

/**
 * IAP product ids + spike pricing. The single place the `remove_ads` id lives, so the native actuals,
 * the `.storekit` config, the Play Console product, and IAP-1..4 all agree on one constant.
 *
 * No secrets: native billing authenticates via the signed app (App Store / Play), so unlike AdMob
 * there are NO ids or keys to inject — the product id is public and safe to commit.
 */
object Iap {
    /** The one-time, NON-CONSUMABLE "Remove Ads" product. Placeholder price $3.99 (tunable at release). */
    const val PRODUCT_REMOVE_ADS: String = "remove_ads"

    /** Every product id the app knows about (IAP-1 queries this set). */
    val ALL_PRODUCT_IDS: List<String> = listOf(PRODUCT_REMOVE_ADS)
}

/**
 * A no-op [BillingProvider] — the safe default for previews and any build where billing is disabled.
 * Reports no products, no ownership, and [PurchaseResult.Failed] for every purchase, so callers/tests
 * are fully deterministic with no store SDK on the classpath. (For SCRIPTED outcomes use
 * [FakeBillingProvider].)
 */
object NoOpBillingProvider : BillingProvider {
    override suspend fun products(ids: List<String>): List<Product> = emptyList()
    override suspend fun purchase(id: String): PurchaseResult = PurchaseResult.Failed
    override suspend fun restore(): List<String> = emptyList()
    override suspend fun ownedProductIds(): Set<String> = emptySet()
}

/**
 * Per-platform Koin module binding the [BillingProvider] `single`, mirroring [com.fuse.ads.platformAdsModule].
 *
 *  - **Android** (`BillingProvider.android.kt`): Play Billing Library (`com.android.billingclient:billing`).
 *    Connects the `BillingClient`, queries `remove_ads`, launches the flow from the foreground
 *    Activity ([com.fuse.ads.AdActivityHolder]), acknowledges purchases. Defensive — no crash when
 *    not configured.
 *  - **iOS** (`BillingProvider.ios.kt`): inverts the seam to a Swift `BillingBridge` (StoreKit 2),
 *    which loads `Product`s, runs `product.purchase()`, finishes transactions, and reads
 *    `Transaction.currentEntitlements`.
 *
 * Tests inject [FakeBillingProvider] (or [NoOpBillingProvider]) instead. Registered in `appModules`.
 */
expect val platformBillingModule: Module
