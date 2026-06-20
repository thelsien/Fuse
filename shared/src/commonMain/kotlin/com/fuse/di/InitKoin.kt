package com.fuse.di

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
    }

/**
 * No-argument entry point convenient to call from Kotlin/Native (Swift sees this
 * as `InitKoinKt.doInitKoin()`).
 */
fun doInitKoin(): KoinApplication = initKoin()
