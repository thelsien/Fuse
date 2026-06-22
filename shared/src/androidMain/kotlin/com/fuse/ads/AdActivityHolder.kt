package com.fuse.ads

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * ADS-0 (Android) — a tiny holder for the CURRENT foreground [Activity], so the Android
 * [AdProvider] can present a full-screen rewarded ad (which requires an Activity, not the
 * application `Context` that Koin provides).
 *
 * `MainActivity` sets this in `onResume` and clears its own reference in `onDestroy`. The
 * reference is held WEAKLY so a finished Activity can still be garbage-collected even if we
 * forget to clear it — the holder never leaks an Activity.
 *
 * This is a spike-scoped seam. ADS-1 may replace it with a cleaner lifecycle-aware provider
 * (e.g. an `ActivityLifecycleCallbacks` registration in `FuseApplication`); for ADS-0 the single
 * `MainActivity` setting it is enough.
 */
object AdActivityHolder {
    private var ref: WeakReference<Activity>? = null

    /** The current foreground Activity, or null if none is set / it was collected. */
    val current: Activity?
        get() = ref?.get()

    /** Records [activity] as the current foreground Activity. Called from `MainActivity.onResume`. */
    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    /** Clears [activity] if it is the one currently held. Called from `MainActivity.onDestroy`. */
    fun clear(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }
}
