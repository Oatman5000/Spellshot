package net.everlands.moshpit.loadout

import net.everlands.core.framework.Config
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.StarterLoadoutConfig")
data class StarterLoadoutConfig(
    val armor: Map<String, Int> = emptyMap(),
    // Weapons come from the player's selected loadout; anything listed here is
    // handed out *in addition* to it. Left empty so a chosen secondary isn't
    // shadowed by a free M1911.
    val hotbar: Map<String, Int> = emptyMap(),
    val storage: Map<String, Int> = emptyMap(),
    // These four are the default class. They are what createDefaultLoadout()
    // builds, so a player who has never opened the loadout screen still spawns
    // with a full kit -- previously all four were null and they spawned empty.
    // They also backfill any slot holding an item the player has not unlocked.
    @Description("Default primary gun for players to use if none is selected")
    val primaryGun: String? = "AR14",
    @Description("Default secondary gun for players to use if none is selected")
    val secondaryGun: String? = "M1911",
    val melee: String? = "CombatKnife",
    val utility: String? = "FragGrenade"
) {
    @Component
    class Binder : Config<StarterLoadoutConfig>(StarterLoadoutConfig())
}
