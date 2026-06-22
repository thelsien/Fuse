package com.fuse.data

import com.fuse.cosmetics.EquippedCosmetics
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * COS-1 — local persistence for the player's EQUIPPED cosmetics ([EquippedCosmetics]).
 *
 * Only the EQUIPPED choice is persisted; "owned/unlocked" is DERIVED from the achievements
 * record (see `unlockedCosmetics`), so this repo never stores ownership. Same platform
 * [Settings] (via Koin) under its OWN key ([SettingsCosmeticsRepository.KEY] =
 * `fuse.cosmetics.equipped`). A missing/corrupt blob → the defaults (Classic skins), so a
 * fresh player is always valid.
 */
interface CosmeticsRepository {
    /** Persists the [equipped] choice, overwriting any prior value. */
    fun saveEquipped(equipped: EquippedCosmetics)

    /** Loads the saved equipped choice, or the defaults if none/corrupt. */
    fun loadEquipped(): EquippedCosmetics
}

/**
 * COS-1 — [CosmeticsRepository] backed by multiplatform-settings [Settings] + JSON. Lenient so
 * an older blob decodes to the defaulted record.
 */
class SettingsCosmeticsRepository(
    private val settings: Settings,
    private val json: Json = DefaultJson,
) : CosmeticsRepository {

    override fun saveEquipped(equipped: EquippedCosmetics) {
        settings.putString(KEY, json.encodeToString(EquippedCosmetics.serializer(), equipped))
    }

    override fun loadEquipped(): EquippedCosmetics {
        val blob = settings.getStringOrNull(KEY) ?: return EquippedCosmetics()
        return runCatching { json.decodeFromString(EquippedCosmetics.serializer(), blob) }
            .getOrNull() ?: EquippedCosmetics()
    }

    companion object {
        /** Storage key for the equipped-cosmetics blob. Distinct from all other Fuse keys. */
        const val KEY: String = "fuse.cosmetics.equipped"

        /** Lenient JSON: tolerate a slightly newer/older blob; a genuinely bad one → defaults. */
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * COS-1 — a no-op, never-persisting [CosmeticsRepository]: the default for the cosmetics store
 * so tests/previews construct without a real [Settings] ([loadEquipped] returns the defaults,
 * [saveEquipped] does nothing).
 */
object NoOpCosmeticsRepository : CosmeticsRepository {
    override fun saveEquipped(equipped: EquippedCosmetics) = Unit
    override fun loadEquipped(): EquippedCosmetics = EquippedCosmetics()
}
