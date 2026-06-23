package com.fuse.iap

/**
 * IAP-0 (Sprint 9 spike) — the feature flag that gates the spike's debug billing trigger.
 *
 * The point of the spike is to PROVE a sandbox purchase round-trips through the [BillingProvider]
 * seam without destabilising the app, so the only entry point to the billing code is a debug-only
 * "Buy Remove Ads (spike)" row in Settings, shown ONLY when [enabled] is true. It is wired to NO
 * real entitlement (gating ads is IAP-2) and NO real paywall (that is IAP-4).
 *
 * Default ON for the Sprint-9 branch so the purchase can be triggered + verified locally (iOS via
 * the committed StoreKit configuration). IAP-1+ replaces this with the real paywall; until then a
 * single flag keeps the trigger out of the way and trivially removable, mirroring
 * [com.fuse.ads.AdsDebug].
 */
object IapDebug {
    /** Whether the debug billing trigger is shown in Settings. */
    const val enabled: Boolean = true
}
