package com.fuse.di

import com.fuse.data.Greeting
import com.fuse.domain.GetGreetingUseCase
import com.fuse.presentation.SamplePresenter
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    fun initKoinCompositionRootStartsTheSameGraph() {
        // The shared composition root the app shells call must produce a graph in
        // which the sample dependency resolves — proving Android/iOS init paths
        // wire the same modules. (Koin's reflective verify() is JVM-only, so the
        // platform-safe check is to actually resolve from the started graph.)
        val koin = initKoin().koin
        assertEquals("Fuse DI ready", koin.get<SamplePresenter>().greetingText())
    }
}
