package com.fuse.di

import com.fuse.ads.PersistedEntitlements
import com.fuse.data.AdsRepository
import com.fuse.iap.BillingProvider
import com.fuse.iap.Iap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

        // IAP-2 — seed-on-launch entitlement reconcile. The STORE is the authoritative source of
        // truth: if `remove_ads` is owned per [BillingProvider.ownedProductIds] (a returning owner,
        // or a fresh install whose local cache is still false), grant the persisted entitlement so
        // interstitials are suppressed even before the player opens the paywall. The persisted cache
        // already gives an instant offline check; this only ever GRANTS (never revokes — a refund is
        // corrected by the store on a later launch / restore), and is idempotent via grantRemoveAds().
        //
        // Best-effort + defensive: billing is suspend (store round-trip) so we launch it on a
        // detached scope, and the whole thing is wrapped so a graph without billing/entitlements
        // (e.g. a bare initKoin() in a JVM test) never crashes app start.
        runCatching {
            val billing = app.koin.get<BillingProvider>()
            val entitlements = app.koin.get<PersistedEntitlements>()
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                runCatching {
                    if (Iap.PRODUCT_REMOVE_ADS in billing.ownedProductIds()) {
                        entitlements.grantRemoveAds()
                    }
                }
            }
        }
    }

/**
 * No-argument entry point convenient to call from Kotlin/Native (Swift sees this
 * as `InitKoinKt.doInitKoin()`).
 */
fun doInitKoin(): KoinApplication = initKoin()
