package net.everlands.moshpit.minigame.gungame

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.framework.Config
import net.everlands.moshpit.minigame.engine.KillWeapon
import org.springframework.stereotype.Component

/**
 * Tuning for Gun Game.
 *
 * The ladder is ordered worst-to-best. Tier 0 is what everyone spawns holding.
 *
 * The knife is NOT on the ladder -- it is carried permanently alongside whatever tier
 * weapon you are on, and killing with it fully resets your victim.
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
        "M1911",          //  1  10 rnd sidearm
        "Revolver",       //  2   4 rnd sidearm
        "AR14",           //  3  30 rnd rifle
        "Thompson",       //  4  25 rnd SMG
        "FAMP10",         //  5  15 rnd SMG
        "DoubleBarrel",   //  6   2 rnd shotgun
        "Remington870",   //  7   5 rnd shotgun
        "Famas",          //  8  30 rnd rifle
        "ImpactGrenade",  //  9  detonates on contact
        "FragGrenade",    // 10  timed fuse -- the last rung
    ),

    @Description("Always carried alongside the tier weapon, in the second slot. Null to disable.")
    val sidearmItemId: String? = KNIFE_ITEM_ID,

    @Description("How many grenades to issue on a grenade tier, refreshed on every spawn.")
    val grenadesPerLife: Int = 8,

    @Description("Announce each promotion to the promoted player with a title card.")
    val announcePromotions: Boolean = true,

    @Description("Broadcast to everyone when a player reaches the final weapon.")
    val announceFinalTier: Boolean = true,
) {
    /** Highest valid tier index. */
    val topTier: Int get() = (ladder.size - 1).coerceAtLeast(0)

    /** Item id for a tier, clamped so an out-of-range tier can never crash a spawn. */
    fun weaponFor(tier: Int): String? = ladder.getOrNull(tier.coerceIn(0, topTier))

    /**
     * The explosive a tier requires a kill with, or null if it is a firearm tier.
     *
     * Keyed off the item id rather than the tier index so reordering or extending the
     * ladder needs no other change.
     */
    fun explosiveFor(tier: Int): KillWeapon? = when (weaponFor(tier)) {
        IMPACT_GRENADE_ITEM_ID -> KillWeapon.IMPACT_GRENADE
        FRAG_GRENADE_ITEM_ID -> KillWeapon.FRAG_GRENADE
        else -> null
    }

    /** True if this tier issues grenades rather than a firearm. */
    fun isExplosiveTier(tier: Int): Boolean = explosiveFor(tier) != null

    companion object {
        const val KNIFE_ITEM_ID = "CombatKnife"
        const val IMPACT_GRENADE_ITEM_ID = "ImpactGrenade"
        const val FRAG_GRENADE_ITEM_ID = "FragGrenade"
    }

    @Component
    class Binder : Config<GunGameConfig>(GunGameConfig())
}
