package net.spellshot.moshpit.loadout

import kotlinx.serialization.Serializable

@Serializable
data class PlayerLoadouts(
    val selected: Int = 0,
    val loadouts: MutableList<Loadout> = mutableListOf(),
)

@Serializable
data class Loadout(
    val primary: String?,
    val secondary: String?,
    val melee: String?,
    val utility: String?,
    /**
     * The gun One in the Chamber issues. Ignored by every other mode.
     *
     * Defaulted, unlike the four above, because loadouts are serialised into player
     * progression: without a default, every loadout saved before this field existed
     * would fail to decode and each player would silently lose theirs on their next
     * login. Null means the player has never opened the row, and OitC falls back.
     */
    val oitc: String? = null,
)
