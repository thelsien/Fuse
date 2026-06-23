package com.fuse.data

import com.fuse.ads.InterstitialState
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * ADS-4 — local persistence for the interstitial cap state + the first-session launch marker.
 *
 * Mirrors [SettingsAchievementsRepository] / [SettingsDailyStreakRepository] in shape: the SAME
 * platform [Settings] (via Koin) under its OWN keys, distinct from every other Fuse slot, so the
 * interstitial cadence never collides with the game blob, daily slots, achievements, or equipped
 * cosmetics. A missing/corrupt blob → a zeroed default (never a crash).
 *
 * Two independent pieces of state:
 *  - **Replay cap state** ([loadInterstitialState] / [saveInterstitialState]) under
 *    [SettingsAdsRepository.KEY_INTERSTITIAL] (`fuse.ads.interstitial`): the persisted
 *    [InterstitialState.replayCount] that drives [com.fuse.ads.InterstitialPolicy]'s every-Nth
 *    cadence, so the hard cap is REAL across relaunches (not reset every session).
 *  - **Launch counter** ([loadLaunchCount] / [recordLaunch]) under
 *    [SettingsAdsRepository.KEY_LAUNCH_COUNT] (`fuse.ads.launchCount`): the first-session marker.
 *    "First session" is defined concretely as **the very first app launch** — i.e. the launch
 *    counter is `0` before [recordLaunch], `1` during the first session, and `>= 2` thereafter.
 *    [isFirstSession] is therefore `launchCount <= 1`: it is `true` on the first launch (where
 *    [recordLaunch] at app start sets it to 1) and `false` on every subsequent launch. Recording the
 *    launch at app start is deterministic; tests inject the count directly rather than relying on
 *    process lifecycle.
 */
interface AdsRepository {
    /** Loads the persisted interstitial cap state, or a zeroed [InterstitialState] if none/corrupt. */
    fun loadInterstitialState(): InterstitialState

    /** Persists the interstitial cap [state], overwriting any prior value. */
    fun saveInterstitialState(state: InterstitialState)

    /** The number of app launches recorded so far (0 before the very first [recordLaunch]). */
    fun loadLaunchCount(): Int

    /**
     * Increments and persists the launch counter (called once per app start). Returns the NEW count
     * (so the caller can derive [isFirstSession] from this launch in one step).
     */
    fun recordLaunch(): Int

    /**
     * Whether the app is in its FIRST-EVER session: `true` when the persisted launch counter is at
     * most 1 (i.e. this is the first launch, which [recordLaunch] sets to 1). `false` from the
     * second launch on. The interstitial is suppressed for the whole first session.
     */
    fun isFirstSession(): Boolean = loadLaunchCount() <= 1
}

/**
 * ADS-4 — [AdsRepository] backed by multiplatform-settings [Settings] + JSON. Lenient JSON so an
 * older/newer blob still decodes, matching the rest of Fuse's `Settings`-backed persistence.
 */
class SettingsAdsRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : AdsRepository {

    override fun loadInterstitialState(): InterstitialState {
        val blob = settings.getStringOrNull(KEY_INTERSTITIAL) ?: return InterstitialState()
        return runCatching { json.decodeFromString(InterstitialState.serializer(), blob) }
            .getOrNull() ?: InterstitialState()
    }

    override fun saveInterstitialState(state: InterstitialState) {
        settings.putString(KEY_INTERSTITIAL, json.encodeToString(InterstitialState.serializer(), state))
    }

    override fun loadLaunchCount(): Int = settings.getInt(KEY_LAUNCH_COUNT, 0)

    override fun recordLaunch(): Int {
        val next = loadLaunchCount() + 1
        settings.putInt(KEY_LAUNCH_COUNT, next)
        return next
    }

    companion object {
        /** Storage key for the interstitial cap state blob. Distinct from all other Fuse keys. */
        const val KEY_INTERSTITIAL: String = "fuse.ads.interstitial"

        /** Storage key for the launch counter (first-session marker). Distinct from all other keys. */
        const val KEY_LAUNCH_COUNT: String = "fuse.ads.launchCount"

        /** Lenient JSON: tolerate a slightly newer/older blob; a genuinely bad one → default. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * ADS-4 — a no-op, never-persisting [AdsRepository]: the default for tests/previews and any build
 * without a real [Settings]. The interstitial state is always zeroed and the launch counter never
 * advances — which means [isFirstSession] is permanently `true`, so a NoOp wiring suppresses the
 * interstitial entirely (the safe, ad-free default).
 */
object NoOpAdsRepository : AdsRepository {
    override fun loadInterstitialState(): InterstitialState = InterstitialState()
    override fun saveInterstitialState(state: InterstitialState) = Unit
    override fun loadLaunchCount(): Int = 0
    override fun recordLaunch(): Int = 0
}
