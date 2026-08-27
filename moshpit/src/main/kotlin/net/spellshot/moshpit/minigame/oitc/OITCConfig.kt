package net.spellshot.moshpit.minigame.oitc

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.spellshot.core.framework.Config
import org.springframework.stereotype.Component

/**
 * Tuning for One in the Chamber.
 *
 * The mode is deliberately austere: one gun, one knife, and a bullet economy
 * that only grows by killing. Everything that could soften that -- reloading,
 * power-ups, killstreak rewards -- is switched off elsewhere in the mode rather
 * than being made configurable, because turning any of them back on stops it
 * being One in the Chamber.
 *
 * [gunItemIds] does not weaken that. It is still one gun, one knife; the player
 * chooses which gun in the loadout menu rather than being handed a fixed one.
 * Nobody carries two, and the choice is made before the match, not during it.
 */
@Serializable
@SerialName("minigame.OITCConfig")
data class OITCConfig(
    @Description(
        "Guns this mode will issue. Which one a player gets is their own choice, made " +
            "in the loadout menu's One in the Chamber row; this list is the whitelist " +
            "that choice is checked against, so a player who picks something not named " +
            "here falls back to the first entry. Each must be a gun with an 'ammo' tag."
    )
    val gunItemIds: List<String> = listOf("Revolver", "M40"),

    @Description("Item given as the fallback melee weapon.")
    val meleeItemId: String = "CombatKnife",

    @Description("Bullets you spawn with, and drop back to every time you die.")
    val startingBullets: Int = 1,

    @Description("Bullets awarded for each kill you get with the gun or the knife.")
    val bulletsPerKill: Int = 1,

    @Description(
        "Most bullets you can bank. Null uses the gun's own magazine size, which " +
            "is what the ammo counter shows as the denominator -- setting this " +
            "higher than that will read as e.g. 6/4 on the HUD."
    )
    val maxBullets: Int? = null,
) {
    @Component
    class Binder : Config<OITCConfig>(OITCConfig())
}
