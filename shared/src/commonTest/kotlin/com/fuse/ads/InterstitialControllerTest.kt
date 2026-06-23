package com.fuse.ads

import com.fuse.data.SettingsAdsRepository
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADS-4 — tests for [InterstitialController]: it wires the persisted [SettingsAdsRepository] state +
 * first-session marker + the [Entitlements] hook into the pure [InterstitialPolicy], returns the
 * show decision, and PERSISTS the advanced counter. MapSettings so it runs on JVM + iOS Native.
 */
class InterstitialControllerTest {

    /** A repo over a NON-first session (so cadence is not first-session-suppressed) unless asked. */
    private fun settings(launches: Int = 2): MapSettings {
        val s = MapSettings()
        val r = SettingsAdsRepository(s)
        repeat(launches) { r.recordLaunch() }
        return s
    }

    private fun controller(
        settings: MapSettings,
        removeAdsOwned: Boolean = false,
    ): InterstitialController = InterstitialController(
        repository = SettingsAdsRepository(settings),
        entitlements = FakeEntitlements(removeAdsOwned = removeAdsOwned),
    )

    @Test
    fun showsOnEveryThirdReplayAndPersistsCounter() {
        val s = settings()
        val c = controller(s)
        val shown = (1..6).map { c.onReplay() }
        assertEquals(listOf(false, false, true, false, false, true), shown, "every-3rd cadence")
        // The counter was persisted: a fresh repo over the same store reads 6.
        assertEquals(6, SettingsAdsRepository(s).loadInterstitialState().replayCount, "counter persisted")
    }

    @Test
    fun cadenceIsRealAcrossARelaunch() {
        val s = settings()
        // First "session": two replays (no show), counter now 2 and persisted.
        controller(s).onReplay()
        controller(s).onReplay()
        // "Relaunch": a fresh controller over the same store; the 3rd replay shows (cadence continues).
        assertTrue(controller(s).onReplay(), "3rd replay across a relaunch shows -> cap is persistent")
    }

    @Test
    fun firstSessionSuppressesButStillAdvances() {
        // launches = 1 -> isFirstSession true.
        val s = settings(launches = 1)
        val c = controller(s)
        repeat(3) { assertFalse(c.onReplay(), "no interstitial during the first session") }
        assertEquals(3, SettingsAdsRepository(s).loadInterstitialState().replayCount, "counter still advanced")
    }

    @Test
    fun entitlementHookConsulted_removeAdsSuppresses() {
        val s = settings()
        val c = controller(s, removeAdsOwned = true)
        // Even the would-be-show 3rd replay is suppressed -> the hook is consulted.
        repeat(3) { assertFalse(c.onReplay(), "entitled players never see an interstitial") }
        assertEquals(3, SettingsAdsRepository(s).loadInterstitialState().replayCount, "counter still advanced")
    }

    @Test
    fun persistedEntitlementGrantSuppressesInterstitialAcrossRelaunch() {
        // IAP-2 end-to-end: the REAL persisted entitlement, granted (e.g. via a purchase), suppresses
        // the would-be-show 3rd replay — and a fresh entitlement seeded from the same store (a
        // relaunch) stays suppressed.
        val s = settings()
        val entitlementSettings = com.russhwolf.settings.MapSettings()
        val entitlements = PersistedEntitlements(
            repository = com.fuse.data.SettingsEntitlementsRepository(entitlementSettings),
        )
        entitlements.grantRemoveAds()

        val c = InterstitialController(
            repository = SettingsAdsRepository(s),
            entitlements = entitlements,
        )
        repeat(3) { assertFalse(c.onReplay(), "granted entitlement suppresses the interstitial") }

        // "Relaunch": a fresh PersistedEntitlements seeded from the same store is still owned.
        val reloaded = PersistedEntitlements(
            repository = com.fuse.data.SettingsEntitlementsRepository(entitlementSettings),
        )
        assertTrue(reloaded.removeAdsOwned, "entitlement survives relaunch → still suppressed")
    }

    @Test
    fun defaultEntitlementFalseDoesNotSuppress() {
        // Default NoOpEntitlements (removeAdsOwned=false) must NOT suppress the cadence on its own.
        val s = settings()
        val c = InterstitialController(repository = SettingsAdsRepository(s)) // default entitlements
        assertFalse(c.onReplay())
        assertFalse(c.onReplay())
        assertTrue(c.onReplay(), "with default (false) entitlement the 3rd replay still shows")
    }
}
