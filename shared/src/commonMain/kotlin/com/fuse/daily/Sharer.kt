package com.fuse.daily

import org.koin.core.module.Module

/**
 * DLY-7 — the platform **share** seam: opens the native share sheet with a piece of plain
 * text (the Daily result card built by [buildDailyShareCard]).
 *
 * This is the FIRST `Sharer` `expect`/`actual` platform service and mirrors the existing
 * platform seams ([com.fuse.feedback.platformHapticsModule],
 * [com.fuse.feedback.platformSoundModule], [com.fuse.data.platformSettingsModule]): a thin
 * `interface` in `commonMain` with per-platform `actual` Koin modules
 * ([platformSharerModule]) wiring a real implementation.
 *
 * Sharing is USER-INITIATED (the player taps Share on the solved overlay) and only PRESENTS
 * the OS chooser — it never auto-sends. Implementations are DEFENSIVE: if nothing can handle
 * the share (no chooser, simulator quirks) they must no-op rather than crash.
 */
interface Sharer {
    /** Presents the OS share sheet for [text] (plain text). Best-effort; never throws. */
    fun share(text: String)
}

/**
 * A no-op [Sharer] for tests/previews and as the safe default when no platform sharer can be
 * built. Drops the share silently — the app never crashes when sharing is unavailable.
 */
object NoOpSharer : Sharer {
    override fun share(text: String) { /* no-op */ }
}

/**
 * Per-platform Koin module binding the [Sharer] `single`, mirroring [platformSoundModule].
 *
 *  - **Android** (`Sharer.android.kt`): an `ACTION_SEND` `text/plain` intent wrapped in a
 *    chooser, launched from the application `Context` with `FLAG_ACTIVITY_NEW_TASK`. Defensive.
 *  - **iOS** (`Sharer.ios.kt`): a `UIActivityViewController` presented from the top-most view
 *    controller of the key window, found defensively. No-ops on the simulator if no window.
 *
 * Registered in `appModules` (see `com.fuse.di.Modules`).
 */
expect val platformSharerModule: Module
