package com.fuse.cosmetics

import kotlinx.serialization.Serializable

/**
 * COS-1 — the cosmetics data model: tile skins / board themes that the player can equip,
 * unlocked either for FREE or by hitting an in-game ACHIEVEMENT (no currency anywhere).
 *
 * This story ships the MODEL + the unlock rules + the persisted owned/equipped state and the
 * achievements record that drives unlocking. It deliberately does NOT touch rendering: a
 * [Cosmetic] only carries a stable [Cosmetic.styleId] handle so a later story can resolve a
 * cosmetic → a concrete palette/tile-ramp variant. The wiring is:
 *  - **COS-2** reads the equipped cosmetic ids ([EquippedCosmetics]) and maps [styleId] →
 *    a `FuseColors` / `TileRamp` override fed into `FuseTheme` (the same override seam as the
 *    existing `reducedMotion` / `colorblind` flags).
 *  - **COS-3** builds the collection screen from [Cosmetics.all] + the unlocked set + the
 *    equipped ids + each cosmetic's [UnlockRule] (for the "how to unlock" text + an Equip CTA).
 *  - **COS-4** fills the REAL art (palettes/ramps) behind each non-default [styleId].
 */

/** COS-1 — which slot a cosmetic occupies. One equipped item per type. */
enum class CosmeticType {
    /** Recolors the numbered tiles (resolves to a `TileRamp` variant in COS-2/COS-4). */
    TILE_SKIN,

    /** Recolors the board background/grid (resolves to `FuseColors` fields in COS-2/COS-4). */
    BOARD_THEME,
}

/**
 * COS-1 — the stable identifier of an achievement/milestone that can gate a cosmetic.
 *
 * An enum (not a free string) so the unlock rules are exhaustive and a typo can't silently
 * create an un-meetable gate. Extensible: future milestones (e.g. a streak length, a no-undo
 * solve) add a constant here + a field on [PlayerAchievements] + a branch in [isUnlocked].
 */
enum class AchievementId {
    /** The player has formed the 2048 tile in Classic at least once. */
    REACHED_2048,
}

/**
 * COS-1 — how a [Cosmetic] becomes owned. Either always-available or gated by an achievement.
 * A sealed hierarchy (not a boolean) so future gates (multiple achievements, future
 * non-achievement conditions) slot in without churning callers.
 */
sealed interface UnlockRule {
    /** Always owned — the default skins and any free placeholders. */
    data object Free : UnlockRule

    /** Owned once the [id] achievement has been earned. Auto-unlocks (no purchase). */
    data class Achievement(val id: AchievementId) : UnlockRule
}

/**
 * COS-1 — one equippable cosmetic.
 *
 * @property id stable storage id (persisted in [EquippedCosmetics]); never reused/renamed.
 * @property name human-readable label for the COS-3 collection screen.
 * @property type which slot it fills ([CosmeticType]); one equipped per type.
 * @property unlock how it's owned ([UnlockRule]).
 * @property styleId the COS-2/COS-4 styling handle: a stable key a later story resolves to a
 *   concrete `FuseColors`/`TileRamp` variant. The DEFAULT entries use [Cosmetics.DEFAULT_STYLE]
 *   (the app's current palette/ramp); non-default ids are placeholders until COS-4 fills the art.
 */
@Serializable
data class Cosmetic(
    val id: String,
    val name: String,
    val type: CosmeticType,
    val unlock: UnlockRule,
    val styleId: String,
)

/**
 * COS-1 — the static cosmetics catalog (registry).
 *
 * Holds the two always-on DEFAULT entries (one per [CosmeticType], [UnlockRule.Free], always
 * owned + equippable), at least one ACHIEVEMENT-gated entry (the 2048-reward tile skin), plus
 * a couple of free placeholders. The actual colors are COS-4; here every entry only references
 * a [Cosmetic.styleId] hook.
 */
object Cosmetics {
    /** The id of the default TILE_SKIN — the app's current tile ramp; always owned/equipped. */
    const val DEFAULT_TILE_SKIN: String = "tile.default"

    /** The id of the default BOARD_THEME — the app's current board palette; always owned. */
    const val DEFAULT_BOARD_THEME: String = "board.default"

    /**
     * The styling handle of the DEFAULT entries: resolve to the app's CURRENT
     * `FuseColors`/`TileRamp` (i.e. "no override"). COS-2 treats this as the identity style.
     */
    const val DEFAULT_STYLE: String = "default"

    /** Default tile skin — the app's current tiles. Always owned, the equip fallback. */
    val DefaultTileSkin: Cosmetic = Cosmetic(
        id = DEFAULT_TILE_SKIN,
        name = "Classic Tiles",
        type = CosmeticType.TILE_SKIN,
        unlock = UnlockRule.Free,
        styleId = DEFAULT_STYLE,
    )

    /** Default board theme — the app's current board. Always owned, the equip fallback. */
    val DefaultBoardTheme: Cosmetic = Cosmetic(
        id = DEFAULT_BOARD_THEME,
        name = "Classic Board",
        type = CosmeticType.BOARD_THEME,
        unlock = UnlockRule.Free,
        styleId = DEFAULT_STYLE,
    )

