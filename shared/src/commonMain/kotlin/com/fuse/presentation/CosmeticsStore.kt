package com.fuse.presentation

import com.fuse.cosmetics.Cosmetic
import com.fuse.cosmetics.Cosmetics
import com.fuse.cosmetics.EquippedCosmetics
import com.fuse.cosmetics.PlayerAchievements
import com.fuse.cosmetics.isUnlocked
import com.fuse.cosmetics.unlockedCosmetics
import com.fuse.data.CosmeticsRepository
import com.fuse.data.NoOpCosmeticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * COS-1 — the store that exposes the cosmetics catalog, what's UNLOCKED (derived from
 * achievements), and what's EQUIPPED (persisted), and lets the player equip an owned cosmetic.
 *
 * It is the read seam for COS-2 (which reads [CosmeticsUiState.equipped] → a `FuseColors` /
 * `TileRamp` override fed into `FuseTheme`) and COS-3 (the collection screen rendering the
 * catalog + locked/unlocked + the per-cosmetic unlock text + an Equip CTA).
 *
 * ## State sources
 *  - **Catalog** — the static [Cosmetics.all].
 *  - **Unlocked** — DERIVED from the [achievementsStore]'s [PlayerAchievements] (Free always +
 *    met gates). The store observes that flow ([scope] required), so when the player first
 *    reaches 2048 the gated cosmetic becomes unlocked live, no restart.
 *  - **Equipped** — seeded from [repository] on init and written through on [Equip].
 *
 * ## Intents
 *  - [Equip] — equip a cosmetic, GUARDED: only an UNLOCKED catalog cosmetic can be equipped
 *    (equipping a locked or unknown id is rejected = no-op). The default is always unlocked, so
 *    it is always equippable. The equipped slot is chosen by the cosmetic's `type`.
 *
 * @param achievementsStore the source of the unlock-driving achievements record.
 * @param repository persistence for the equipped choice; defaults to
 *   [NoOpCosmeticsRepository] so tests/previews need no `Settings`.
 * @param scope a coroutine scope used to observe [achievementsStore]'s flow so newly-met
 *   achievements recompute the unlocked set. `null` (the default) means "don't observe" — the
 *   unlocked set is then a one-shot snapshot from the achievements value at construction (handy
 *   for pure/preview construction); the Koin-built singleton passes a real app scope.
 */
class CosmeticsStore(
    private val achievementsStore: AchievementsStore,
    private val repository: CosmeticsRepository = NoOpCosmeticsRepository,
    scope: CoroutineScope? = null,
) {
    private var equipped: EquippedCosmetics = repository.loadEquipped()

    private val _state = MutableStateFlow(project(achievementsStore.state.value))

    /** The cosmetics UI state: catalog + unlocked set + equipped ids. */
    val state: StateFlow<CosmeticsUiState> = _state.asStateFlow()

    init {
        // Recompute the unlocked set whenever achievements change (e.g. reaching 2048 unlocks
        // the gated skin live). When no scope is supplied, the state stays the init snapshot.
        scope?.let { s ->
            achievementsStore.state
                .onEach { achievements -> _state.value = project(achievements) }
                .launchIn(s)
        }
    }

    /** Submits an [intent]. */
    fun accept(intent: CosmeticsIntent) {
        when (intent) {
            is CosmeticsIntent.Equip -> equip(intent.cosmeticId)
        }
    }

    /**
     * Equips [cosmeticId] iff it is a KNOWN, currently-UNLOCKED cosmetic; otherwise a no-op
     * (a locked or unknown id is rejected). Persists the new equipped choice and republishes.
     */
    private fun equip(cosmeticId: String) {
        val achievements = achievementsStore.state.value
        val cosmetic = Cosmetics.byId(cosmeticId) ?: return
        if (!isUnlocked(cosmetic, achievements)) return
        equipped = equipped.equip(cosmetic.type, cosmetic.id)
        repository.saveEquipped(equipped)
        _state.value = project(achievements)
    }

    /** Projects catalog + the unlocked set for [achievements] + the equipped ids. */
    private fun project(achievements: PlayerAchievements): CosmeticsUiState = CosmeticsUiState(
        catalog = Cosmetics.all,
        unlocked = unlockedCosmetics(achievements).map { it.id }.toSet(),
        equipped = equipped,
    )
}

/** COS-1 — the intents the cosmetics UI (COS-3) can submit. */
sealed interface CosmeticsIntent {
    /** Equip the cosmetic with [cosmeticId] (guarded: only an unlocked one is accepted). */
    data class Equip(val cosmeticId: String) : CosmeticsIntent
}

/**
 * COS-1 — the immutable cosmetics UI projection.
 *
 * @property catalog every cosmetic in display order ([Cosmetics.all]).
 * @property unlocked the ids currently OWNED (derived from achievements).
 * @property equipped the persisted equipped choice (one id per [com.fuse.cosmetics.CosmeticType]).
 */
data class CosmeticsUiState(
    val catalog: List<Cosmetic> = Cosmetics.all,
    val unlocked: Set<String> = emptySet(),
    val equipped: EquippedCosmetics = EquippedCosmetics(),
) {
    /** Is [cosmeticId] currently owned? */
    fun isUnlocked(cosmeticId: String): Boolean = cosmeticId in unlocked

    /** Is [cosmeticId] the one currently equipped in its slot? */
    fun isEquipped(cosmetic: Cosmetic): Boolean = equipped.idFor(cosmetic.type) == cosmetic.id
}
