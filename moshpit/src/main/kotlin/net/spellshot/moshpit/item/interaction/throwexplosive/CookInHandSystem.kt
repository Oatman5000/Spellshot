package net.spellshot.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.ext.transformOrNull
import net.spellshot.core.framework.EcsService
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Detonates a grenade whose fuse ran out while it was still in someone's hand.
 *
 * Pressing the throw button pulls the pin: [CookExplosiveInteraction] starts the fuse
 * on the press, and releasing throws the grenade with whatever fuse is left. Holding
 * past the fuse means it goes off where you stand.
 *
 * The grenade is taken off the holder here, not just on a successful throw. This
 * originally skipped it, on the reasoning that a 250 damage blast at zero range kills
 * you and the item goes with your loadout on respawn anyway -- which was wrong twice
 * over: the blast was not landing at all (see executeRadial), and "they will die
 * anyway" is not something a consume path should assume. Surviving a cook-off with
 * the grenade still in hand is an infinite supply.
 *
 * Ticks players rather than grenades because a cooking grenade is not an entity --
 * it is an entry in [CookingExplosives], deliberately, so the fuse survives the
 * player's entity being destroyed and rebuilt on respawn.
 */
@EcsService
class CookInHandSystem : DelayedEntitySystem<EntityStore>(0.1f) {

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        val playerRef = player.playerRefOrNull ?: return
        val cooking = CookingExplosives.peek(playerRef.uuid) ?: return
        if (Instant.now().isBefore(cooking.explodeAt)) return

        // Claim it before detonating. If anything below throws, the fuse is already
        // spent -- leaving it in the map would re-detonate every tick from here on.
        CookingExplosives.take(playerRef.uuid) ?: return

        val transform = chunk.transformOrNull(index) ?: run {
            logger.warn("{} cooked off a grenade but has no transform to place it at", playerRef.username)
            return
        }

        logger.debug("Grenade cooked off in {}'s hand", playerRef.username)
        consumeGrenade(player, playerRef, cooking.itemId)
        Explosions.detonate(
            buffer = buffer,
            position = transform.transform.position,
            source = chunk.getReferenceTo(index),
            radius = cooking.damageRadius,
            damageAmount = cooking.damageAmount,
            damageFalloff = cooking.damageFalloff,
            explosionParticleId = cooking.explosionParticleId,
            explosionSoundId = cooking.explosionSoundId,
            explosiveType = cooking.type,
        )
    }

    /**
     * Takes one [itemId] off the holder's hotbar.
     *
     * Scans rather than trusting a remembered slot: the fuse keeps burning through a
     * slot change, and the grenades cancel their charge on one, so "pull the pin and
     * switch weapons" is a completely ordinary thing to do and would otherwise take
     * the wrong item -- or, worse, whatever they had switched to.
     *
     * A miss is only logged. Failing to consume leaves the player one grenade richer,
     * which is a balance problem; throwing out of a tick would be a crash.
     */
    private fun consumeGrenade(player: Player, playerRef: PlayerRef, itemId: String?) {
        if (itemId == null) {
            logger.warn("{} cooked off a grenade with no recorded item; nothing consumed", playerRef.username)
            return
        }
        val hotbar = player.inventory.hotbar ?: return
        runCatching {
            for (slot in 0 until HOTBAR_SCAN_SLOTS) {
                val stack = hotbar.getItemStack(slot.toShort()) ?: continue
                if (stack.itemId != itemId) continue
                if (stack.quantity > 1) {
                    hotbar.setItemStackForSlot(slot.toShort(), stack.withQuantity(stack.quantity - 1))
                } else {
                    hotbar.removeItemStackFromSlot(slot.toShort())
                }
                return
            }
            logger.debug("{} no longer holds {}; nothing to consume", playerRef.username, itemId)
        }.onFailure {
            logger.warn("Could not take {} from {}: {}", itemId, playerRef.username, it.message)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CookInHandSystem::class.java)

        /** Matches the hotbar width the rest of the mod scans. */
        private const val HOTBAR_SCAN_SLOTS = 5
    }
}
