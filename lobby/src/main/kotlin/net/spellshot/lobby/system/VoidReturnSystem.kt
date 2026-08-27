package net.spellshot.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService
import org.slf4j.LoggerFactory

/**
 * Returns anyone who falls into water or the void back to spawn.
 *
 * Ported from the original Everlands lobby module, which used the same fluid
 * check. Damage is cancelled outright by [NoDamageSystem], so without this a
 * player who walked off the edge would fall forever instead of dying.
 */
@EcsService
class VoidReturnSystem : DelayedEntitySystem<EntityStore>(0.5f) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        try {
            val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return
            val world = store.externalData.world
            val pos = transform.position
            val inFluid = runCatching {
                world.getFluidId(pos.x.toInt(), pos.y.toInt(), pos.z.toInt()) != 0
            }.getOrDefault(false)
            // Below the build floor counts as the void; the fluid check alone
            // misses a lobby that simply has nothing underneath it.
            if (!inFluid && pos.y > VOID_Y) return

            val spawn = world.worldConfig.spawnProvider ?: return
            val ref = chunk.getReferenceTo(index)
            val target = spawn.getSpawnPoint(ref, store)
            // Through the CommandBuffer, NOT Ref.addTeleport.
            //
            // addTeleport calls store.addComponent directly, and this runs inside
            // tick() while the Store is mid-processing -- that throws "Store is
            // currently processing!", which the catch below then swallowed into a
            // warn line. The teleport never landed and falling in water did
            // nothing. The minigames queue the same component through the buffer
            // for exactly this reason.
            buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, target.position, target.rotation)
            )
        } catch (e: Exception) {
            logger.warn("VoidReturnSystem failed: ${e.message}")
        }
    }

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    private companion object {
        /** Anything below this is considered out of the world. */
        const val VOID_Y = 0.0
    }
}
