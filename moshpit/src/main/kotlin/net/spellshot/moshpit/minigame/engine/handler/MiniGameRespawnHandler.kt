package net.spellshot.moshpit.minigame.engine.handler

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.RespawnSystems
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.MiniGameRespawnEvent
import org.slf4j.LoggerFactory

class MiniGameRespawnHandler(val engine: MiniGameEngine) : RespawnSystems.OnRespawnSystem() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun onComponentRemoved(
        refStore: Ref<EntityStore>, comp: DeathComponent, store: Store<EntityStore>, buffer: CommandBuffer<EntityStore>
    ) {
        val playerRef = store.getComponent(refStore, PlayerRef.getComponentType()) ?: error("Player not found")
        val game = engine.getGameByPlayerUUID(playerRef.uuid) ?: return
        val session = game.getSessionByUUID(playerRef.uuid) ?: return
        val pair = session.getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        try {
            engine.eventPublisher.publishEvent(
                MiniGameRespawnEvent(
                    session,
                    pair.first,
                    pair.second,
                    buffer
                )
            )
        } catch (ex: Exception) {
            logger.warn("Error while processing respawn events for ${playerRef.username}", ex)
        }
    }


}
