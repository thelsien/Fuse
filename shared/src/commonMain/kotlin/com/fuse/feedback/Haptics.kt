package com.fuse.feedback

/**
 * FEL-4 — a thin, intent-named haptic feedback service.
 *
 * The method names describe the *meaning* of the feedback, not the physical waveform,
 * so the decision logic ([HapticsCoordinator]) stays platform-agnostic and the platform
 * implementations are free to map each intent onto whatever native primitive feels right
 * (Android `VibrationEffect` / iOS `UIFeedbackGenerator`).
 *
 * It is deliberately an interface with no state and no return values: the platform
 * impls are thin, fire-and-forget side effects (see `platformHapticsModule`), and a
 * recording [FakeHaptics] makes the *decision* (which call, when) trivially testable in
 * `commonTest` without touching any platform code.
 *
 *  - [tick] — a light tap, fired once per move that produced at least one merge.
 *  - [thunk] — a heavier impact, fired when a move first reaches a milestone tile
 *    (see [HapticsCoordinator.MILESTONES]).
 *  - [buzz] — a distinct error pattern, fired on an invalid / blocked (no-op) move.
 */
interface Haptics {
    /** Light tap — one merge happened this move. */
    fun tick()

    /** Heavier impact — a notable milestone tile was reached this move. */
    fun thunk()

    /** Distinct error buzz — the move was blocked (no-op). */
    fun buzz()
}

/**
 * A [Haptics] that does nothing.
 *
 * Used as the default binding wherever a real platform vibrator is unavailable or
 * unwanted (tests, previews, and any code path that constructs without Koin). It keeps
 * the rest of the system honest: haptics are always *invoked* through the same seam, and
 * "no feedback" is a real implementation rather than a scattered set of null checks.
 */
object NoOpHaptics : Haptics {
    override fun tick() {}
    override fun thunk() {}
    override fun buzz() {}
}
