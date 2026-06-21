package com.fuse.feedback

/**
 * FEL-5 — the mute toggle that gates all sound effects.
 *
 * ## Separate from haptics, on purpose
 * Sound and haptics are independent channels a player tunes separately (mute the phone in
 * a meeting but keep the buzz in your hand, or vice-versa). So this is a DISTINCT holder
 * from [HapticsSettings] with its own [soundEnabled] flag — never share one toggle for both.
 *
 * ## Why a holder (and not just a `Boolean`)
 * The story requires a working mute toggle, but the Settings *screen* that flips it is
 * Sprint 4 (`SHL-3`). So FEL-5 plumbs the seam without building the UI: an injectable holder
 * with [soundEnabled] defaulting to `true` (sound ON out of the box). The [SoundCoordinator]
 * reads it on every event and short-circuits when it is `false`, so *one* check mutes
 * everything.
 *
 * ## What SHL-3 will do here
 * SHL-3 will (a) add the Settings UI that flips this flag, and (b) back it with the
 * UIB-6-style `multiplatform-settings` persistence (same pattern as `SettingsGameRepository`
 * and the planned `HapticsSettings` persistence). Either swap this in-memory holder for a
 * `Settings`-backed implementation of the same shape, or keep this class and make
 * [soundEnabled] read/write through `Settings`. The coordinator and platform impls need no
 * change — the gate stays exactly here.
 *
 * @property soundEnabled `true` to allow sound effects; `false` mutes all of them.
 *   Mutable so a future settings toggle can flip it live; defaults to `true`.
 */
class SoundSettings(
    var soundEnabled: Boolean = true,
)
