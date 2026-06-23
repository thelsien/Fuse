package com.fuse.data

import com.russhwolf.settings.Settings

/**
 * IAP-2 (Sprint 9) — local persistence for the **Remove-Ads entitlement** flag.
 *
 * Mirrors the rest of Fuse's `Settings`-backed persistence ([SettingsAdsRepository] /
 * [SettingsCosmeticsRepository]): the SAME platform [Settings] (via Koin) under its OWN key
 * ([SettingsEntitlementsRepository.KEY_REMOVE_ADS] = `fuse.entitlement.removeAds`), distinct from
 * every other Fuse slot. This is the cache that gives the ad call sites an INSTANT, offline check of
 * whether the player owns Remove-Ads — the store ([com.fuse.iap.BillingProvider.ownedProductIds]) is
 * the authoritative source reconciled into it at app start (see `initKoin`).
 *
 * Only a single boolean is persisted; there is no purchase receipt here (the store owns that). A
 * fresh install reads `false` (no entitlement), which makes interstitials behave normally.
 */
interface EntitlementsRepository {
    /** Loads the persisted Remove-Ads entitlement (`false` if never granted). */
    fun loadRemoveAdsOwned(): Boolean

    /** Persists the Remove-Ads entitlement [owned] flag, overwriting any prior value. */
    fun saveRemoveAdsOwned(owned: Boolean)
}

/**
 * IAP-2 — [EntitlementsRepository] backed by multiplatform-settings [Settings]. A plain boolean
 * (no JSON needed); a missing key reads `false`.
 */
class SettingsEntitlementsRepository(
    private val settings: Settings,
) : EntitlementsRepository {

    override fun loadRemoveAdsOwned(): Boolean = settings.getBoolean(KEY_REMOVE_ADS, false)

    override fun saveRemoveAdsOwned(owned: Boolean) {
        settings.putBoolean(KEY_REMOVE_ADS, owned)
    }

    companion object {
        /** Storage key for the Remove-Ads entitlement flag. Distinct from all other Fuse keys. */
        const val KEY_REMOVE_ADS: String = "fuse.entitlement.removeAds"
    }
}

/**
 * IAP-2 — a no-op, never-persisting [EntitlementsRepository]: the default for tests/previews and any
 * build without a real [Settings]. Always reads `false` (not entitled) and discards writes, so a
 * NoOp wiring leaves interstitials behaving normally.
 */
object NoOpEntitlementsRepository : EntitlementsRepository {
    override fun loadRemoveAdsOwned(): Boolean = false
    override fun saveRemoveAdsOwned(owned: Boolean) = Unit
}
