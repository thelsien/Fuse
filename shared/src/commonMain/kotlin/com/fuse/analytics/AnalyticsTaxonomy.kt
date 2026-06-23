package com.fuse.analytics

/**
 * ANL-2 (Sprint 9) — the **core analytics event taxonomy**: the single, typed source of truth for
 * every event name and param key Fuse logs through [AnalyticsLogger] (ANL-1). Instrumented at the
 * real call sites (Classic [com.fuse.presentation.GameStore], Daily
 * [com.fuse.presentation.DailyStore], ads [com.fuse.ads.AdManager], IAP
 * [com.fuse.presentation.RemoveAdsStore], and the Daily share action in
 * [com.fuse.ui.daily.DailyScreen]).
 *
 * ## No PII, by construction
 * Every param VALUE is an enum/count/boolean/short constant — **never** a device id, email,
 * user-entered text, account id, or any identifier. The defensive [sanitizeParams] backstop exists,
 * but the real guarantee is this taxonomy: the typed helpers below only ever pass ints/booleans and
 * the small fixed value-constants in [AnalyticsValues].
 *
 * ## Volume / sampling stance
 * NONE of the events here are per-move or per-merge — those are intentionally NOT logged as discrete
 * events (a 2048 game emits hundreds of moves; logging each would swamp the backend and add no
 * product signal beyond the aggregate already captured by `game_over`'s score/best_tile/moves). The
 * listed events are all low-frequency lifecycle/outcome events (a game start, a game over, a solve, a
 * share, an ad impression/reward, a purchase), so they need no sampling. If a future story adds a
 * high-frequency event, sample it at the call site (e.g. log 1-in-N) — see the kdoc note on
 * [AnalyticsEvents].
 *
 * ## ftue_step — DEFINED, deferred
 * [AnalyticsEvents.FTUE_STEP] (+ [AnalyticsParams.STEP_INDEX]) is part of the taxonomy NOW, but there
 * is NO first-time-user flow yet (that is Sprint 10 `FTU-*`). It is therefore declared here but NOT
 * instrumented; its call site lands with the FTUE flow, which will call
 * [AnalyticsLogger.logFtueStep] at each onboarding step.
 */
object AnalyticsEvents {
    /** A new Classic game began ([com.fuse.presentation.GameStore], `NewGame` / fresh game). */
    const val GAME_START: String = "game_start"

    /** A Classic game ended in a loss ([com.fuse.presentation.GameStore], transition to Lost). */
    const val GAME_OVER: String = "game_over"

    /** Today's Daily puzzle was solved ([com.fuse.presentation.DailyStore], `Solved`). */
    const val DAILY_COMPLETED: String = "daily_completed"

    /** A share action was invoked (Daily result share — [com.fuse.ui.daily.DailyScreen]). */
    const val SHARE_TAPPED: String = "share_tapped"

    /** A full-screen ad was presented ([com.fuse.ads.AdManager], rewarded or interstitial). */
    const val AD_IMPRESSION: String = "ad_impression"

    /** A rewarded ad granted its reward ([com.fuse.ads.AdManager], verified completion). */
    const val AD_REWARD_GRANTED: String = "ad_reward_granted"

    /** A Remove-Ads purchase completed ([com.fuse.presentation.RemoveAdsStore], `Purchased`). */
    const val IAP_PURCHASE: String = "iap_purchase"

    /**
     * DEFERRED — a step of the first-time-user experience was reached. DEFINED here for the taxonomy
     * but NOT instrumented: there is no FTUE flow yet (Sprint 10 `FTU-*`). The call site lands with
     * the FTUE flow via [AnalyticsLogger.logFtueStep].
     */
    const val FTUE_STEP: String = "ftue_step"
}

/**
 * ANL-2 — the param KEYS for [AnalyticsEvents]. Keys are stable snake_case strings; the typed helpers
 * on [AnalyticsLogger] are the only producers, so a key is never typo'd at a call site. Values are
 * always enums/counts/booleans (no PII — see [AnalyticsEvents] kdoc).
 */
object AnalyticsParams {
    /** Game mode (value from [AnalyticsValues]: `classic` / `daily`). */
    const val MODE: String = "mode"

    /** Final score of a Classic game (int). */
    const val SCORE: String = "score"

    /** Highest tile value reached (int) — `best_tile` keeps the Firebase-friendly snake_case. */
    const val BEST_TILE: String = "best_tile"

    /** Move count (int) — Classic game length, or a Daily solve's move count. */
    const val MOVES: String = "moves"

    /** The Daily #N that was solved (int). */
    const val DAY_NUMBER: String = "day_number"

    /** The Daily puzzle's optimal move count (int). */
    const val PAR: String = "par"

    /** Where a share originated (value from [AnalyticsValues]: `daily`). */
    const val SURFACE: String = "surface"

    /** Ad format (value from [AnalyticsValues]: `rewarded` / `interstitial`). */
    const val FORMAT: String = "format"

    /** Ad placement (value from [AnalyticsValues]: `revive` / `streak_saver` / `game_over`). */
    const val PLACEMENT: String = "placement"

    /** The purchased product id (value from [AnalyticsValues]: `remove_ads`). */
    const val PRODUCT_ID: String = "product_id"

    /** DEFERRED (ftue_step) — the zero-based onboarding step index (int). */
    const val STEP_INDEX: String = "step_index"
}

/**
 * ANL-2 — the fixed, enum-like VALUE constants a few params carry, so call sites and tests share one
 * spelling. Everything else passed is a raw int/boolean. No PII.
 */
