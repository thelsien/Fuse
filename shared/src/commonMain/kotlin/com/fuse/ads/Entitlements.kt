package com.fuse.ads

import com.fuse.data.EntitlementsRepository
import com.fuse.data.NoOpEntitlementsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ADS-4 (Sprint 8) — the Remove-Ads ENTITLEMENT seam (a HOOK, not an implementation).
 *
 * This is the single check the Classic game-over interstitial ([InterstitialPolicy]) is gated on:
 * when [removeAdsOwned] is `true`, NO interstitial is ever shown. Rewarded ads (ADS-2 revive, ADS-3
 * streak-saver) deliberately do NOT consult this — Remove-Ads suppresses only the cadence
 * interstitial; opt-in rewarded video stays available to everyone.
 *
 * ## Always-false until Sprint 9
 * No purchase flow exists yet, so the bound implementation is [NoOpEntitlements], whose
 * [removeAdsOwned] is permanently `false` (interstitials behave as if the player has not bought
 * Remove-Ads). **IAP-2 (Sprint 9)** introduces the real purchase and replaces this binding (or backs
 * it with persisted purchase state) so that completing the Remove-Ads purchase flips
 * [removeAdsOwned] to `true` — and from that moment interstitials are suppressed automatically, with
 * NO change to the rewarded paths. ADS-4 only provides + reads the hook.
 */
interface Entitlements {
    /**
     * Whether the player owns the Remove-Ads entitlement. `false` until IAP-2 ships a purchase that
     * flips it `true`. The ONLY entitlement gate on the interstitial placement.
     */
    val removeAdsOwned: Boolean
}

/**
 * ADS-4 — the default, no-purchase [Entitlements]: [removeAdsOwned] is always `false`.
 *
 * The Koin-bound implementation until IAP-2 (Sprint 9) wires real purchase state. Also the safe
 * default for tests/previews that don't exercise the entitled branch.
 */
object NoOpEntitlements : Entitlements {
    override val removeAdsOwned: Boolean = false
}

/**
 * ADS-4 — a fixed-value [Entitlements] for tests: returns [removeAdsOwned] verbatim. Lets a unit /
 * UI test exercise the entitled branch (suppression) without an IAP implementation, and lets a
 * controller test assert the hook is actually consulted.
 */
class FakeEntitlements(override val removeAdsOwned: Boolean) : Entitlements

/**
 * IAP-2 (Sprint 9) — the REAL, persisted [Entitlements] that replaces [NoOpEntitlements] in Koin.
 *
 * It backs the single [removeAdsOwned] gate the interstitial reads with a persisted boolean cache
 * ([EntitlementsRepository], key `fuse.entitlement.removeAds`):
 *  - **Seeded on construction** from the repo, so the entitlement SURVIVES RELAUNCH and is available
 *    INSTANTLY and OFFLINE (no store round-trip needed for the gate). A returning owner's
 *    interstitials stay suppressed from the first frame.
 *  - **[grantRemoveAds]** flips the cached flag to `true` AND persists it (write-through). It is
 *    IDEMPOTENT (granting twice is a no-op on an already-owned entitlement and does not re-emit). This
 *    is the single grant path called by (a) the seed-on-launch reconcile with the store
 *    ([com.fuse.iap.BillingProvider.ownedProductIds] in `initKoin`), (b) a successful purchase
 *    ([com.fuse.presentation.RemoveAdsStore] observing `owned`), and (c) IAP-3's restore (reusing
 *    this same method) — so all three converge on one persisted source of truth.
 *
 * The value lives in a [MutableStateFlow] so the paywall (IAP-4) / UI can REACT to ownership via
 * [removeAdsOwnedFlow]; the synchronous [removeAdsOwned] bool the ad sites read is just its current
 * value, so [InterstitialController] / [InterstitialPolicy] are completely UNCHANGED. The entitlement
 * never gates rewarded ads (ADS-2 revive / ADS-3 streak-saver) — those code paths never consult
 * [Entitlements] at all.
 *
 * Once granted it stays granted for the process; we do not auto-revoke (a refund is rare and the
 * store-reconcile on the NEXT launch is the authoritative correction).
 *
 * `single` in Koin (one shared, persisted entitlement). Default repo is [NoOpEntitlementsRepository]
 * for previews/tests that don't supply persistence (then it behaves like the always-false NoOp).
 */
class PersistedEntitlements(
    private val repository: EntitlementsRepository = NoOpEntitlementsRepository,
) : Entitlements {

    private val _removeAdsOwned = MutableStateFlow(repository.loadRemoveAdsOwned())

    /** Reactive view of ownership for the paywall (IAP-4) / UI. */
    val removeAdsOwnedFlow: StateFlow<Boolean> = _removeAdsOwned.asStateFlow()

    /** The gate the interstitial reads — the current value of [removeAdsOwnedFlow]. */
    override val removeAdsOwned: Boolean
        get() = _removeAdsOwned.value

    /**
     * Grants the Remove-Ads entitlement: flips [removeAdsOwned] true and PERSISTS it. Idempotent — a
     * no-op (no persist, no re-emit) if already owned. The single grant path shared by purchase,
     * seed-on-launch reconcile, and IAP-3 restore.
     */
    fun grantRemoveAds() {
        if (_removeAdsOwned.value) return
        _removeAdsOwned.value = true
        repository.saveRemoveAdsOwned(true)
    }
}
