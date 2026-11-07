package net.spellshot.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader
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
            // Update 7 removed World.getFluidId along with the rest of World's block
            // access. SectionReader is the replacement; it is cheap to build and this
            // reads one block every half second, so there is nothing to cache.
            val inFluid = runCatching {
                SectionReader(world.chunkStore)
                    .getFluidId(pos.x.toInt(), pos.y.toInt(), pos.z.toInt()) != 0
            }.getOrDefault(false)
            // Below the build floor counts as the void; the fluid check alone
            // misses a lobby that simply has nothing underneath it.
            if (!inFluid && pos.y > VOID_Y) return

            val spawn = world.worldConfig.spawnProvider ?: return
            val ref = chunk.getReferenceTo(index)

            // The spawn point is resolved asynchronously as of Update 7:
            // ISpawnProvider.getSpawnPoint is gone, replaced by getSpawnPointAsync
            // returning a CompletableFuture<Transform>. It has to be async now
            // because resolving a spawn can need a chunk that is not in memory.
            //
            // That rules out the CommandBuffer this used to queue through -- the
            // buffer belongs to the tick that is long finished by the time the future
            // completes. Hopping back onto the world thread with world.execute is the
            // equivalent, and is what the minigames already do for deferred work.
            spawn.getSpawnPointAsync(ref, store).thenAccept { target ->
                if (target == null) return@thenAccept
                world.execute {
                    runCatching {
                        // Remove-then-add, not a bare add. A component that is already
                        // present makes addComponent throw -- the lesson Spectating
                        // taught -- and a player sitting in the void can have a
                        // teleport queued from the previous half-second tick.
                        ref.store.removeComponentIfExists(ref, Teleport.getComponentType())
                        ref.store.addComponent(
                            ref,
                            Teleport.getComponentType(),
                            Teleport(world, target.position, target.rotation)
                        )
                    }.onFailure { logger.warn("VoidReturnSystem teleport failed: ${it.message}") }
                }
            }
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
