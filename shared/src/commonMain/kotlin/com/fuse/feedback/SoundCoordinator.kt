package com.fuse.feedback

import kotlin.math.log2

/**
 * FEL-5 — the PURE decision: given the outcome of one move, decide which [Sound] call(s)
 * to make (and at what pitch). All "which tone/sting for which event, at what pitch" logic
 * lives here (not in the platform impls), so it is fully unit-tested in `commonTest` with a
 * recording [FakeSound] across JVM and iOS Native. This mirrors [HapticsCoordinator].
 *
 * ## The mapping (acceptance criteria)
 *  - **A merge happened** (`mergedValues` non-empty) → [Sound.mergeTone] at the pitch of
 *    the move's **highest** merged value. The tone RISES with the value ([mergePitch]),
 *    giving the audible "climbing" feeling as you build bigger tiles. One tone per move
 *    (the move is the unit of feedback, exactly like haptics' single tick), and we pick the
 *    highest merge so a move that produces a big tile sounds appropriately high even if it
 *    also produced a small one.
 *  - **A milestone tile was reached this move** (any merged result in [MILESTONES]) →
 *    [Sound.milestoneSting] *in addition to* the merge tone. Unlike haptics (where the heavy
 *    thunk REPLACES the light tick because two overlapping vibrations muddy the feel),
 *    audio layers cleanly: the climbing tone still plays for the merge, and the sting rings
 *    *on top* to mark the milestone. So a milestone move = tone + sting. (Documented
 *    divergence from FEL-4's thunk-instead-of-tick: two simultaneous sounds are pleasant; two
 *    simultaneous vibrations are not.)
 *  - **The win** (`justWon`) → [Sound.winSting] (instead of a plain milestone sting). The
 *    merge tone still plays, then the win sting rings — a distinct, bigger cue than the
 *    milestone sting. `justWon` takes precedence over the milestone sting so first-2048 plays
 *    the *win* sting, not the milestone one (it is always also a 2048 milestone).
 *  - **A no-merge accepted move** (pure slide) → silence (no tone). Matches haptics.
 *  - **A blocked move** → no sound. The block already has a haptic buzz; an error *sound*
 *    every failed swipe would be grating, so FEL-5 intentionally has no blocked cue. (There
 *    is therefore no `onBlocked` here — the screen simply doesn't call sound for a block.)
 *
 * ## The gate
 * [onMove] first consults [settings]; when [SoundSettings.soundEnabled] is `false` it returns
 * immediately and **nothing plays** — the single seam that mutes all sound (see
 * [SoundSettings] for the SHL-3 persistence/UI plan).
 *
 * @param sound the platform audio sink (real on device, [FakeSound] in tests).
 * @param settings the mute gate.
 */
class SoundCoordinator(
    private val sound: Sound,
    private val settings: SoundSettings,
) {
    /**
     * Decide audio for one ACCEPTED move.
     *
     * @param mergedValues the `resultingValue` of each merge this move (empty if none).
     * @param justWon `true` iff this move first reached the win target.
     */
    fun onMove(mergedValues: List<Int>, justWon: Boolean) {
        if (!settings.soundEnabled) return
        if (mergedValues.isEmpty()) return // pure slide: silent, like haptics

        // The climbing tone, pitched to the highest tile this move produced.
        sound.mergeTone(mergePitch(mergedValues.max()))

        // Layer a sting on top for a milestone / the win. justWon wins over a plain
        // milestone so first-2048 plays the celebratory win sting, not the milestone one.
        when {
            justWon -> sound.winSting()
            mergedValues.any { it in MILESTONES } -> sound.milestoneSting()
            else -> {}
        }
    }

    companion object {
        /**
         * The tile values that ring a [Sound.milestoneSting]. Aliases the single shared
         * [com.fuse.feedback.MILESTONES] set (the same notable powers of two used by haptics
         * and the FEL-6 visual), kept in one place so the channels can never drift. 2048 is
         * included for symmetry, though first-2048 plays the *win* sting via `justWon`; a later
         * customised win target below 2048 still stings on 2048.
         */
        val MILESTONES: Set<Int> = com.fuse.feedback.MILESTONES

        /**
         * The lowest tile value a merge can produce (two 2-tiles → 4), the floor of the pitch
         * range. [mergePitch] maps this to `0f`.
         */
        const val MIN_MERGE_VALUE: Int = 4

        /**
         * The tile value mapped to the TOP of the pitch range (`1f`). 2048 is the classic win
         * target and the highest milestone; merges above it (2048 game continues to 4096+)
         * clamp at `1f` so the pitch never runs away.
         */
        const val MAX_MERGE_VALUE: Int = 2048

        private val MIN_EXP: Float = log2(MIN_MERGE_VALUE.toFloat())  // log2(4)   = 2
        private val MAX_EXP: Float = log2(MAX_MERGE_VALUE.toFloat())  // log2(2048) = 11

        /**
         * PURE pitch mapping — the testable heart of "rises in pitch with the tile value".
         *
         * Tile values are powers of two, so we map on a **log2 (musical / exponent) scale**:
         * each doubling of the value is an equal step up in pitch, which is what reads as a
         * steady "climb" to the ear (linear-in-value would bunch all the low tiles together
         * and explode at the top). The exponent `log2(value)` runs 2 (=4) … 11 (=2048); we
         * normalise that span to `[0f, 1f]`:
         *
         * ```
         * pitch(value) = (log2(value) - log2(4)) / (log2(2048) - log2(4))
         * ```
         *
         * Properties (pinned by tests):
         *  - **Monotonic**: strictly increasing in `value` over `[4, 2048]` (each doubling
         *    adds a fixed `1 / (MAX_EXP - MIN_EXP)` step).
         *  - **Bounded**: clamped to `[0f, 1f]`; values ≤ 4 give `0f`, values ≥ 2048 give `1f`.
         *  - **Deterministic & pure**: same input ⇒ same output, no state, no platform calls.
         *
         * The platform [Sound] impl turns this normalised step into a concrete frequency or
         * playback rate; keeping the *mapping* here (not in the impls) is what makes the
         * "climbing" rule unit-testable on every target.
         *
         * @param value a merged tile value (a power of two ≥ 4).
         * @return a normalised pitch in `[0f, 1f]`, increasing with [value].
         */
        fun mergePitch(value: Int): Float {
            val safe = value.coerceAtLeast(MIN_MERGE_VALUE).coerceAtMost(MAX_MERGE_VALUE)
            val exp = log2(safe.toFloat())
            return ((exp - MIN_EXP) / (MAX_EXP - MIN_EXP)).coerceIn(0f, 1f)
        }
    }
}
