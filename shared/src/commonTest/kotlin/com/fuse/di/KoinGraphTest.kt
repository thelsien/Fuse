package com.fuse.di

import com.fuse.data.Greeting
import com.fuse.domain.GetGreetingUseCase
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.presentation.SamplePresenter
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
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
        val koin = startKoin { modules(appModules) }.koin
        assertTrue(koin.get<HapticsSettings>().hapticsEnabled, "haptics default ON")
    }

    @Test
    fun reducedMotionToggleBindingResolvesAndDefaultsOff() {
        // FEL-8 — the single reduced-motion switch is bound and default-OFF (full motion). An
        // INDEPENDENT toggle from haptics/sound. `App()` resolves this and feeds it into
        // `FuseTheme(reducedMotion = …)`; default OFF means full motion out of the box.
        val koin = startKoin { modules(appModules) }.koin
        assertFalse(
            koin.get<ReducedMotionSettings>().reducedMotionEnabled,
            "reduced motion default OFF",
        )
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
