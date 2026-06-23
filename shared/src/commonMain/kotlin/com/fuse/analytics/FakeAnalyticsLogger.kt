package com.fuse.analytics

/**
 * ANL-1 — a recording [AnalyticsLogger] for tests/previews.
 *
 * Lives in `commonMain` (not `commonTest`, mirroring [com.fuse.iap.FakeBillingProvider] /
 * [com.fuse.ads.FakeAdProvider]) so the instrumentation stories (ANL-2) can inject it from their own
 * test source sets and from previews without depending on `:shared` test fixtures. Holds no SDK, so
 * it runs on JVM and Kotlin/Native alike.
 *
 * Every [logEvent] is captured into [loggedEvents] (name + a defensive copy of params) so a test can
 * assert exactly what was recorded; [setUserProperty] is captured into [userProperties]. All mutation
 * is single-threaded test usage; not synchronised.
 *
 * ### Example (ANL-2 will assert call sites)
 * ```
 * val analytics = FakeAnalyticsLogger()
 * analytics.logEvent("game_start", mapOf("mode" to "classic"))
 * assertEquals("game_start", analytics.loggedEvents.single().name)
 * assertEquals("classic", analytics.loggedEvents.single().params["mode"])
 * ```
 */
class FakeAnalyticsLogger : AnalyticsLogger {

    /** A single recorded event: its [name] and a copy of the [params] passed at the call site. */
    data class Event(val name: String, val params: Map<String, Any?>)

    /** Every event logged, in order. */
    val loggedEvents: MutableList<Event> = mutableListOf()

    /** Every user property set, in order, as (name, value) pairs. */
    val userProperties: MutableList<Pair<String, String?>> = mutableListOf()

    override fun logEvent(name: String, params: Map<String, Any?>) {
        loggedEvents += Event(name, params.toMap())
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties += name to value
    }

    /** Names of all logged events, in order — convenience for sequence assertions. */
    val loggedEventNames: List<String> get() = loggedEvents.map { it.name }

    /** Clears all recorded state — handy between phases of a test. */
    fun reset() {
        loggedEvents.clear()
        userProperties.clear()
    }
}
