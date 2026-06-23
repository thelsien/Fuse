package com.fuse.analytics

/**
 * ANL-1 (Sprint 9) — the app-wide **analytics** seam: a tiny, platform-neutral surface for
 * recording product analytics events, mirroring the established platform-service abstractions
 * ([com.fuse.daily.Sharer], [com.fuse.ads.AdProvider], [com.fuse.iap.BillingProvider]).
 *
 * ## Why an abstraction now, Firebase later
 * Product locked the decision: build the analytics plumbing this sprint behind a
 * **debug-verifiable** logger, and drop in real Firebase LATER (no Firebase project/config exists
 * yet). So ANL-1 ships:
 *  - this generic [AnalyticsLogger] interface,
 *  - a [NoOpAnalyticsLogger] (safe default — does nothing),
 *  - a [DebugAnalyticsLogger] bound on BOTH platforms (the "initialized on both platforms"
 *    acceptance criterion is met by a live, resolvable logger that prints each event to the
 *    device console — logcat on Android, Xcode/Console on iOS — see [analyticsDebugPrint]),
 *  - a [FakeAnalyticsLogger] (commonMain) for tests.
 *
 * ### The Firebase-later seam
 * When the user creates the Firebase project, a `FirebaseAnalyticsLogger` (GitLive
 * `dev.gitlive:firebase-analytics` KMP wrapper, or native per-platform) REPLACES the
 * [DebugAnalyticsLogger] binding in `platformAnalyticsModule` (`androidMain` / `iosMain`), and the
 * `google-services.json` / `GoogleService-Info.plist` go in the already-gitignored config. Nothing
 * else in the app changes — every call site depends only on this interface. **Do NOT add the
 * Firebase SDK or config now.** See `docs/analytics/ANL-1-firebase-seam.md`.
 *
 * ## No PII (privacy)
 * Events must carry **no personal identifiers** — no device ids, no emails, no user-entered text,
 * no account ids. The event taxonomy (ANL-2) keeps params to enums/counts/booleans only. As a
 * defensive backstop, [sanitizeParams] is provided for call sites to strip obviously-PII-keyed
 * params, but the real guarantee is the taxonomy. No data leaves the device under the debug logger.
 *
 * The taxonomy itself is **ANL-2** (game_start, game_over, daily_completed, share_tapped,
 * ad_impression, ad_reward_granted, iap_purchase, ftue_step). ANL-1 only ships the generic logger.
 */
interface AnalyticsLogger {

    /**
     * Records a single analytics event.
     *
     * @param name the event name (snake_case, taxonomy-defined in ANL-2). Must not be empty.
     * @param params event parameters — **enums/counts/booleans only, never PII**. Values are kept
     *   loose ([Any]?) to match the underlying SDKs (Firebase accepts String/Long/Double/Bundle);
     *   the taxonomy in ANL-2 narrows what's actually passed.
     */
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())

    /**
     * Sets a non-identifying user property (e.g. a coarse segment like `"theme":"midnight"`).
     * **Never an identifier** — no user id, device id, email. Optional; default no-op so most
     * loggers needn't implement it. Pass a null [value] to clear.
     */
    fun setUserProperty(name: String, value: String?) { /* optional; default no-op */ }
}

/**
 * The safe default [AnalyticsLogger] — drops every event silently. Used in tests/previews and as the
 * fallback when no real analytics backend is wired. The app behaves identically with analytics off.
 */
object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Map<String, Any?>) { /* no-op */ }
    override fun setUserProperty(name: String, value: String?) { /* no-op */ }
}

/**
 * A **debug-verifiable** [AnalyticsLogger] that prints each event to the platform debug console via
 * the [analyticsDebugPrint] `expect`/`actual` seam (Android → `Log.d`, iOS → `NSLog`). This is the
 * binding live on BOTH platforms for ANL-1, so analytics is observably "initialized" without any
 * SDK: trigger an event and confirm the `[Fuse-Analytics] …` line in logcat / the Xcode console.
 *
 * It lives in `commonMain` (the only platform-specific part is the one-line print seam) so its
 * formatting is shared and identical across Android and iOS.
 */
class DebugAnalyticsLogger(
    /** Log tag/prefix so events are greppable in a noisy console. */
    private val tag: String = DEFAULT_TAG,
) : AnalyticsLogger {

    override fun logEvent(name: String, params: Map<String, Any?>) {
        require(name.isNotBlank()) { "Analytics event name must not be blank" }
        val rendered = if (params.isEmpty()) {
            "event=$name"
        } else {
            "event=$name " + params.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=$v" }
        }
        analyticsDebugPrint("$tag $rendered")
    }

    override fun setUserProperty(name: String, value: String?) {
        analyticsDebugPrint("$tag userProperty $name=$value")
    }

    companion object {
        const val DEFAULT_TAG: String = "[Fuse-Analytics]"
    }
}

/**
 * Defensive no-PII helper: strips params whose KEY looks like a personal identifier (email, device
 * id, user id, name, phone, …). The taxonomy (ANL-2) is the real guarantee — params are enums/counts
 * — but call sites can route through this as a backstop. Pure; commonMain-testable.
 */
fun sanitizeParams(params: Map<String, Any?>): Map<String, Any?> =
    params.filterKeys { key -> PII_KEY_FRAGMENTS.none { key.contains(it, ignoreCase = true) } }

/** Key fragments that signal a param is (or could carry) PII and must be dropped by [sanitizeParams]. */
private val PII_KEY_FRAGMENTS: List<String> = listOf(
    "email", "deviceid", "device_id", "userid", "user_id", "uid",
    "name", "phone", "address", "lat", "lon", "ip", "advertisingid", "idfa", "gaid",
)
