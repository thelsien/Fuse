package com.fuse.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ANL-1 — the contract for the generic analytics seam: the fake RECORDS what call sites log (so
 * ANL-2 can assert game_start/game_over/… at their sites), the no-op DROPS everything, user
 * properties are captured, and the no-PII [sanitizeParams] backstop strips identifier-keyed params.
 *
 * The platform debug logger ([DebugAnalyticsLogger] + `analyticsDebugPrint`) is thin and verified by
 * BUILD (it prints to logcat / NSLog) — not asserted here.
 */
class AnalyticsLoggerTest {

    @Test
    fun fakeRecordsEventNameAndParams() {
        val analytics = FakeAnalyticsLogger()

        analytics.logEvent("game_start", mapOf("mode" to "classic", "tile" to 2))

        val event = analytics.loggedEvents.single()
        assertEquals("game_start", event.name)
        assertEquals("classic", event.params["mode"])
        assertEquals(2, event.params["tile"])
    }

    @Test
    fun fakeRecordsEventsInOrder() {
        val analytics = FakeAnalyticsLogger()

        analytics.logEvent("game_start")
        analytics.logEvent("game_over")
        analytics.logEvent("share_tapped")

        assertEquals(listOf("game_start", "game_over", "share_tapped"), analytics.loggedEventNames)
    }

    @Test
    fun fakeDefaultsParamsToEmpty() {
        val analytics = FakeAnalyticsLogger()

        analytics.logEvent("daily_completed")

        assertTrue(analytics.loggedEvents.single().params.isEmpty())
    }

    @Test
    fun fakeSnapshotsParamsDefensively() {
        val analytics = FakeAnalyticsLogger()
        val params = mutableMapOf<String, Any?>("count" to 1)

        analytics.logEvent("game_over", params)
        params["count"] = 999 // mutating the caller's map must NOT change the recorded event

        assertEquals(1, analytics.loggedEvents.single().params["count"])
    }

    @Test
    fun fakeCapturesUserProperties() {
        val analytics = FakeAnalyticsLogger()

        analytics.setUserProperty("equipped_theme", "midnight")
        analytics.setUserProperty("equipped_theme", null)

        assertEquals(
            listOf("equipped_theme" to "midnight", "equipped_theme" to null),
            analytics.userProperties,
        )
    }

    @Test
    fun fakeResetClearsRecordedState() {
        val analytics = FakeAnalyticsLogger()
        analytics.logEvent("game_start")
        analytics.setUserProperty("equipped_theme", "ocean")

        analytics.reset()

        assertTrue(analytics.loggedEvents.isEmpty())
        assertTrue(analytics.userProperties.isEmpty())
    }

    @Test
    fun noOpDropsEventsAndUserProperties() {
        // The NoOp logger must not throw and must hold no state — exercising it is enough.
        NoOpAnalyticsLogger.logEvent("game_over", mapOf("score" to 1024))
        NoOpAnalyticsLogger.setUserProperty("equipped_theme", "sunset")
        // No observable state to assert; the contract is "does nothing, never throws".
        assertTrue(true)
    }

    @Test
    fun sanitizeStripsPiiKeyedParams() {
        val raw = mapOf(
            "mode" to "classic",
            "score" to 2048,
            "email" to "a@b.com",
            "deviceId" to "abc-123",
            "user_id" to "u-42",
            "player_name" to "Ada",
            "idfa" to "00000",
        )

        val clean = sanitizeParams(raw)

        assertEquals(setOf("mode", "score"), clean.keys)
        assertFalse("email" in clean)
        assertFalse("deviceId" in clean)
        assertFalse("user_id" in clean)
        assertFalse("player_name" in clean) // matched by the "name" fragment
        assertNull(clean["idfa"])
    }

    @Test
    fun sanitizeKeepsCleanParamsUnchanged() {
        val raw = mapOf("mode" to "daily", "moves" to 7, "won" to true)
        assertEquals(raw, sanitizeParams(raw))
    }
}
