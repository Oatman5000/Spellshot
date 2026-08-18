package net.everlands.moshpit.minigame.oitc

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import java.util.UUID

/**
 * Cross-cutting checks for One in the Chamber.
 *
 * The mode's rules have to be enforced in places that have no reference to the
 * session -- the shared damage filter, the reload interaction, the killstreak
 * manager. Rather than wiring each of those to the mini-game, they ask here.
 *
 * Every lookup goes through [MiniGameEngine.instance], so these are safe to call
 * from anywhere that runs after engine setup and return false when no session is
 * running at all.
 */
object OITCRules {

    /**
     * Damage large enough to kill through any armour or resistance.
     *
     * Deliberately the same value the airstrike uses. Health is a float, so this
     * is "lethal" rather than a literal instant-death flag; the engine has no
     * public one-shot mechanism.
     */
    const val LETHAL_DAMAGE: Float = 2.1474836E9f

    /**
     * Index of the "Gun" damage cause, which [net.everlands.moshpit.item.interaction.ShootInteraction]
     * stamps on every bullet.
     *
     * This is what separates a shot from a knife swing. The knife runs the stock
     * DamageEntity interaction and carries a different cause, so it keeps its
     * normal 25 physical damage and is NOT a one-hit kill.
     */
    val gunDamageCauseIndex: Int by lazy(LazyThreadSafetyMode.NONE) {
        DamageCause.getAssetMap().getIndex("Gun")
    }

    /** True if this player is currently in an OitC match. */
    fun isPlaying(uuid: UUID): Boolean =
        runCatching { MiniGameEngine.instance.getGameByPlayerUUID(uuid) is OITCMiniGame }.getOrDefault(false)

    /** True if this entity is a player currently in an OitC match. */
    fun isPlaying(ref: Ref<EntityStore>): Boolean {
        val playerRef = runCatching {
            ref.store.getComponent(ref, PlayerRef.getComponentType())
        }.getOrNull() ?: return false
        return isPlaying(playerRef.uuid)
    }

    /**
     * Reloading is disabled outright in OitC.
     *
     * Bullets are the scoreboard: you get one, and you only get more by killing.
     * Leaving the reload interaction live would let anyone top the revolver back
     * up to its magazine size and the mode would collapse into plain FFA.
     */
    fun blocksReload(ref: Ref<EntityStore>): Boolean = isPlaying(ref)
}
