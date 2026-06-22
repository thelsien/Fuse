package com.fuse.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fuse.ads.AdActivityHolder
import com.fuse.shared.App

/**
 * Single activity that hosts the Compose Multiplatform UI.
 *
 * ADS-0 (Sprint 8 spike): registers itself with [AdActivityHolder] while in the foreground, so the
 * Android [com.fuse.ads.AdProvider] can present a full-screen rewarded test ad (which requires an
 * Activity, not the application Context that Koin provides). The holder keeps only a weak reference.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        AdActivityHolder.set(this)
    }

    override fun onDestroy() {
        AdActivityHolder.clear(this)
        super.onDestroy()
    }
}
