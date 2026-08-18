package net.everlands.moshpit.minigame.gungame

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.framework.Config
import org.springframework.stereotype.Component

/**
 * Tuning for Gun Game.
 *
 * The ladder is ordered worst-to-best. Tier 0 is what everyone spawns holding,
 * and each kill moves you one step up it; dying puts you straight back to tier 0.
 *
 * Item ids must match the filenames under Server/Item/Items/Weapons -- note the
 * Thompson's id is "Thompson" even though its model file is Tomy.blockymodel.
 *
 * This is every firearm in the mod except the M40, which stays out for the same
 * reason it is commented out of ProgressionConfig. Re-add "M40" below to bring
 * it back; nothing else needs changing, the ladder length is read from the list.
 */
@Serializable
@SerialName("minigame.GunGameConfig")
data class GunGameConfig(
    @Description("Weapon ladder, worst first. Tier 0 is the spawn weapon.")
    val ladder: List<String> = listOf(
        "CombatKnife",    // 1  melee
        "M1911",          // 2  10 rnd sidearm
        "Revolver",       // 3   4 rnd sidearm
        "AR14",           // 4  30 rnd rifle
        "Thompson",       // 5  25 rnd SMG
        "FAMP10",         // 6  15 rnd SMG
        "DoubleBarrel",   // 7   2 rnd shotgun
        "Remington870",   // 8   5 rnd shotgun
        "Famas",          // 9  30 rnd rifle
    ),

    @Description("Announce each promotion to the promoted player with a title card.")
    val announcePromotions: Boolean = true,

    @Description("Broadcast to everyone when a player reaches the final weapon.")
    val announceFinalTier: Boolean = true,
) {
    /** Highest valid tier index. */
    val topTier: Int get() = (ladder.size - 1).coerceAtLeast(0)

    /** Item id for a tier, clamped so an out-of-range tier can never crash a spawn. */
    fun weaponFor(tier: Int): String? = ladder.getOrNull(tier.coerceIn(0, topTier))

    @Component
    class Binder : Config<GunGameConfig>(GunGameConfig())
}
