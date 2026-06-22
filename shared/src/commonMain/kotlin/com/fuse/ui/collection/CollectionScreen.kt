package com.fuse.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuse.cosmetics.Cosmetic
import com.fuse.cosmetics.CosmeticType
import com.fuse.cosmetics.Cosmetics
import com.fuse.cosmetics.unlockConditionText
import com.fuse.presentation.CosmeticsIntent
import com.fuse.presentation.CosmeticsStore
import com.fuse.presentation.CosmeticsUiState
import com.fuse.ui.theme.CosmeticStyles
import com.fuse.ui.theme.FuseColors
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * COS-3 — the **Collection** screen: browse cosmetics, see owned vs locked (with the unlock
 * CONDITION — no currency anywhere), and equip an owned one.
 *
 * ## Two layers (testability), mirroring Home/Settings
 *  - A thin **stateful wrapper** [CollectionScreen] (the one `App()`/the nav graph calls) that
 *    resolves the [CosmeticsStore] from Koin (overridable so UI tests inject a store over
 *    `MapSettings`/a test state), collects its [CosmeticsStore.state], and binds Equip to
 *    `store.accept(CosmeticsIntent.Equip(id))`.
 *  - A **presentational** [CollectionScreen] overload that takes the immutable [CosmeticsUiState]
 *    plus an `onEquip(id)` and an [onBack], so it renders with no Koin and no coroutines.
 *
 * ## Layout
 * A scrollable column with a single "‹ Home" back affordance (the SHL-2 pattern — system back on
 * Android, this in-screen button on iOS), a title, then one SECTION per [CosmeticType]
 * ([Cosmetics.grouped]: Tile Skins, then Board Themes). Each section is a header + a stack of
 * per-cosmetic cards.
 *
 * ## Per-cosmetic card
 *  - a **preview swatch** built from COS-2's resolution ([CosmeticStyles]): a TILE_SKIN shows a few
 *    sample tiles via `tileRamp(styleId).forValue(v)` for 2/8/64/512/2048; a BOARD_THEME shows a
 *    mini board-bg swatch via `boardColors(styleId, base)`. Token-styled; no literal hex.
 *  - the **name**, and a **state indicator**: **Equipped** (current), **Equip** (owned, not
 *    equipped → an action), or **Locked** with the unlock-condition text
 *    ([com.fuse.cosmetics.unlockConditionText], e.g. "Reach 2048 to unlock"). A locked card is NOT
 *    equippable (no Equip action). Tapping Equip on an owned card → `Equip(id)`; because COS-2
 *    wired the live theme, returning to the board shows the new skin/theme.
 *
 * ## Auto-unlock (no screen-specific logic)
 * The store's `unlocked` set is DERIVED from achievements and updates live, so a previously-locked
 * cosmetic (e.g. `tile.goldRush` after reaching 2048 in Classic) flips to owned/equippable simply
 * because [CosmeticsUiState.isUnlocked] returns true — this screen only reflects that flag.
 *
 * @param onBack navigate back to Home (nav `popBackStack`).
 */
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cosmeticsStore: CosmeticsStore = koinInject(),
) {
    val state by cosmeticsStore.state.collectAsState()
    CollectionScreen(
        state = state,
        onEquip = { id -> cosmeticsStore.accept(CosmeticsIntent.Equip(id)) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * COS-3 — the **presentational** Collection screen (value-driven; no Koin, no coroutines).
 *
 * @param state the cosmetics UI projection (catalog + unlocked set + equipped ids).
 * @param onEquip invoked with a cosmetic id when its **Equip** action is tapped (only rendered for
 *   owned, not-equipped cosmetics; the store guards anyway).
 * @param onBack navigate back to Home.
 */
@Composable
fun CollectionScreen(
    state: CosmeticsUiState,
    onEquip: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .testTag(CollectionScreenTags.ROOT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: a single back affordance, coherent with the nav back stack (SHL-2 pattern).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(CollectionScreenTags.BACK),
            ) {
                Text("‹ Home", style = FuseTheme.type.headingM.copy(color = c.text))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Collection",
                style = FuseTheme.type.titleL.copy(color = c.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CollectionScreenTags.TITLE),
            )

            Spacer(Modifier.heightIn(min = 8.dp))

            Cosmetics.grouped().forEach { (type, items) ->
                Spacer(Modifier.heightIn(min = 16.dp))
                Text(
                    text = sectionTitle(type),
                    style = FuseTheme.type.headingM.copy(color = c.sub),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CollectionScreenTags.sectionTag(type)),
                )
                Spacer(Modifier.heightIn(min = 10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items.forEach { cosmetic ->
                        CosmeticCard(
                            cosmetic = cosmetic,
                            unlocked = state.isUnlocked(cosmetic.id),
                            equipped = state.isEquipped(cosmetic),
                            onEquip = { onEquip(cosmetic.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

/** The header copy for a section. */
private fun sectionTitle(type: CosmeticType): String = when (type) {
    CosmeticType.TILE_SKIN -> "Tile Skins"
    CosmeticType.BOARD_THEME -> "Board Themes"
}

/**
 * One cosmetic card: preview swatch + name + a state indicator (Equipped / Equip / Locked).
 * Locked cards show the unlock-condition text and expose NO Equip action.
 */
@Composable
private fun CosmeticCard(
    cosmetic: Cosmetic,
    unlocked: Boolean,
    equipped: Boolean,
    onEquip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    // The unlock-condition copy (null for Free cosmetics, which are never locked).
    val condition = cosmetic.unlockConditionText()
    val cd = when {
        equipped -> "${cosmetic.name}, equipped"
        unlocked -> "${cosmetic.name}, owned"
        else -> "${cosmetic.name}, locked. ${condition ?: ""}".trim()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, if (equipped) c.accent else c.line, shape)
            .padding(14.dp)
            .testTag(CollectionScreenTags.cardTag(cosmetic.id))
            .semantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CosmeticPreview(cosmetic = cosmetic)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cosmetic.name,
                style = FuseTheme.type.headingM.copy(color = c.text),
            )
            Spacer(Modifier.heightIn(min = 4.dp))
            when {
                equipped -> Text(
                    text = "Equipped",
                    style = FuseTheme.type.caption.copy(color = c.good),
                    modifier = Modifier.testTag(CollectionScreenTags.equippedTag(cosmetic.id)),
                )
                unlocked -> Text(
                    text = "Owned",
                    style = FuseTheme.type.caption.copy(color = c.sub),
                )
                else -> Text(
                    // The unlock CONDITION (no currency): e.g. "Reach 2048 to unlock".
                    text = condition ?: "Locked",
                    style = FuseTheme.type.caption.copy(color = c.sub),
                    modifier = Modifier.testTag(CollectionScreenTags.lockedTag(cosmetic.id)),
                )
            }
        }

        // Trailing state action: Equip (owned, not equipped), nothing for equipped/locked.
        when {
            equipped -> Unit
            unlocked -> EquipButton(
                onClick = onEquip,
                tag = CollectionScreenTags.equipTag(cosmetic.id),
            )
            else -> Unit // locked → no action (not equippable).
        }
    }
}

/** A small token-styled "Equip" pill for an owned, not-equipped cosmetic. */
@Composable
private fun EquipButton(onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    val c = FuseTheme.colors
    Box(
        modifier = modifier
            .clip(FuseTheme.shapes.pill)
            .background(c.accentSoft)
            .border(1.dp, c.line, FuseTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Equip",
            style = FuseTheme.type.caption.copy(color = c.text),
        )
    }
}

/**
 * The preview swatch for a cosmetic, built from COS-2's style resolution:
 *  - TILE_SKIN → a row of sample tiles for a handful of values (2/8/64/512/2048), each filled with
 *    `tileRamp(styleId).forValue(v).bg`.
 *  - BOARD_THEME → a mini board-bg swatch using `boardColors(styleId, base).boardBg`/`.card2`.
 */
@Composable
private fun CosmeticPreview(cosmetic: Cosmetic, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.testTag(CollectionScreenTags.previewTag(cosmetic.id)),
    ) {
        when (cosmetic.type) {
            CosmeticType.TILE_SKIN -> TileSkinPreview(cosmetic.styleId)
            CosmeticType.BOARD_THEME -> BoardThemePreview(cosmetic.styleId)
        }
    }
}

/** A row of sample tiles showing the skin's ramp at representative values. */
@Composable
private fun TileSkinPreview(styleId: String, modifier: Modifier = Modifier) {
    val ramp = CosmeticStyles.tileRamp(styleId)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SAMPLE_TILE_VALUES.forEach { v ->
            val colors = ramp.forValue(v)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(FuseTheme.shapes.chip)
                    .background(colors.bg),
            )
        }
    }
}

/** A mini board swatch: the board background with a couple of empty-cell tints. */
@Composable
private fun BoardThemePreview(styleId: String, modifier: Modifier = Modifier) {
    val colors: FuseColors = CosmeticStyles.boardColors(styleId, FuseTheme.colors)
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(FuseTheme.shapes.chip)
            .background(colors.boardBg)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(FuseTheme.shapes.chip)
                        .background(colors.card2),
                )
            }
        }
    }
}

/** The representative tile values shown in a TILE_SKIN preview swatch. */
private val SAMPLE_TILE_VALUES: List<Int> = listOf(2, 8, 64, 512, 2048)

/** Stable test tags so UI tests target Collection nodes without depending on copy. */
object CollectionScreenTags {
    const val ROOT: String = "collection_screen"
    const val TITLE: String = "collection_title"
    const val BACK: String = "collection_back"

    /** A per-type section header (e.g. Tile Skins / Board Themes). */
    fun sectionTag(type: CosmeticType): String = "collection_section_${type.name}"

    /** A per-cosmetic card. */
    fun cardTag(id: String): String = "collection_card_$id"

    /** A per-cosmetic preview swatch. */
    fun previewTag(id: String): String = "collection_preview_$id"

    /** The per-cosmetic "Equip" action (owned, not equipped). */
    fun equipTag(id: String): String = "collection_equip_$id"

    /** The per-cosmetic "Equipped" indicator (currently equipped). */
    fun equippedTag(id: String): String = "collection_equipped_$id"

    /** The per-cosmetic locked label (the unlock-condition text). */
    fun lockedTag(id: String): String = "collection_locked_$id"
}
