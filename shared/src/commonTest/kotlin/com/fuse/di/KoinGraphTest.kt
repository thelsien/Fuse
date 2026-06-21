package com.fuse.di

import com.fuse.daily.DailyClock
import com.fuse.data.Greeting
import com.fuse.domain.GetGreetingUseCase
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.ReducedMotionSettings
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import com.fuse.presentation.SamplePresenter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the FND-3 acceptance criterion: the Koin graph wires the (currently
 * empty) layers and the sample dependency resolves end to end. Runs on every CI
 * target — JVM via :shared:testDebugUnitTest and Kotlin/Native via
 * :shared:iosSimulatorArm64Test.
 */
class KoinGraphTest : KoinTest {

    @AfterTest
    fun tearDown() = stopKoin()

    /**
     * SHL-3 — overrides the platform `Settings` (whose Android actual needs a `Context` the app
     * shell supplies but a plain JVM Koin test does not) with an in-memory `MapSettings`. This is
     * the same `Settings` the feedback persistence ([com.fuse.feedback.SettingsFeedbackPreferences],
     * which seeds the settings holders) resolves, so the toggle bindings construct in tests.
     */
    private val testSettingsModule = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun graphStartsAndResolvesSampleDependencyChain() {
        val koin = startKoin { modules(appModules) }.koin

        // Each layer's sample binding resolves...
        val greeting = koin.get<Greeting>()
        val useCase = koin.get<GetGreetingUseCase>()
        val presenter = koin.get<SamplePresenter>()

        assertNotNull(greeting)
        assertNotNull(useCase)
        assertNotNull(presenter)

        // ...and the cross-layer chain (data -> domain -> presentation) produces
        // the expected value, demonstrating real wiring, not just instantiation.
        assertEquals("Fuse DI ready", greeting.greet())
        assertEquals("Fuse DI ready", useCase())
        assertEquals("Fuse DI ready", presenter.greetingText())
    }

    @Test
    fun haptricsToggleBindingResolvesAndDefaultsOn() {
        // FEL-4 — the haptics enable/disable seam is bound and default-ON. (The platform
        // [Haptics] / [HapticsCoordinator] are NOT resolved here: on the Android target
        // the Haptics actual needs a `Context` from the graph — supplied only by the real
        // app shell — so resolving it in a plain JVM Koin test would fail, exactly as the
        // sample-chain test deliberately avoids resolving `Context`-backed bindings.)
        val koin = startKoin { modules(appModules + testSettingsModule) }.koin
        assertTrue(koin.get<HapticsSettings>().hapticsEnabled, "haptics default ON")
    }

    @Test
    fun reducedMotionToggleBindingResolvesAndDefaultsOff() {
        // FEL-8 — the single reduced-motion switch is bound and default-OFF (full motion). An
        // INDEPENDENT toggle from haptics/sound. `App()` resolves this and feeds it into
        // `FuseTheme(reducedMotion = …)`; default OFF means full motion out of the box.
        val koin = startKoin { modules(appModules + testSettingsModule) }.koin
        assertFalse(
            koin.get<ReducedMotionSettings>().reducedMotionEnabled,
            "reduced motion default OFF",
        )
    }

    @Test
    fun dailyClockBindingResolves() {
        // DLY-1 — the Daily Challenge clock seam is bound (device-backed
        // SystemDailyClock). DLY-3/DLY-4 resolve this for TODAY's UTC day; here we
        // only assert the binding constructs and yields a non-null calendar day.
        val koin = startKoin { modules(appModules + testSettingsModule) }.koin
        assertNotNull(koin.get<DailyClock>().todayUtc())
    }

    @Test
    fun dailyStoreBindingResolves() {
        // DLY-4 — the Daily Challenge store + its single-slot repository wire up: the store
        // resolves the DailyClock (today's puzzle) and the DailyRepository (over the test
        // Settings) and starts today's run at move 0.
        val koin = startKoin { modules(appModules + testSettingsModule) }.koin
        val store = koin.get<com.fuse.presentation.DailyStore>()
        assertEquals(0, store.state.value.moveCount, "daily starts at move 0")
        assertNotNull(koin.get<com.fuse.data.DailyRepository>())
    }

    @Test
    fun dailyStreakBindingsResolveAndDefaultToZero() {
        // DLY-5 — the streak repository + store wire up over the test Settings. A fresh graph
        // (no prior completion) projects a zeroed streak (current 0, longest 0).
        val koin = startKoin { modules(appModules + testSettingsModule) }.koin
        assertNotNull(koin.get<com.fuse.data.DailyStreakRepository>())
        val streak = koin.get<com.fuse.presentation.DailyStreakStore>().state.value
        assertEquals(0, streak.current, "no completions → current 0")
        assertEquals(0, streak.longest, "no completions → longest 0")
    }

    @Test
    fun initKoinCompositionRootStartsTheSameGraph() {
        // The shared composition root the app shells call must produce a graph in
        // which the sample dependency resolves — proving Android/iOS init paths
        // wire the same modules. (Koin's reflective verify() is JVM-only, so the
        // platform-safe check is to actually resolve from the started graph.)
        val koin = initKoin().koin
        assertEquals("Fuse DI ready", koin.get<SamplePresenter>().greetingText())
    }
}
