package net.spellshot.moshpit.item.airstrike

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService
import org.slf4j.LoggerFactory
import java.time.Instant

@EcsService
class MissileSpawnerEntitySystem : DelayedEntitySystem<EntityStore>(1f) {

    private val logger = LoggerFactory.getLogger(MissileSpawnerEntitySystem::class.java)

    override fun tick(
        delta: Float, index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        try {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
            val spawner = MissileSpawner.SPAWNERS.getIfPresent(playerRef.uuid.toString()) ?: return
            if (!Instant.now().isAfter(spawner.allowedToSpawn)) {
                return
            }
            for (i in 0 until spawner.amountPerSecond) {
                spawner.spawn(chunk.getReferenceTo(index), buffer)
            }
            spawner.seconds -= 1
            if (spawner.seconds <= 0) {
                MissileSpawner.SPAWNERS.invalidate(spawner.id)
                return
            }
        } catch (e: Exception) {
            logger.warn("Missile spawner tick failed: {}", e.message, e)
        }
    }

    override fun getQuery(): Query<EntityStore> {
        return PlayerRef.getComponentType()
    }
}
