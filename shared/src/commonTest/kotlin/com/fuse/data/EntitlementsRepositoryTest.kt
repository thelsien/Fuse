package com.fuse.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * IAP-2 — round-trip tests for [SettingsEntitlementsRepository] over an in-memory [MapSettings], so
 * they run on JVM + iOS Native with no real SharedPreferences/NSUserDefaults.
 *
 * Covers: a fresh install reads `false`; a saved entitlement persists across a "relaunch" (a fresh
 * repo over the same store); the NoOp default is always `false`.
 */
class EntitlementsRepositoryTest {

    private fun repo(settings: MapSettings = MapSettings()): SettingsEntitlementsRepository =
        SettingsEntitlementsRepository(settings)

    @Test
    fun freshInstallIsNotOwned() {
        assertFalse(repo().loadRemoveAdsOwned(), "no entitlement persisted yet → false")
    }

    @Test
    fun savedEntitlementRoundTrips() {
        val r = repo()
        r.saveRemoveAdsOwned(true)
        assertTrue(r.loadRemoveAdsOwned())
    }

    @Test
    fun entitlementPersistsAcrossARelaunch() {
        val settings = MapSettings()
        repo(settings).saveRemoveAdsOwned(true)
        // A brand-new repository over the SAME settings (simulates a relaunch) sees it -> the
        // entitlement survives relaunch.
        assertTrue(repo(settings).loadRemoveAdsOwned(), "entitlement survives relaunch")
    }

    @Test
    fun savingFalseClearsTheEntitlement() {
        val settings = MapSettings()
        repo(settings).saveRemoveAdsOwned(true)
        repo(settings).saveRemoveAdsOwned(false)
        assertFalse(repo(settings).loadRemoveAdsOwned())
    }

    @Test
    fun noOpRepositoryIsAlwaysFalse() {
        assertFalse(NoOpEntitlementsRepository.loadRemoveAdsOwned())
        NoOpEntitlementsRepository.saveRemoveAdsOwned(true) // discarded
        assertFalse(NoOpEntitlementsRepository.loadRemoveAdsOwned(), "NoOp never persists")
    }
}
