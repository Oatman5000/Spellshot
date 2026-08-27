package net.spellshot.moshpit.ui

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import net.spellshot.core.framework.OnStart
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.spellshot.core.ext.playerOrErr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.function.Consumer

@Service
class PlayerDisconnectListener(val plugin: JavaPlugin, val composer: HUDComposer) : Consumer<PlayerDisconnectEvent> {

    @OnStart
    fun start() {
        plugin.eventRegistry.register(PlayerDisconnectEvent::class.java, this)
    }

    override fun accept(event: PlayerDisconnectEvent) {
        try {
            val player = event.playerRef.playerOrErr
            composer.remove(player, event.playerRef)
        } catch (ex: Exception) {
            // Expected in the common case: by the time the disconnect event runs, the
            // entity may already be gone, and playerOrErr throws rather than returning
            // null. Cleaning up HUD state for a player who has left is a no-op anyway,
            // so this stays non-fatal -- but at debug rather than discarded, because
            // this listener is also where a genuine off-thread access would surface.
            logger.debug("No HUD cleanup for {}: {}", event.playerRef.username, ex.message)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(PlayerDisconnectListener::class.java)
    }
}

