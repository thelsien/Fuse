package com.fuse.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ANL-2 — the core analytics TAXONOMY: that every defined event name + param key exists, the typed
 * call-site helpers emit the right name + params, and NO param value is PII. Call-site INSTRUMENTATION
 * (the stores/ads/screen actually firing these) is asserted in the per-feature tests
 * ([com.fuse.presentation.GameStoreAnalyticsTest], [com.fuse.presentation.DailyStoreAnalyticsTest],
 * [com.fuse.presentation.RemoveAdsStoreAnalyticsTest], [com.fuse.ads.AdManagerAnalyticsTest], and the
 * Robolectric `DailyShareAnalyticsUiTest`).
 */
class AnalyticsTaxonomyTest {

    @Test
    fun everyTaxonomyEventNameIsDefinedAndSnakeCase() {
        val names = listOf(
            AnalyticsEvents.GAME_START,
            AnalyticsEvents.GAME_OVER,
            AnalyticsEvents.DAILY_COMPLETED,
            AnalyticsEvents.SHARE_TAPPED,
            AnalyticsEvents.AD_IMPRESSION,
            AnalyticsEvents.AD_REWARD_GRANTED,
            AnalyticsEvents.IAP_PURCHASE,
            AnalyticsEvents.FTUE_STEP,
        )
        // All present, non-blank, lower_snake_case (Firebase-friendly, no PII in the name).
        names.forEach { name ->
            assertTrue(name.isNotBlank(), "event name must not be blank")
            assertTrue(name.matches(Regex("[a-z][a-z0-9_]*")), "event name '$name' must be snake_case")
        }
        // Stable wire values (the backend keys off these).
        assertEquals("game_start", AnalyticsEvents.GAME_START)
        assertEquals("game_over", AnalyticsEvents.GAME_OVER)
        assertEquals("daily_completed", AnalyticsEvents.DAILY_COMPLETED)
        assertEquals("share_tapped", AnalyticsEvents.SHARE_TAPPED)
        assertEquals("ad_impression", AnalyticsEvents.AD_IMPRESSION)
        assertEquals("ad_reward_granted", AnalyticsEvents.AD_REWARD_GRANTED)
        assertEquals("iap_purchase", AnalyticsEvents.IAP_PURCHASE)
    }

