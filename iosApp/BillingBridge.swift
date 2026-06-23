import Foundation
import StoreKit
import Shared

/// IAP-0 (iOS) — the Swift side of the cross-platform BillingProvider seam, backed by **StoreKit 2**.
///
/// The Kotlin `:shared` framework defines `IosBillingBridge` (an Obj-C protocol) and the process-wide
/// `IosBilling` registration point (see `BillingProvider.ios.kt`). This class implements the bridge
/// using StoreKit 2's async API — which only Swift can call cleanly — and registers itself at launch
/// so the Kotlin `IosBillingProvider` resolved from Koin delegates here. This keeps the shared Kotlin
/// framework compiling with ZERO third-party iOS deps (StoreKit is a system framework: just
/// `import StoreKit`, no SwiftPM, no Frameworks-phase entry — Swift auto-links it).
///
/// **Local testing (no App Store Connect):** the committed `Config.storekit` configuration defines
/// the `remove_ads` non-consumable at $3.99; the iosApp scheme's StoreKit Configuration points at it,
/// so on the simulator `Product.products(for:)` returns it and `product.purchase()` round-trips
/// through StoreKit's local test sheet. See `docs/iap/IAP-0-gotchas.md`.
///
/// Result strings ("Purchased"/"Cancelled"/"Pending"/"AlreadyOwned"/"Failed") map to the Kotlin
/// `PurchaseResult`. Behind the spike's debug trigger — not a real paywall (IAP-4) or entitlement
/// gate (IAP-2).
@available(iOS 15.0, *)
final class BillingBridge: NSObject, IosBillingBridge {

    /// Call once at app launch (from `iOSApp.init()`), before the first Compose view controller.
    static func register() {
        IosBilling.shared.register(bridge: BillingBridge())
    }

    func products(ids: [String], onResult: @escaping ([String], [String], [String]) -> Void) {
        Task {
            do {
                let storeProducts = try await Product.products(for: ids)
                var outIds: [String] = []
                var titles: [String] = []
                var prices: [String] = []
                for product in storeProducts {
                    outIds.append(product.id)
                    titles.append(product.displayName)
                    prices.append(product.displayPrice) // store-localized, currency-formatted
                }
                onResult(outIds, titles, prices)
            } catch {
                onResult([], [], [])
            }
        }
    }

    func purchase(id: String, onResult: @escaping (String) -> Void) {
        Task {
            do {
                let storeProducts = try await Product.products(for: [id])
                guard let product = storeProducts.first else {
                    onResult("Failed")
                    return
                }
                // Short-circuit: a non-consumable already in current entitlements is AlreadyOwned.
                if await Self.isOwned(id: id) {
                    onResult("AlreadyOwned")
                    return
                }
                let result = try await product.purchase()
                switch result {
                case .success(let verification):
                    switch verification {
                    case .verified(let transaction):
                        // Finish the transaction so StoreKit stops re-delivering it.
                        await transaction.finish()
                        onResult("Purchased")
                    case .unverified:
                        // Signature/JWS check failed — do not grant.
                        onResult("Failed")
                    }
                case .pending:
                    // Deferred (e.g. Ask-to-Buy / SCA) — resolves later out of band.
                    onResult("Pending")
                case .userCancelled:
                    onResult("Cancelled")
                @unknown default:
                    onResult("Failed")
                }
            } catch {
                onResult("Failed")
            }
        }
    }

    func restore(onResult: @escaping ([String]) -> Void) {
        Task {
            // StoreKit 2: a "restore" is syncing then re-reading current entitlements. We sync
            // best-effort (ignore errors — currentEntitlements is the source of truth) then report.
            try? await AppStore.sync()
            onResult(await Self.ownedIds())
        }
    }

    func ownedProductIds(onResult: @escaping ([String]) -> Void) {
        Task {
            onResult(await Self.ownedIds())
        }
    }

    /// The product ids the account currently owns, from `Transaction.currentEntitlements`.
    private static func ownedIds() async -> [String] {
        var owned: [String] = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result {
                // Non-consumables / non-revoked entitlements only.
                if transaction.revocationDate == nil {
                    owned.append(transaction.productID)
                }
            }
        }
        return owned
    }

    private static func isOwned(id: String) async -> Bool {
        await ownedIds().contains(id)
    }
}
