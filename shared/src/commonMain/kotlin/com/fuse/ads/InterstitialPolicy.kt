package com.fuse.ads

import kotlinx.serialization.Serializable

/**
 * ADS-4 (Sprint 8) — the PURE, ambient-free placement policy for the **Classic game-over → replay**
 * interstitial. It is the single brain that decides whether tapping "Restart" on the lose overlay
 * should be preceded by a full-screen interstitial, and how the persisted counter advances. Because
 * it is a pure function over its explicit inputs ([decide]), every rule is unit-tested with no SDK,
 * no Compose, and no Settings.
 *
 * ## The placement (where this fires)
 * ONLY the Classic **game-over → replay** path (the give-up → new game choice on [com.fuse.ui.game]'s
 * lose overlay). NEVER mid-run, NEVER on a win, and NEVER on the rewarded REVIVE path (a revived
 * player keeps playing, so no interstitial). Rewarded ads (ADS-2 revive, ADS-3 streak-saver) are
 * UNgated and untouched by this policy.
 *
 * ## The three suppression rules (in priority order)
 *  1. **Remove-Ads entitlement** — if [removeAdsOwned] is `true`, NEVER show. This is the single
 *     gate the interstitial is suppressed on; IAP-2 (Sprint 9) flips the [Entitlements] hook true on
 *     a Remove-Ads purchase and interstitials stop instantly (rewarded stays ungated).
 *  2. **First session** — if [isFirstSession] is `true`, NEVER show. The very first app launch is a
 *     no-ad grace window so a brand-new player's first replays are clean.
 *  3. **Frequency cap (every-Nth)** — once neither suppressor applies, count qualifying replays and
 *     show on every [CAP_EVERY_NTH]th one. Concretely the counter advances FIRST, then we show iff
 *     `newReplayCount % CAP_EVERY_NTH == 0`. With [CAP_EVERY_NTH] = 3 the cadence over consecutive
 *     replays is: 1 no, 2 no, **3 SHOW**, 4 no, 5 no, **6 SHOW**, … — a hard cap of one interstitial
 *     per three give-up replays. The counter PERSISTS (via [com.fuse.data.AdsRepository]) so the
 *     cadence is real across relaunches, not reset every session.
 *
 * The counter still advances on suppressed replays so the cadence is positional, not "every 3rd
 * *eligible*" — a player who buys Remove-Ads and later refunds, or whose first session ends, picks up
 * the cadence where the count left off rather than restarting it. (Suppression wins regardless; the
 * advance just keeps the count honest.)
 */
object InterstitialPolicy {

    /** Show on every Nth qualifying game-over → replay. Concrete, documented cap value. */
    const val CAP_EVERY_NTH: Int = 3

    /**
     * Decides the outcome of ONE game-over → replay, given the persisted [state] and the ambient
     * suppressors. Pure: returns the (already-advanced) next [InterstitialState] to persist AND
     * whether to actually present the interstitial now.
     *
     * @param state the persisted counter state BEFORE this replay.
     * @param removeAdsOwned the Remove-Ads entitlement (default-false until IAP-2); `true` ⇒ never show.
     * @param isFirstSession whether the app is in its first-ever session; `true` ⇒ never show.
     */
    fun decide(
        state: InterstitialState,
        removeAdsOwned: Boolean,
        isFirstSession: Boolean,
    ): Decision {
        val advanced = state.copy(replayCount = state.replayCount + 1)
        val suppressed = removeAdsOwned || isFirstSession
        val show = !suppressed && advanced.replayCount % CAP_EVERY_NTH == 0
        return Decision(shouldShow = show, nextState = advanced)
    }

    /** The result of [decide]: whether to present an interstitial, and the state to persist. */
    data class Decision(
        /** `true` ⇒ the UI should call `adManager.showInterstitial()` before starting the new game. */
        val shouldShow: Boolean,
        /** The advanced [InterstitialState] the caller must persist (regardless of [shouldShow]). */
        val nextState: InterstitialState,
    )
}

/**
 * ADS-4 — the tiny persisted state the interstitial cap needs. A monotonically increasing count of
 * Classic game-over → replays (the cadence "clock" for [InterstitialPolicy]). Kept @[Serializable]
 * and persisted via [com.fuse.data.AdsRepository] so the every-Nth cadence survives relaunch and the
 * cap is REAL, not per-session. First-session detection is a SEPARATE persisted marker (a launch
 * counter; see [com.fuse.data.AdsRepository]) — it is not part of this replay clock.
 */
@Serializable
data class InterstitialState(
    /** How many qualifying game-over → replays have occurred (starts at 0; advances by 1 per replay). */
    val replayCount: Int = 0,
)