    @Test
    fun ftueStepEventIsDefinedButInstrumentationIsDeferred() {
        // ANL-2 acceptance: the ftue_step constant EXISTS (taxonomy) even though there is no FTUE
        // flow yet (Sprint 10). Its helper exists too, so the FTUE flow can call it unchanged.
        assertEquals("ftue_step", AnalyticsEvents.FTUE_STEP)
        val fake = FakeAnalyticsLogger()
        fake.logFtueStep(stepIndex = 3)
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.FTUE_STEP, event.name)
        assertEquals(3, event.params[AnalyticsParams.STEP_INDEX])
    }

    @Test
    fun logGameStartEmitsClassicMode() {
        val fake = FakeAnalyticsLogger()
        fake.logGameStart()
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.GAME_START, event.name)
        assertEquals(AnalyticsValues.MODE_CLASSIC, event.params[AnalyticsParams.MODE])
        assertNoPii(event)
    }

    @Test
    fun logGameOverCarriesScoreBestTileAndMoves() {
        val fake = FakeAnalyticsLogger()
        fake.logGameOver(score = 1234, bestTile = 256, moves = 88)
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.GAME_OVER, event.name)
        assertEquals(AnalyticsValues.MODE_CLASSIC, event.params[AnalyticsParams.MODE])
        assertEquals(1234, event.params[AnalyticsParams.SCORE])
        assertEquals(256, event.params[AnalyticsParams.BEST_TILE])
        assertEquals(88, event.params[AnalyticsParams.MOVES])
        assertNoPii(event)
    }

    @Test
    fun logDailyCompletedCarriesDayMovesAndPar() {
        val fake = FakeAnalyticsLogger()
        fake.logDailyCompleted(dayNumber = 42L, moves = 7, par = 5)
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.DAILY_COMPLETED, event.name)
        assertEquals(AnalyticsValues.MODE_DAILY, event.params[AnalyticsParams.MODE])
        assertEquals(42L, event.params[AnalyticsParams.DAY_NUMBER])
        assertEquals(7, event.params[AnalyticsParams.MOVES])
        assertEquals(5, event.params[AnalyticsParams.PAR])
        assertNoPii(event)
    }

    @Test
    fun logShareTappedDefaultsToDailySurface() {
        val fake = FakeAnalyticsLogger()
        fake.logShareTapped()
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.SHARE_TAPPED, event.name)
        assertEquals(AnalyticsValues.SURFACE_DAILY, event.params[AnalyticsParams.SURFACE])
        assertNoPii(event)
    }

    @Test
    fun logAdImpressionCarriesFormatAndPlacement() {
        val fake = FakeAnalyticsLogger()
        fake.logAdImpression(
            format = AnalyticsValues.FORMAT_INTERSTITIAL,
            placement = AnalyticsValues.PLACEMENT_GAME_OVER,
        )
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.AD_IMPRESSION, event.name)
        assertEquals(AnalyticsValues.FORMAT_INTERSTITIAL, event.params[AnalyticsParams.FORMAT])
        assertEquals(AnalyticsValues.PLACEMENT_GAME_OVER, event.params[AnalyticsParams.PLACEMENT])
        assertNoPii(event)
    }

    @Test
    fun logAdRewardGrantedCarriesRewardedFormatAndPlacement() {
        val fake = FakeAnalyticsLogger()
        fake.logAdRewardGranted(placement = AnalyticsValues.PLACEMENT_STREAK_SAVER)
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.AD_REWARD_GRANTED, event.name)
        assertEquals(AnalyticsValues.FORMAT_REWARDED, event.params[AnalyticsParams.FORMAT])
        assertEquals(AnalyticsValues.PLACEMENT_STREAK_SAVER, event.params[AnalyticsParams.PLACEMENT])
        assertNoPii(event)
    }

    @Test
    fun logIapPurchaseDefaultsToRemoveAds() {
        val fake = FakeAnalyticsLogger()
        fake.logIapPurchase()
        val event = fake.loggedEvents.single()
        assertEquals(AnalyticsEvents.IAP_PURCHASE, event.name)
        assertEquals(AnalyticsValues.PRODUCT_REMOVE_ADS, event.params[AnalyticsParams.PRODUCT_ID])
        assertNoPii(event)
    }

    @Test
    fun noTaxonomyParamValueLooksLikePii() {
        // Every helper's params must survive sanitizeParams untouched (no PII-keyed entry) AND carry
        // only enum/count/boolean values — never a String that could be free text/an identifier.
        val fake = FakeAnalyticsLogger()
        fake.logGameStart()
        fake.logGameOver(score = 10, bestTile = 64, moves = 20)
        fake.logDailyCompleted(dayNumber = 1L, moves = 3, par = 2)
        fake.logShareTapped()
        fake.logAdImpression(AnalyticsValues.FORMAT_REWARDED, AnalyticsValues.PLACEMENT_REVIVE)
        fake.logAdRewardGranted(AnalyticsValues.PLACEMENT_REVIVE)
        fake.logIapPurchase()
        fake.loggedEvents.forEach { assertNoPii(it) }
    }

    /** Asserts an event's params carry no PII-keyed entry and only enum/count/boolean values. */
    private fun assertNoPii(event: FakeAnalyticsLogger.Event) {
        // The defensive backstop drops nothing — no key looks like an identifier.
        assertEquals(event.params, sanitizeParams(event.params), "no param key should look like PII")
        // The real guarantee: values are ints/longs/booleans or short enum-like value-constants.
        val allowedValueConstants = setOf(
            AnalyticsValues.MODE_CLASSIC, AnalyticsValues.MODE_DAILY, AnalyticsValues.SURFACE_DAILY,
            AnalyticsValues.FORMAT_REWARDED, AnalyticsValues.FORMAT_INTERSTITIAL,
            AnalyticsValues.PLACEMENT_REVIVE, AnalyticsValues.PLACEMENT_STREAK_SAVER,
            AnalyticsValues.PLACEMENT_GAME_OVER, AnalyticsValues.PRODUCT_REMOVE_ADS,
        )
        event.params.values.forEach { value ->
            val ok = when (value) {
                is Int, is Long, is Boolean -> true
                is String -> value in allowedValueConstants
                else -> false
            }
            assertTrue(ok, "param value '$value' is not an enum/count/boolean (possible PII)")
        }
    }
}
