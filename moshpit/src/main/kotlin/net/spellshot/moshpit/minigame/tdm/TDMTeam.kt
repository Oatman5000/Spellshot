package net.spellshot.moshpit.minigame.tdm

import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam

/**
 * The constant names are NOT display text -- they are the spawn-marker keys
 * baked into each arena ("team:ODA:spawn:N" / "team:SENTINEL:spawn:N", looked up
 * by TeamSpawnStrategy via team.id). Renaming them would break team spawns on
 * every map, so the visible label lives in [displayName] instead.
 *
 * Colours are written onto the HUD at runtime and must match Hextech.ui.
 */
enum class TDMTeamDef(val displayName: String, val color: String, val armorPrefix: String) {
    ODA("Blue", "#00D4FF", "Blue"),
    SENTINEL("Red", "#FF4D5A", "Red"),
}

class TDMTeam(
    val def: TDMTeamDef,
    var points: Int = 0
) : MiniGameTeam(def.name, def.displayName)
