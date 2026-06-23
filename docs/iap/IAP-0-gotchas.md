# IAP-0 — Billing spike: integration gotchas (Sprint 9, IAP & analytics)

Goal of this spike: retire the **native-billing** risk before IAP-1..4 by proving a sandbox purchase
round-trips through a minimal `BillingProvider` `expect`/`actual` seam — **iOS via StoreKit 2**
(verified locally with a committed `.storekit` config, no App Store Connect), **Android via the Play
Billing Library** (builds + connects + queries; the real sandbox round-trip awaits a Play Console) —
and documenting every gotcha so IAP-1..4 are mechanical.

**Decisions (locked with product):** native billing, NOT RevenueCat. Single product: a one-time
**non-consumable** "Remove Ads", id **`remove_ads`**, **$3.99** placeholder (tunable before release).

**Status:**
- **Shared seam + Fake + tests:** DONE, green on JVM and iOS (`BillingProviderTest` +
  `FakeBillingProviderTest`, 13 tests).
- **Android (Play Billing):** CODE-COMPLETE; builds + the client connects + the product-query / launch
  path runs. A real sandbox purchase needs a Play Console (see "Android — real purchases").
- **iOS (StoreKit 2):** CODE-COMPLETE and BUILD-verified (`xcodebuild build` succeeds with
  `Config.storekit` bundled and referenced by the committed scheme — **no manual Xcode step needed
  for the StoreKit config**, unlike ADS-0's SwiftPM step). The live local purchase round-trip is
  observed by **running from the Xcode scheme** on a simulator (Xcode's debug run is what injects the
  StoreKit configuration at runtime); see "iOS — observing the local purchase".

**No secrets:** native billing authenticates via the **signed app** (App Store / Play), so unlike
AdMob there are NO ids or keys to inject — the `remove_ads` product id is public, and the `.storekit`
file is safe to commit. Nothing account-specific is in the repo.

---

## The seam (shared) — `com.fuse.iap`

- `commonMain` — `BillingProvider.kt`:
  - `interface BillingProvider { suspend fun products(ids); suspend fun purchase(id); suspend fun
    restore(); suspend fun ownedProductIds() }`
  - `data class Product(id, title, price)` — `price` is the **store-localized** string (we never
    format prices ourselves; IAP-4 renders it verbatim).
  - `enum PurchaseResult { Purchased, Cancelled, Pending, AlreadyOwned, Failed }` — an enum (not a
    sealed class) so it crosses the Kotlin/Native bridge cleanly, mirroring `AdResult`.
  - `object Iap { PRODUCT_REMOVE_ADS = "remove_ads"; ALL_PRODUCT_IDS }` — the single place the id lives.
  - `object NoOpBillingProvider` (safe default) + `expect val platformBillingModule: Module`.
  - `FakeBillingProvider.kt` (in commonMain, like `FakeAdProvider`): scripted purchase results +
    catalog + ownership + call recording, for IAP-1..4 tests/previews with no store SDK on the classpath.
  - `IapDebug.enabled` — the spike feature flag.
- `platformBillingModule` is registered in `com/fuse/di/Modules.kt`'s `appModules` (right after
  `platformAdsModule`).
- Trigger: a debug-only "Buy Remove Ads (spike)" row in `SettingsScreen` (stateful wrapper), shown
  only when `IapDebug.enabled`. It calls `products()` (shows the localized price) then `purchase()`
  and surfaces the `PurchaseResult`. NOT wired to entitlements (IAP-2) or a paywall (IAP-4).

---

## Android — Play Billing Library

- **SDK:** `com.android.billingclient:billing:9.1.0` (version catalog: `play-billing`), added to
  **`:shared` androidMain** (the Android actual lives there, mirroring `play-services-ads`). Builds
  against compileSdk 35 / AGP 8.9.3 with no compileSdk bump; `assembleDebug` / `lintDebug` stay green.
