package com.fuse.ads

/**
 * ADS-0 (Sprint 8 spike) — the feature flag that gates the spike's debug ad trigger.
 *
 * The whole point of the spike is to PROVE a test ad renders without destabilising the real app,
 * so the only entry point to the ad code is a debug-only "Show test ad" button in Settings, shown
 * ONLY when [enabled] is true. It is wired to NO real placement (game-over/replay is ADS-2/4).
 *
 * Default ON for the spike branch so the ad can be triggered + verified on a device. ADS-1+ will
 * replace this with proper build-time gating / real placements; until then a single flag keeps the
 * trigger out of the way and trivially removable.
 */
object AdsDebug {
    /** Whether the debug "Show test ad" trigger is shown in Settings. */
    const val enabled: Boolean = true
}
