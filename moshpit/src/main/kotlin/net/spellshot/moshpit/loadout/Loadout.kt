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
)
