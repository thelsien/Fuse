package com.fuse.analytics

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * ANL-1 — light coverage of the shared [DebugAnalyticsLogger]: it logs without throwing (the actual
 * console write [analyticsDebugPrint] is `Log.d` on Android / `NSLog` on iOS — both safe to call from
 * the unit-test runners; Android unit tests use `isReturnDefaultValues=true`), and it rejects a blank
 * event name. The console OUTPUT itself is verified by build/run, not asserted here.
 */
class DebugAnalyticsLoggerTest {

    @Test
    fun logsEventWithAndWithoutParamsWithoutThrowing() {
        val logger = DebugAnalyticsLogger()
        logger.logEvent("game_start")
        logger.logEvent("game_over", mapOf("mode" to "classic", "score" to 2048, "won" to true))
        logger.setUserProperty("equipped_theme", "midnight")
    }

    @Test
    fun rejectsBlankEventName() {
        val logger = DebugAnalyticsLogger()
        assertFailsWith<IllegalArgumentException> { logger.logEvent("   ") }
    }
}
