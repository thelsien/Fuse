package com.fuse.ads

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
