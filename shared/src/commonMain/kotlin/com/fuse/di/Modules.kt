package com.fuse.di

import com.fuse.data.AchievementsRepository
import com.fuse.data.CosmeticsRepository
import com.fuse.data.DailyRepository
import com.fuse.data.DailyStreakRepository
import com.fuse.data.DefaultGreeting
import com.fuse.data.GameRepository
import com.fuse.data.Greeting
import com.fuse.data.SettingsAchievementsRepository
import com.fuse.data.SettingsCosmeticsRepository
import com.fuse.data.SettingsDailyRepository
import com.fuse.data.SettingsDailyStreakRepository
import com.fuse.data.SettingsGameRepository
import com.fuse.data.AdsRepository
import com.fuse.data.SettingsAdsRepository
import com.fuse.data.platformSettingsModule
import com.fuse.ads.AdManager
import com.fuse.ads.Entitlements
import com.fuse.ads.InterstitialController
import com.fuse.ads.NoOpEntitlements
import com.fuse.ads.platformAdsModule
import com.fuse.iap.platformBillingModule
import com.fuse.daily.DailyClock
import com.fuse.daily.SystemDailyClock
import com.fuse.daily.platformSharerModule
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.CosmeticsStore
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyStreakStore
import com.fuse.domain.GetGreetingUseCase
import com.fuse.feedback.ColorblindSettings
import com.fuse.feedback.FeedbackPreferences
import com.fuse.feedback.HapticsCoordinator
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.feedback.SettingsFeedbackPreferences
import com.fuse.feedback.SoundCoordinator
import com.fuse.feedback.SoundSettings
import com.fuse.feedback.platformHapticsModule
import com.fuse.feedback.platformSoundModule
import com.fuse.presentation.GameStore
import com.fuse.presentation.SamplePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Per-layer Koin modules for Fuse. One module per architecture layer
 * (engine / domain / data / presentation / ui) so each sprint adds its real
 * bindings to the matching module without touching the others.
 *
 * For FND-3 the modules are intentionally near-empty: they only carry the
 * *sample dependency* chain that proves the graph wires across all layers
 * (data -> domain -> presentation), which is the story's acceptance criterion.
 */

/** Engine layer — pure game logic (Sprint 1 ENG-*). No bindings yet. */
val engineModule: Module = module {
}

/**
 * Daily Challenge layer (DLY-1) — the date->seed foundation.
 *
 * Binds the [DailyClock] seam (the only impure part of the daily) to its
 * device-backed [SystemDailyClock]. DLY-3 (generator) and DLY-4 (mode) resolve
 * this to learn TODAY's UTC calendar day, then feed it into the pure
 * `dateToSeed` / `dailyDayNumber` functions. `single` — stateless, one is enough.
 */
val dailyModule: Module = module {
    single<DailyClock> { SystemDailyClock() }
}

/** Data layer — repositories / local sources. Provides the sample [Greeting]. */
val dataModule: Module = module {
    single<Greeting> { DefaultGreeting() }
    // UIB-6: local persistence for the in-progress game + best score. Depends on the
    // platform `Settings` bound by [platformSettingsModule] (SharedPreferences on
    // Android, NSUserDefaults on iOS).
    single<GameRepository> { SettingsGameRepository(get()) }
    // DLY-4: the single daily save slot's persistence over the SAME platform `Settings`
    // (independent key from Classic's game blob — see SettingsDailyRepository.KEY_DAILY).
    single<DailyRepository> { SettingsDailyRepository(get()) }
    // DLY-5: the daily STREAK's persistence over the SAME platform `Settings`, under its OWN
    // key (fuse.daily.streak — SEPARATE from the per-day puzzle slot fuse.daily.progress) so
    // the streak outlives every new-day reset of the in-progress puzzle.
    single<DailyStreakRepository> { SettingsDailyStreakRepository(get()) }
    // COS-1: the player's earned ACHIEVEMENTS (e.g. reached 2048) — over the SAME platform
    // `Settings`, under its OWN key (fuse.achievements). Drives cosmetic unlocking; no currency.
    single<AchievementsRepository> { SettingsAchievementsRepository(get()) }
    // COS-1: the player's EQUIPPED cosmetics (the only persisted cosmetics choice; owned is
    // DERIVED from achievements). Own key (fuse.cosmetics.equipped).
    single<CosmeticsRepository> { SettingsCosmeticsRepository(get()) }
    // ADS-4: the Classic game-over interstitial's persisted cap state (replay counter) + the
    // first-session launch marker — over the SAME platform `Settings`, under its OWN keys
    // (fuse.ads.interstitial / fuse.ads.launchCount). Survives relaunch so the every-Nth cap is
    // real, and the launch counter (advanced once at app start in initKoin) drives first-session
    // suppression. Read by [InterstitialController].
    single<AdsRepository> { SettingsAdsRepository(get()) }
}

