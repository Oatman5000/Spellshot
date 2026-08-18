package net.everlands.moshpit.minigame.engine.handler

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import org.slf4j.LoggerFactory
import java.util.function.Consumer

class MiniGameDisconnectHandler(val engine: MiniGameEngine) : Consumer<PlayerDisconnectEvent> {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun accept(event: PlayerDisconnectEvent) {
        val playerRef = event.playerRef
        val game = engine.getGameByPlayerUUID(playerRef.uuid) ?: return
        val session = game.getSessionByUUID(playerRef.uuid) ?: return
        try {
            session.removeParticipant(playerRef)
        } catch (e: Exception) {
            logger.warn("Error while removing participant ${playerRef.username} from session ${session.id}: ${e.message}")
        }
    }
}
