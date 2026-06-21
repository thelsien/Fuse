package com.fuse.feedback

/**
 * FEL-4 — the toggle seam that gates all haptic feedback.
 *
 * ## Why a holder (and not just a `Boolean`)
 * The story requires haptics to "respect an OS/setting toggle", but the Settings *screen*
 * that flips it is Sprint 4 (`SHL-3`). So FEL-4 plumbs the seam without building the UI:
 * an injectable holder with [hapticsEnabled] defaulting to `true` (haptics ON out of the
 * box). The [HapticsCoordinator] reads it on every event and short-circuits when it is
 * `false`, so *one* check disables every haptic.
 *
 * ## What SHL-3 will do here
 * SHL-3 will (a) add the Settings UI that flips this flag, and (b) back it with the
 * UIB-6-style `multiplatform-settings` persistence (same pattern as `SettingsGameRepository`).
 * Either swap this in-memory holder for a `Settings`-backed implementation of the same
 * shape, or keep this class and make [hapticsEnabled] read/write through `Settings`. The
 * coordinator and platform impls need no change — the gate stays exactly here.
 *
 * For FEL-4 an in-memory, default-on holder is sufficient and is what the Koin graph binds.
 *
 * @property hapticsEnabled `true` to allow haptic feedback; `false` silences all of it.
 *   Mutable so a future settings toggle can flip it live; defaults to `true`.
 */
class HapticsSettings(
    var hapticsEnabled: Boolean = true,
)
