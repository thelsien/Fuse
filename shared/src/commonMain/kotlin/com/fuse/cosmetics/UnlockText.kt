package com.fuse.cosmetics

/**
 * COS-3 — the PURE unlock-condition → display-text mapping for the collection screen.
 *
 * A locked cosmetic must tell the player HOW to unlock it (an achievement to reach), and there is
 * NO currency anywhere — so the only condition is "reach an achievement". This is a small, pure
 * helper (no Compose) so the copy is unit-testable in commonTest and reused by the screen without
 * literal strings scattered through the UI.
 *
 *  - [UnlockRule.Free] → `null` (free cosmetics are always owned; they never show a lock label).
 *  - [UnlockRule.Achievement] → the per-achievement "how to unlock" copy ([unlockText]).
 *
 * Returning `null` for [UnlockRule.Free] makes the screen logic trivial: "show the lock label iff
 * this is non-null AND the cosmetic is not yet unlocked".
 */
fun unlockConditionText(rule: UnlockRule): String? = when (rule) {
    UnlockRule.Free -> null
    is UnlockRule.Achievement -> unlockText(rule.id)
}

/**
 * COS-3 — the "how to unlock" copy for a gating [AchievementId]. Exhaustive over the enum so a new
 * milestone forces a copy decision here (no silent missing string).
 *
 *  - [AchievementId.REACHED_2048] → "Reach 2048 to unlock".
 */
fun unlockText(id: AchievementId): String = when (id) {
    AchievementId.REACHED_2048 -> "Reach 2048 to unlock"
}

/**
 * COS-3 — the unlock-condition text for a [Cosmetic] (its [Cosmetic.unlock] rule). `null` for a
 * free cosmetic. Convenience over [unlockConditionText] so callers pass the cosmetic directly.
 */
fun Cosmetic.unlockConditionText(): String? = unlockConditionText(unlock)