object AnalyticsValues {
    /** [AnalyticsParams.MODE] — the Classic endless mode. */
    const val MODE_CLASSIC: String = "classic"

    /** [AnalyticsParams.MODE] — the Daily puzzle mode. */
    const val MODE_DAILY: String = "daily"

    /** [AnalyticsParams.SURFACE] — a share from the Daily screen. */
    const val SURFACE_DAILY: String = "daily"

    /** [AnalyticsParams.FORMAT] — a rewarded ad. */
    const val FORMAT_REWARDED: String = "rewarded"

    /** [AnalyticsParams.FORMAT] — an interstitial ad. */
    const val FORMAT_INTERSTITIAL: String = "interstitial"

    /** [AnalyticsParams.PLACEMENT] — ADS-2 game-over revive (rewarded). */
    const val PLACEMENT_REVIVE: String = "revive"

    /** [AnalyticsParams.PLACEMENT] — ADS-3 daily streak-saver (rewarded). */
    const val PLACEMENT_STREAK_SAVER: String = "streak_saver"

    /** [AnalyticsParams.PLACEMENT] — ADS-4 Classic game-over → replay (interstitial). */
    const val PLACEMENT_GAME_OVER: String = "game_over"

    /** [AnalyticsParams.PRODUCT_ID] — the single Remove-Ads non-consumable. */
    const val PRODUCT_REMOVE_ADS: String = "remove_ads"
}

/**
 * ANL-2 — typed call-site helpers over [AnalyticsLogger.logEvent], one per instrumented event. They
 * keep call sites a single readable line, guarantee the right name + param keys, and keep every value
 * an int/boolean/value-constant (no PII). Tests assert on the resulting [AnalyticsLogger.logEvent]
 * params via [FakeAnalyticsLogger].
 */

/** Logs [AnalyticsEvents.GAME_START] for a Classic game. */
fun AnalyticsLogger.logGameStart() {
    logEvent(
        AnalyticsEvents.GAME_START,
        mapOf(AnalyticsParams.MODE to AnalyticsValues.MODE_CLASSIC),
    )
}

/**
 * Logs [AnalyticsEvents.GAME_OVER] for a Classic loss with its aggregate outcome:
 * [score], [bestTile] (highest tile reached), and [moves] (game length). No per-move events are
 * logged (see [AnalyticsEvents] volume note) — this single aggregate is the signal.
 */
fun AnalyticsLogger.logGameOver(score: Int, bestTile: Int, moves: Int) {
    logEvent(
        AnalyticsEvents.GAME_OVER,
        mapOf(
            AnalyticsParams.MODE to AnalyticsValues.MODE_CLASSIC,
            AnalyticsParams.SCORE to score,
            AnalyticsParams.BEST_TILE to bestTile,
            AnalyticsParams.MOVES to moves,
        ),
    )
}

/** Logs [AnalyticsEvents.DAILY_COMPLETED] with the day, the solve's move count, and the day's par. */
fun AnalyticsLogger.logDailyCompleted(dayNumber: Long, moves: Int, par: Int) {
    logEvent(
        AnalyticsEvents.DAILY_COMPLETED,
        mapOf(
            AnalyticsParams.MODE to AnalyticsValues.MODE_DAILY,
            AnalyticsParams.DAY_NUMBER to dayNumber,
            AnalyticsParams.MOVES to moves,
            AnalyticsParams.PAR to par,
        ),
    )
}

/** Logs [AnalyticsEvents.SHARE_TAPPED] from a [surface] (default Daily). */
fun AnalyticsLogger.logShareTapped(surface: String = AnalyticsValues.SURFACE_DAILY) {
    logEvent(
        AnalyticsEvents.SHARE_TAPPED,
        mapOf(AnalyticsParams.SURFACE to surface),
    )
}

/** Logs [AnalyticsEvents.AD_IMPRESSION] for an ad [format] shown at [placement]. */
fun AnalyticsLogger.logAdImpression(format: String, placement: String) {
    logEvent(
        AnalyticsEvents.AD_IMPRESSION,
        mapOf(
            AnalyticsParams.FORMAT to format,
            AnalyticsParams.PLACEMENT to placement,
        ),
    )
}

/** Logs [AnalyticsEvents.AD_REWARD_GRANTED] for a rewarded ad at [placement]. */
fun AnalyticsLogger.logAdRewardGranted(placement: String) {
    logEvent(
        AnalyticsEvents.AD_REWARD_GRANTED,
        mapOf(
            AnalyticsParams.FORMAT to AnalyticsValues.FORMAT_REWARDED,
            AnalyticsParams.PLACEMENT to placement,
        ),
    )
}

/** Logs [AnalyticsEvents.IAP_PURCHASE] for a successful purchase of [productId] (default Remove-Ads). */
fun AnalyticsLogger.logIapPurchase(productId: String = AnalyticsValues.PRODUCT_REMOVE_ADS) {
    logEvent(
        AnalyticsEvents.IAP_PURCHASE,
        mapOf(AnalyticsParams.PRODUCT_ID to productId),
    )
}

/**
 * DEFERRED — logs [AnalyticsEvents.FTUE_STEP] at onboarding step [stepIndex]. Provided so the Sprint
 * 10 FTUE flow has the call-site helper ready, but NOT invoked anywhere yet (no FTUE flow exists).
 */
fun AnalyticsLogger.logFtueStep(stepIndex: Int) {
    logEvent(
        AnalyticsEvents.FTUE_STEP,
        mapOf(AnalyticsParams.STEP_INDEX to stepIndex),
    )
}
