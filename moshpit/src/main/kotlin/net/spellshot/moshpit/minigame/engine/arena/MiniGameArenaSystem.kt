package net.spellshot.moshpit.minigame.engine.arena

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession
import org.slf4j.LoggerFactory

@EcsService
class MiniGameArenaSystem(val engine: MiniGameEngine) : DelayedEntitySystem<EntityStore>(0.3f) {

    private val logger = LoggerFactory.getLogger(MiniGameArenaSystem::class.java)
    private val damageCause by lazy {
        DamageCause.getAssetMap().getIndex("Fall")
    }

    override fun tick(
        delta: Float, index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        try {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
            val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return
            val world = store.externalData.world
            val pos = transform.position

            // Read the fluid from the chunk directly, and only if it is already loaded.
            //
            // world.getFluidId() routes through ChunkAccessor -> IWorldChunks.getChunk,
            // which BLOCKS on a future and drains the world task queue while waiting.
            // Called from inside a system tick -- which is where this runs -- that
            // re-enters the Store mid-processing and throws
            //
            //   IllegalStateException: Store is currently processing!
            //
            // The damage is not limited to this system: draining the queue here runs
            // every pending world.execute task in an illegal context, so unrelated work
            // (chunk ticking, teleport completion, spectator components) fails too. The
            // 0.6.0 logs were full of "Failed to load chunk" and "Failed to set chunk
            // ticking" traces that all bottom out in this one call.
            //
            // A player standing in an unloaded chunk is not somewhere we need to check
            // for water anyway; the check resumes as soon as the chunk is in memory.
            val worldChunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(pos.x.toInt(), pos.z.toInt())
            ) ?: return
            val fluidId = worldChunk.getFluidId(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
            if (fluidId != 0) {
                val ref = chunk.getReferenceTo(index)
                val session = engine.getSessionByPlayerUUID<MiniGameSession>(playerRef.uuid) ?: return
                val arena = session.arena
                if (arena.dieOnWater) {
                    val height = arena.dieOnWaterHeight
                    if (height == null) {
                        DeathComponent.tryAddComponent(
                            buffer,
                            ref,
                            Damage(Damage.NULL_SOURCE, damageCause, 2.1474836E9f)
                        )
                        return
                    }
                    if (transform.position.y <= height) {
                        DeathComponent.tryAddComponent(
                            buffer,
                            ref,
                            Damage(Damage.NULL_SOURCE, damageCause, 2.1474836E9f)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to check fluid to player ${e.message}")
        }
    }

    override fun getQuery() = Player.getComponentType()
}