- **API shape (9.x — note the drift from 6.x docs you'll find online):**
  - Build with `enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
    — in 8/9 `enablePendingPurchases()` REQUIRES a `PendingPurchasesParams` (the no-arg overload is gone).
  - `queryProductDetailsAsync(params) { billingResult, queryResult -> ... }` — in 8/9 the callback's
    second arg is a **`QueryProductDetailsResult`** (`.productDetailsList`), NOT a bare
    `List<ProductDetails>` as in 7.x.
  - Price string: `productDetails.oneTimePurchaseOfferDetails?.formattedPrice` for a one-time INAPP.
  - `setProductType(BillingClient.ProductType.INAPP)` for both the query and `queryPurchasesAsync`.
- **Activity requirement:** `launchBillingFlow` needs an `Activity`, but Koin only provides the
  application `Context`. We reuse ADS-0's `AdActivityHolder` (weak ref, set/cleared by `MainActivity`)
  to get the foreground Activity — no new holder.
- **Async purchase result:** Play delivers the purchase outcome via the `PurchasesUpdatedListener`,
  NOT from `launchBillingFlow`'s return value. The actual bridges this with a `CompletableDeferred`
  the listener completes (`OK`→ acknowledge + `Purchased` / `PENDING`→`Pending`; `USER_CANCELED`→
  `Cancelled`; `ITEM_ALREADY_OWNED`→`AlreadyOwned`; else `Failed`).
- **Acknowledge or lose the sale:** a `PURCHASED`, not-yet-acknowledged purchase is auto-refunded by
  Play after 3 days. The actual calls `acknowledgePurchase` on the token (best-effort). For a
  non-consumable we never `consumePurchase` (that would let it be re-bought). IAP-2 will persist the
  entitlement; IAP-3 will surface `ownedProductIds()` for restore.
- **Defensive:** a failed connection, an unconfigured/unpublished product (so `queryProductDetails`
  returns nothing), a missing Activity, or any SDK error surfaces as an empty list /
  `PurchaseResult.Failed` — never a crash.

### Android — real purchases (PENDING the user's Play Console)
The spike compiles + connects + runs the query path, but Play returns NO product details for an
unpublished id, so an end-to-end purchase can't be observed until the user sets up:
1. A Play Console app + upload a signed build to an **internal testing** track (Play only serves
   billing to a recognized, uploaded application id).
2. Create an in-app product **`remove_ads`** (one-time / non-consumable), price ~$3.99, **active**.
3. Add the tester's Google account as a **license tester** (License testing) so purchases are free
   sandbox transactions.
4. Install the build from the internal-testing link on a device signed into that account.
Then the debug trigger's `purchase("remove_ads")` round-trips for real. No code change needed.

---

## iOS — StoreKit 2 (LOCAL testing, no App Store Connect)

- **No SDK to add:** StoreKit is a **system framework** — `import StoreKit` in Swift auto-links it.
  There is NO SwiftPM package and NO Frameworks-phase entry (contrast ADS-0's GoogleMobileAds SPM
  step). So the iOS billing path is much lighter than the ads path.
- **Inverted seam (same pattern as ADS-0's ads):** `BillingProvider.ios.kt` does NOT touch StoreKit.
  It exposes the Obj-C protocol `IosBillingBridge` + the `IosBilling` registration point; the **Swift**
  `iosApp/BillingBridge.swift` implements it with StoreKit 2 and registers itself at launch
  (`iOSApp.init()` → `BillingBridge.register()`). Until a bridge is registered (e.g. in commonTest),
  the Kotlin provider behaves like `NoOpBillingProvider` — so the framework compiles and tests pass
  with zero third-party iOS deps. (Why a Swift bridge and not Kotlin/Native cinterop: StoreKit 2's
  `Product`/`Transaction` are Swift value types built on `async`/`await` — not cleanly callable from
  Kotlin/Native, and fragile across Xcode versions.)
- **Swift API used:** `Product.products(for:)`, `product.displayName` / `product.displayPrice`
  (localized), `product.purchase()` → `.success(.verified(txn))` ⇒ `await txn.finish()` + "Purchased"
  / `.success(.unverified)` ⇒ "Failed" (JWS check failed — never grant) / `.pending` ⇒ "Pending" /
  `.userCancelled` ⇒ "Cancelled"; ownership via `Transaction.currentEntitlements` (skip
  `revocationDate != nil`); `restore()` does `try? await AppStore.sync()` then re-reads entitlements.
  Guarded `@available(iOS 15.0, *)` (the project's deployment target is iOS 15).
- **Local StoreKit config (committed):** `iosApp/Config.storekit` defines the `remove_ads`
  non-consumable at displayPrice "3.99". It is added to the project as a file reference + the app's
  Resources build phase, AND wired into the **committed shared scheme**
  (`iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`) via:
  ```xml
  <LaunchAction ...>
     <StoreKitConfigurationFileReference identifier = "../Config.storekit"></StoreKitConfigurationFileReference>
     ...
  ```
  The `identifier` is relative to the `.xcodeproj` container; `Config.storekit` sits beside it in
  `iosApp/`, so `../Config.storekit` resolves correctly. **This was done by hand-editing the scheme +
  pbxproj and verified by a clean `xcodebuild build` — no manual Xcode GUI step is required for the
  StoreKit config** (the pbxproj uses simple sequential IDs and StoreKit needs no SwiftPM, so unlike
  ADS-0 there's nothing the hand-edit can break).

### iOS — observing the local purchase
The StoreKit **local** configuration is injected into the running process by Xcode's **debug run of
the scheme** (it's a launch-time option, not something baked into the app binary). So to watch the
round-trip:
1. Open `iosApp/iosApp.xcodeproj` in Xcode, pick an iOS-17/18 simulator (e.g. iPhone 17e).
2. Run (▶). The scheme already points at `Config.storekit`, so StoreKit serves it locally.
3. Home ▸ Settings ▸ "Buy Remove Ads (spike)". The row shows "Remove Ads price: $3.99" (proving
   `products()` loaded from the local config) and StoreKit's local purchase sheet appears; confirming
   it yields **Result: Purchased** (and a second tap yields **AlreadyOwned**, since the non-consumable
   is now in `currentEntitlements`).
- A plain `xcrun simctl launch` (CLI, no Xcode debugger) does **not** inject the StoreKit config, so
  `products()` returns empty there — that's expected; use the scheme run to observe it.
- This is the SAME GUI boundary ADS-0 hit (a device/GUI step to *watch* the result); the code, the
  `.storekit`, the scheme, and the build are all complete and committed.

### ATT / privacy (NOT needed for IAP)
In-app purchases do not require App Tracking Transparency or SKAdNetwork (those are ad-tracking
concerns, tracked separately for the ads release). No Info.plist change is needed for IAP.

---

## CI implications

- **Android job (ubuntu):** picks up `com.android.billingclient:billing` from Google's Maven (already
  a configured repo). No workflow change. `assembleDebug` / `lintDebug` / unit tests stay green.
- **iOS job (macos):** `:shared` framework link + `:shared:iosSimulatorArm64Test` are UNAFFECTED (the
  Kotlin framework has no StoreKit dependency by design — the bridge is Swift). The `xcodebuild build`
  step compiles `BillingBridge.swift` against the system StoreKit framework (no package to resolve)
  and bundles `Config.storekit`. No new CI flag required.

---

## Notes for IAP-1..4

- **IAP-1 (load product + localized price into a paywall view-model):** call
  `billingProvider.products(Iap.ALL_PRODUCT_IDS)`, find `PRODUCT_REMOVE_ADS`, expose its `title` +
  `price` (render `price` verbatim). Drive the VM test with `FakeBillingProvider` (pre-set `catalog`).
- **IAP-2 (entitlement + ad gating):** persist ownership (multiplatform-settings, own key e.g.
  `fuse.iap.removeAds`) seeded from `billingProvider.ownedProductIds()` at launch and flipped true on
  a `PurchaseResult.Purchased`/`AlreadyOwned` of `remove_ads`. Replace the `NoOpEntitlements` Koin
  binding in `presentationModule` with a real `Entitlements` whose `removeAdsOwned` reflects this —
  ADS-4's `InterstitialController` already gates interstitials on it; **rewarded (ADS-2/3) stays
  ungated**. Drive with `FakeBillingProvider`.
- **IAP-3 (restore):** call `billingProvider.restore()` (already implemented — Android
  `queryPurchasesAsync`; iOS `AppStore.sync()` + `currentEntitlements`), update the same persisted
  entitlement, add the "Restore Purchases" UX.
- **IAP-4 (paywall UI):** a real screen replacing the debug Settings row (`IapDebug` removable);
  reads the IAP-1 VM (title/price), calls `purchase`, handles each `PurchaseResult` (Cancelled =
  dismiss; Pending = "we'll let you know"; Failed = retry; Purchased/AlreadyOwned = entitled).
