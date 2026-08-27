package net.spellshot.moshpit.minigame.hardpoint

import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam

/**
 * Constant names are the arena spawn-marker keys ("team:ODA:spawn:N"), resolved
 * through team.id by TeamSpawnStrategy -- renaming them breaks team spawns.
 * The visible label is [displayName]. Colours must match Hextech.ui.
 */
enum class HardpointTeamDef(
    val displayName: String,
    val particleColor: String,
    val color: String,
    val armorPrefix: String
) {
    ODA("Blue", "Hardpoint_Blue", "#00D4FF", "Blue"),
    SENTINEL("Red", "Hardpoint_Red", "#FF4D5A", "Red"),
}

class HardpointTeam(
    val def: HardpointTeamDef,
    var points: Int = 0
) : MiniGameTeam(def.name, def.displayName)
