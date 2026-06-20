package com.fuse.android

import android.app.Application
import com.fuse.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

/**
 * Android Application that starts the shared Koin graph once, at process launch,
 * before any Activity/Compose content resolves dependencies.
 *
 * It delegates to the shared composition root [initKoin] and only contributes
 * the Android-specific bits (context + logger), keeping the module list in
 * commonMain.
 */
class FuseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@FuseApplication)
        }
    }
}
