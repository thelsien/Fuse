package com.fuse.di

import com.fuse.data.DefaultGreeting
import com.fuse.data.GameRepository
import com.fuse.data.Greeting
import com.fuse.data.SettingsGameRepository
import com.fuse.data.platformSettingsModule
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

/** Data layer — repositories / local sources. Provides the sample [Greeting]. */
val dataModule: Module = module {
    single<Greeting> { DefaultGreeting() }
    // UIB-6: local persistence for the in-progress game + best score. Depends on the
    // platform `Settings` bound by [platformSettingsModule] (SharedPreferences on
    // Android, NSUserDefaults on iOS).
    single<GameRepository> { SettingsGameRepository(get()) }
}

/** Domain layer — use cases. Sample use case consuming the data abstraction. */
val domainModule: Module = module {
    factory { GetGreetingUseCase(get()) }
}

/** Presentation layer — MVI stores/presenters. */
val presentationModule: Module = module {
    factory { SamplePresenter(get()) }
    // UIB-3/UIB-6: the game's MVI store. `single` — it holds the live GameState, so the
    // whole app shares one game instance. The store takes the [GameRepository] so it
    // loads any saved game/best on init (resume) and persists after every change.
    single { GameStore(repository = get()) }
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
    platformSettingsModule,
    platformHapticsModule,
    platformSoundModule,
    dataModule,
    domainModule,
    presentationModule,
    feedbackModule,
    uiModule,
)
