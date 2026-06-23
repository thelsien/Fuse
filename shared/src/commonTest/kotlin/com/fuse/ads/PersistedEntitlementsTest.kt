package com.fuse.ads

import com.fuse.data.SettingsEntitlementsRepository
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * IAP-2 — tests for [PersistedEntitlements]: it seeds [removeAdsOwned] from the
 * [SettingsEntitlementsRepository] on construction (so a granted entitlement survives a "relaunch"),
 * and [grantRemoveAds] flips + persists + is idempotent. MapSettings so it runs on JVM + iOS Native.
 */
class PersistedEntitlementsTest {

    private fun entitlements(settings: MapSettings = MapSettings()): PersistedEntitlements =
        PersistedEntitlements(repository = SettingsEntitlementsRepository(settings))

    @Test
    fun freshInstallIsNotOwned() {
        assertFalse(entitlements().removeAdsOwned, "no prior grant → not owned")
    }

    @Test
    fun seedsTrueFromRepoSoEntitlementSurvivesRelaunch() {
        val settings = MapSettings()
        // First "session": grant + persist.
        entitlements(settings).grantRemoveAds()
        // "Relaunch": a fresh PersistedEntitlements over the same store seeds true from the repo.
        val reloaded = entitlements(settings)
        assertTrue(reloaded.removeAdsOwned, "entitlement seeded from repo survives relaunch")
        assertTrue(reloaded.removeAdsOwnedFlow.value, "flow reflects the seeded value")
    }

    @Test
    fun grantFlipsAndPersists() {
        val settings = MapSettings()
        val e = entitlements(settings)
        assertFalse(e.removeAdsOwned)
        e.grantRemoveAds()
        assertTrue(e.removeAdsOwned, "grant flips the gate")
        assertTrue(e.removeAdsOwnedFlow.value, "grant flips the reactive flow")
        // Persisted: the repo over the same store now reads true.
        assertTrue(SettingsEntitlementsRepository(settings).loadRemoveAdsOwned(), "grant persisted")
    }

    @Test
    fun grantIsIdempotent() {
        val settings = MapSettings()
        val e = entitlements(settings)
        e.grantRemoveAds()
        e.grantRemoveAds() // no-op on an already-owned entitlement
        assertTrue(e.removeAdsOwned)
        assertTrue(SettingsEntitlementsRepository(settings).loadRemoveAdsOwned())
    }
}