/** Domain layer — use cases. Sample use case consuming the data abstraction. */
val domainModule: Module = module {
    factory { GetGreetingUseCase(get()) }
}

/** Presentation layer — MVI stores/presenters. */
val presentationModule: Module = module {
    factory { SamplePresenter(get()) }
    // ADS-1/ADS-2 — the load-then-show coordinator over the platform [com.fuse.ads.AdProvider]
    // (bound by [platformAdsModule]). `single` mirrors the provider's single (one stateful ad
    // cache). ADS-2 wires this into GameScreen's game-over revive: a verified rewarded completion
    // (showRewarded() → AdResult.isRewardEarned) grants GameIntent.Revive. ADS-3/4 reuse it.
    single { AdManager(provider = get()) }
    // ADS-4 — the Remove-Ads ENTITLEMENT hook the game-over interstitial is gated on. Always-false
    // [NoOpEntitlements] until IAP-2 (Sprint 9) replaces this binding with real purchase state; from
    // that moment interstitials are suppressed for entitled players (rewarded ADS-2/3 stay ungated).
    single<Entitlements> { NoOpEntitlements }
    // ADS-4 — the count/persist/decide glue between the lose-overlay "Restart" tap and the pure
    // InterstitialPolicy. `single` (one shared, stateful cadence). GameScreen calls onReplay() once
    // per game-over → replay; if it returns true the UI shows adManager.showInterstitial() THEN
    // restarts (replay never blocked by an ad). Reads the persisted cap state + first-session marker
    // ([AdsRepository]) and the Remove-Ads hook ([Entitlements]).
    single { InterstitialController(repository = get(), entitlements = get()) }
    // UIB-3/UIB-6: the game's MVI store. `single` — it holds the live GameState, so the
    // whole app shares one game instance. The store takes the [GameRepository] so it
    // loads any saved game/best on init (resume) and persists after every change.
    single { GameStore(repository = get()) }
    // DLY-4: the Daily Challenge MVI store. `single` — it holds the live daily run, so the
    // whole app shares one. It resolves the [DailyClock] (today's UTC day → today's puzzle)
    // and the [DailyRepository] (the single slot: resume today / reset on a new day).
    single { DailyStore(clock = get(), repository = get()) }
    // DLY-5: the Daily STREAK store. `single` — it owns the live, persisted streak shared by
    // the Daily screen (solved overlay) and Home. It resolves the [DailyClock] (today's day,
    // for the live-current display) and the [DailyStreakRepository] (its own slot). `DailyScreen`
    // collects [DailyStore]'s one-shot Solved effect and calls [DailyStreakStore.recordSolved],
    // so a solve records the streak exactly once (idempotent for the same day).
    single { DailyStreakStore(clock = get(), repository = get()) }
    // COS-1: the ACHIEVEMENTS store — owns the persisted PlayerAchievements record and exposes
    // it so the cosmetics layer can recompute unlocks live. `single` (one shared record).
    // `GameScreen` collects GameStore's one-shot GameEffect.Won and calls markReached2048()
    // (idempotent), mirroring how DailyScreen records the streak on DailyEffect.Solved.
    single { AchievementsStore(repository = get()) }
    // COS-1: the COSMETICS store — catalog + unlocked (derived from AchievementsStore) +
    // equipped (persisted). `single`. It observes the achievements flow on a long-lived app
    // scope so reaching 2048 unlocks the gated skin live; COS-2 reads equipped → theme override,
    // COS-3 renders the collection + Equip.
    single {
        CosmeticsStore(
            achievementsStore = get(),
            repository = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
}

/**
 * Feedback layer (FEL-4) — haptic feedback wiring.
 *
 * Binds the enable/disable toggle ([HapticsSettings], default-on; SHL-3 will persist/flip
 * it) and the pure [HapticsCoordinator] that maps move outcomes to [com.fuse.feedback.Haptics]
 * calls. The platform [com.fuse.feedback.Haptics] itself is bound by [platformHapticsModule]
 * (Vibrator on Android, UIFeedbackGenerator on iOS), which must precede this module.
 */
val feedbackModule: Module = module {
    // SHL-3 — the persistence seam for the four settings toggles. Backed by the SAME platform
    // `Settings` (SharedPreferences / NSUserDefaults) as [SettingsGameRepository], so all of
    // Fuse's local state lives in one store. Each holder below is SEEDED from this at startup
    // (relaunch restores the user's choices) and writes through on flip via its `setEnabled`.
    single<FeedbackPreferences> { SettingsFeedbackPreferences(get()) }

    // FEL-4 / SHL-3 — haptics gate, seeded from + write-through to persistence (default ON).
    single { HapticsSettings(hapticsEnabled = get<FeedbackPreferences>().loadHaptics(), preferences = get()) }
    factory { HapticsCoordinator(haptics = get(), settings = get()) }
    // FEL-5 / SHL-3 — sound effects wiring. A SEPARATE mute toggle ([SoundSettings], default-on; a
    // player may mute sound while keeping haptics) and the pure [SoundCoordinator] mapping
    // move outcomes to [com.fuse.feedback.Sound] calls (climbing merge tone + milestone/win
    // stings). The platform [com.fuse.feedback.Sound] is bound by [platformSoundModule]
    // (AudioTrack synth on Android, AVAudioEngine synth on iOS), which must precede this. Seeded
    // from + write-through to persistence (default ON).
    single { SoundSettings(soundEnabled = get<FeedbackPreferences>().loadSound(), preferences = get()) }
    factory { SoundCoordinator(sound = get(), settings = get()) }
    // FEL-8 / SHL-3 — the single reduced-motion switch. A THIRD independent toggle
    // ([ReducedMotionSettings], default-OFF = full motion). `App()` resolves it and feeds its
    // value into `FuseTheme(reducedMotion = …)`, so one flip collapses every FEL-1..7 animation
    // (slides/overshoot snap; milestone burst+flash and combo badge suppressed). Compose-state-
    // backed, so flipping it from the SHL-3 settings screen recomposes live without a restart.
    // Seeded from + write-through to persistence (default OFF).
    single {
        ReducedMotionSettings(
            reducedMotionEnabled = get<FeedbackPreferences>().loadReducedMotion(),
            preferences = get(),
        )
    }
    // SHL-3 — colorblind-mode toggle. A FOURTH independent toggle ([ColorblindSettings],
    // default-OFF). `App()` resolves it and feeds its value into `FuseTheme(colorblind = …)`, so a
    // flip re-themes live (same chain as reduced-motion). The colorblind-SAFE palette behind the
    // flag is ACC-1 (Sprint 10); SHL-3 ships the persisted, live toggle + seam. Seeded from +
    // write-through to persistence (default OFF).
    single {
        ColorblindSettings(
            colorblindEnabled = get<FeedbackPreferences>().loadColorblind(),
            preferences = get(),
        )
    }
}

/** UI layer — composable-scoped providers (FND-4 design tokens etc.). Empty. */
val uiModule: Module = module {
}

/** The full application graph, ordered by layer. Used by [initKoin].
 *
 * [platformSettingsModule] is the per-platform `expect`/`actual` binding for the
 * `Settings` store (SharedPreferences / NSUserDefaults); it must precede [dataModule],
 * which resolves `Settings` for the [com.fuse.data.GameRepository]. */
val appModules: List<Module> = listOf(
    engineModule,
    dailyModule,
    platformSettingsModule,
    platformHapticsModule,
    platformSoundModule,
    // DLY-7 — the native share seam (ACTION_SEND chooser / UIActivityViewController). The
    // Daily solved overlay resolves [com.fuse.daily.Sharer] from here to share the result card.
    platformSharerModule,
    // ADS-0 (Sprint 8 spike) — the native ad seam (AdProvider expect/actual). Settings' debug
    // "Show test ad" trigger resolves [com.fuse.ads.AdProvider] from here to load+show ONE
    // Google-TEST rewarded ad. Behind a flag; not wired into any real placement (that's ADS-2/4).
    platformAdsModule,
    // IAP-0 (Sprint 9 spike) — the native billing seam (BillingProvider expect/actual). Settings'
    // debug "Buy Remove Ads (spike)" trigger resolves [com.fuse.iap.BillingProvider] from here to
    // query + purchase the single `remove_ads` non-consumable (StoreKit 2 on iOS, Play Billing on
    // Android). Behind a flag; not wired into entitlements/ads (that's IAP-2) or a paywall (IAP-4).
    platformBillingModule,
    dataModule,
    domainModule,
    presentationModule,
    feedbackModule,
    uiModule,
)
