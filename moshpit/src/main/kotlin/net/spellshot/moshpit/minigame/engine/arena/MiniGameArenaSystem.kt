package net.spellshot.moshpit.minigame.engine.arena

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader
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

            // Read the fluid only if the section is already in memory.
            //
            // The original sin here was world.getFluidId(), which routed through
            // ChunkAccessor -> IWorldChunks.getChunk and BLOCKED on a future, draining
            // the world task queue while it waited. Called from inside a system tick --
            // which is where this runs -- that re-entered the Store mid-processing:
            //
            //   IllegalStateException: Store is currently processing!
            //
            // The damage was never limited to this system: draining the queue ran every
            // pending world.execute task in an illegal context, so unrelated work
            // (chunk ticking, teleport completion, spectator components) failed too.
            //
            // 0.6.0 was fixed by going through getChunkIfLoaded. Update 7 removed that
            // too, so the same guarantee now comes from SectionReader.hasStorageInMemory:
            // check first, read second, never block. A player standing in an unloaded
            // section is not somewhere we need to check for water anyway; the check
            // resumes as soon as it is in memory.
            val reader = SectionReader(world.chunkStore)
            val bx = pos.x.toInt()
            val by = pos.y.toInt()
            val bz = pos.z.toInt()
            if (!reader.hasStorageInMemory(bx, by, bz)) return
            val fluidId = reader.getFluidId(bx, by, bz)
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
