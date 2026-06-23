package com.fuse.di

import com.fuse.data.AdsRepository
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Single composition root for the Fuse DI graph, callable from every app shell.
 *
 * Both platforms funnel through here so the module list lives in one place:
 *  - Android: called from the Application (see :androidApp FuseApplication).
 *  - iOS:     called from [doInitKoin] (Swift-friendly bridge) before the first
 *             Compose view controller is created.
 *
 * [appDeclaration] lets a platform contribute extra setup (e.g. Android
 * `androidContext(...)`, logger) without the shared module depending on it.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModules)
    }.also { app ->
        // ADS-4 — advance the persisted launch counter ONCE per app start (deterministic, here in the
        // single composition root both platforms funnel through). The first launch sets it to 1, so
        // [AdsRepository.isFirstSession] is true for the whole first session and false thereafter —
        // this is what suppresses the Classic game-over interstitial during the first session.
        //
        // Best-effort: a graph started without the platform `Settings` binding (e.g. a bare
        // `initKoin()` in a JVM test with no androidContext) must not crash app start — the marker
        // simply does not advance there. Both real shells DO bind `Settings`, so this advances on
        // every real launch.
        runCatching { app.koin.get<AdsRepository>().recordLaunch() }
    }

/**
 * No-argument entry point convenient to call from Kotlin/Native (Swift sees this
 * as `InitKoinKt.doInitKoin()`).
 */
fun doInitKoin(): KoinApplication = initKoin()
