package com.fuse.feedback

/**
 * The single, shared definition of which tile values count as a "milestone" worth a
 * celebratory beat — the notable powers of two near/at the win target.
 *
 * One definition, three consumers:
 *  - [HapticsCoordinator] (FEL-4) — a heavier "thunk" when a milestone is reached.
 *  - [SoundCoordinator] (FEL-5) — a milestone sting layered over the merge tone.
 *  - the milestone visual (FEL-6) — a particle burst + screen flash.
 *
 * Keeping all three on the SAME set means the haptic, the sound, and the visual always
 * agree on what a milestone is. Lower tiles (128/256) merge constantly and would make
 * the celebration lose meaning, so they are deliberately excluded.
 */
val MILESTONES: Set<Int> = setOf(512, 1024, 2048)

/**
 * FEL-6 — the PURE milestone-detection decision used to drive the milestone VISUAL.
 *
 * Given the per-merge result values produced by one accepted move (the same
 * `mergedValues` carried by `GameEffect.Moved`), return the HIGHEST milestone the move
 * produced, or `null` if it produced none. Returning the highest means a single move that
 * happens to produce more than one milestone-valued tile celebrates the bigger one (e.g.
 * a 1024 over a concurrent 512), so the burst's intensity matches the proudest result.
 *
 * Pure and Compose-free, so it is unit-tested on JVM and iOS Native and the composable
 * stays a thin renderer over its result.
 *
 * @param mergedValues the `resultingValue` of each merge this move (empty for a pure slide).
 * @return the largest value in [mergedValues] that is in [MILESTONES], or `null`.
 */
fun milestoneReached(mergedValues: List<Int>): Int? =
    mergedValues.filter { it in MILESTONES }.maxOrNull()