    /**
     * The 2048 milestone reward — a tile skin unlocked by forming the 2048 tile in Classic.
     * This is the acceptance-criterion "at least one achievement-gated cosmetic". Its art is
     * a COS-4 concern; here it is a placeholder [styleId].
     */
    val GoldRushTileSkin: Cosmetic = Cosmetic(
        id = "tile.goldRush",
        name = "Gold Rush",
        type = CosmeticType.TILE_SKIN,
        unlock = UnlockRule.Achievement(AchievementId.REACHED_2048),
        styleId = "goldRush",
    )

    /** A free placeholder tile skin (art is COS-4) so the collection isn't a single row. */
    val MidnightTileSkin: Cosmetic = Cosmetic(
        id = "tile.midnight",
        name = "Midnight",
        type = CosmeticType.TILE_SKIN,
        unlock = UnlockRule.Free,
        styleId = "midnight",
    )

    /** A free placeholder board theme (art is COS-4). */
    val OceanBoardTheme: Cosmetic = Cosmetic(
        id = "board.ocean",
        name = "Ocean",
        type = CosmeticType.BOARD_THEME,
        unlock = UnlockRule.Free,
        styleId = "ocean",
    )

    /** The whole catalog, in display order. The first of each type is its DEFAULT. */
    val all: List<Cosmetic> = listOf(
        DefaultTileSkin,
        MidnightTileSkin,
        GoldRushTileSkin,
        DefaultBoardTheme,
        OceanBoardTheme,
    )

    /** The default cosmetic for a [type] — its always-owned, equip-fallback entry. */
    fun default(type: CosmeticType): Cosmetic = when (type) {
        CosmeticType.TILE_SKIN -> DefaultTileSkin
        CosmeticType.BOARD_THEME -> DefaultBoardTheme
    }

    /** Looks up a cosmetic by [id], or `null` if no catalog entry has it. */
    fun byId(id: String): Cosmetic? = all.firstOrNull { it.id == id }
}

/**
 * COS-1 — the persisted record of which achievements the player has earned. Extensible: each
 * new milestone adds a `false`-defaulting flag (so an OLD blob decodes — unknown-key tolerant
 * + defaulted) and a branch in [isUnlocked].
 *
 * @property reached2048 set once the player first forms the 2048 tile in Classic (see the
 *   `GameEffect.Won` hook in `AchievementsStore`). Idempotent: setting it again is a no-op.
 */
@Serializable
data class PlayerAchievements(
    val reached2048: Boolean = false,
)

/**
 * COS-1 — has the player earned [id]? The pure projection of [PlayerAchievements] → a boolean
 * per [AchievementId], so [isUnlocked] never has to know the record's field layout.
 */
fun PlayerAchievements.has(id: AchievementId): Boolean = when (id) {
    AchievementId.REACHED_2048 -> reached2048
}

/**
 * COS-1 — the pure unlock rule: is [cosmetic] owned given [achievements]?
 *  - [UnlockRule.Free] → always `true`.
 *  - [UnlockRule.Achievement] → `true` iff that achievement is earned.
 * Owned/unlocked is fully DERIVED from achievements (never persisted separately).
 */
fun isUnlocked(cosmetic: Cosmetic, achievements: PlayerAchievements): Boolean =
    when (val rule = cosmetic.unlock) {
        UnlockRule.Free -> true
        is UnlockRule.Achievement -> achievements.has(rule.id)
    }

/** COS-1 — every catalog cosmetic currently owned given [achievements] (Free + met gates). */
fun unlockedCosmetics(achievements: PlayerAchievements): List<Cosmetic> =
    Cosmetics.all.filter { isUnlocked(it, achievements) }

/**
 * COS-1 — the player's currently EQUIPPED cosmetic per slot (the only persisted choice; owned
 * is derived). Defaults to the always-owned defaults so a fresh player is always valid.
 *
 * @property tileSkinId the equipped TILE_SKIN id (default [Cosmetics.DEFAULT_TILE_SKIN]).
 * @property boardThemeId the equipped BOARD_THEME id (default [Cosmetics.DEFAULT_BOARD_THEME]).
 */
@Serializable
data class EquippedCosmetics(
    val tileSkinId: String = Cosmetics.DEFAULT_TILE_SKIN,
    val boardThemeId: String = Cosmetics.DEFAULT_BOARD_THEME,
) {
    /** The equipped id for [type]. */
    fun idFor(type: CosmeticType): String = when (type) {
        CosmeticType.TILE_SKIN -> tileSkinId
        CosmeticType.BOARD_THEME -> boardThemeId
    }

    /** This record with [type]'s slot set to [cosmeticId] (the other slot unchanged). */
    fun equip(type: CosmeticType, cosmeticId: String): EquippedCosmetics = when (type) {
        CosmeticType.TILE_SKIN -> copy(tileSkinId = cosmeticId)
        CosmeticType.BOARD_THEME -> copy(boardThemeId = cosmeticId)
    }
}
