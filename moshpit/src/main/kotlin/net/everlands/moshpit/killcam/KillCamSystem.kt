package net.everlands.moshpit.killcam

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import org.slf4j.LoggerFactory
import java.time.Instant

@EcsService
class KillCamSystem : DelayedEntitySystem<EntityStore>(1f) {

    private val logger = LoggerFactory.getLogger("KillCamSystem")

    override fun tick(
        delta: Float, index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        try {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
            val session = killCamSessions.getIfPresent(playerRef) ?: return
            if (Instant.now().isAfter(session.respawnAt)) {
                session.stop(chunk.getReferenceTo(index), buffer)
                return
            }
            session.page.updateCountdown(session.differenceInSeconds.toInt())
        } catch (e: Exception) {
            logger.error("Error while processing KillCamSystem tick", e)
        }
    }

    override fun getQuery(): Query<EntityStore?>? {
        return Player.getComponentType()
    }
}
