package net.spellshot.moshpit.minigame.engine.handler

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import org.slf4j.LoggerFactory
import java.util.function.Consumer

class MiniGameDisconnectHandler(val engine: MiniGameEngine) : Consumer<PlayerDisconnectEvent> {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Removes a disconnecting player from their session, ON THE WORLD THREAD.
     *
     * `PlayerDisconnectEvent` fires on a network worker thread, but
     * [MiniGameSession.removeParticipant] reads the player component and unregisters
     * the HUD, and the ECS store asserts it is on the owning world thread. Calling it
     * directly threw
     *
     *     Assert not in thread! Thread[WorldThread - Moshpit]
     *     but was in Thread[ServerWorkerGroup - 4]
     *
     * which the catch below swallowed at WARN. The player stayed a participant for the
     * rest of the match: counted in the player total, still feeding the win checks and
     * the scoreboard, never actually there.
     */
    override fun accept(event: PlayerDisconnectEvent) {
        val playerRef = event.playerRef
        val game = engine.getGameByPlayerUUID(playerRef.uuid) ?: return
        val session = game.getSessionByUUID(playerRef.uuid) ?: return

        val world = runCatching { session.arena.world }.getOrNull()
        if (world == null) {
            logger.warn(
                "Could not resolve a world for session {}; {} stays a participant",
                session.id, playerRef.username
            )
            return
        }

        world.execute {
            try {
                session.removeParticipant(playerRef)
            } catch (e: Exception) {
                logger.warn(
                    "Error while removing participant {} from session {}: {}",
                    playerRef.username, session.id, e.message, e
                )
            }
        }
    }
}
