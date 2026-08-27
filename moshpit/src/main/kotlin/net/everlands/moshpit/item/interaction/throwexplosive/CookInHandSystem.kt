package net.everlands.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.transformOrNull
import net.everlands.core.framework.EcsService
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Detonates a grenade whose fuse ran out while it was still in someone's hand.
 *
 * Pulling the pin commits you: [ThrowExplosiveInteraction] takes the item and starts
 * the fuse on the first click, and the second click throws it with whatever fuse is
 * left. Never throwing it means it goes off where you stand.
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

    private companion object {
        private val logger = LoggerFactory.getLogger(CookInHandSystem::class.java)
    }
}
