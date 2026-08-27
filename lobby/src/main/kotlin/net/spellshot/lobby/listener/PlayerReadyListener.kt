package net.spellshot.lobby.listener

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.spellshot.core.ext.addTeleport
import net.spellshot.core.ext.heal
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.core.framework.OnStart
import net.spellshot.lobby.LobbyConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Sets a player up when they arrive: fixed kit, full health, at spawn.
 *
 * Ported from the original Everlands lobby module. The inventory is cleared
 * first so nothing carried in from a previous world survives -- the lobby is
 * meant to hold exactly the navigator and, optionally, the leaderboard.
 */
@Service
class PlayerReadyListener(val plugin: JavaPlugin, val config: LobbyConfig.Binder) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @OnStart
    fun start() {
        plugin.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            event.playerRef.store.externalData.world.execute {
                runCatching { apply(event) }
                    .onFailure { logger.warn("lobby setup failed: ${it.message}") }
            }
        }
    }

    private fun apply(event: PlayerReadyEvent) {
        val player = event.player
        val playerRef = player.playerRefOrNull ?: return
        val cfg = config.value

        player.inventory.clear()
        player.heal()

        val hotbar = player.inventory.hotbar
        if (hotbar != null) {
            cfg.navigatorItemId?.let { id ->
                runCatching { hotbar.addItemStack(ItemStack(id)) }
                    .onFailure { logger.warn("could not give $id: ${it.message}") }
            }
            cfg.leaderboardItemId?.let { id ->
                runCatching { hotbar.addItemStack(ItemStack(id)) }
                    .onFailure { logger.warn("could not give $id: ${it.message}") }
            }
        }

        val ref = player.reference ?: return
        val spawn = ref.store.externalData.world.worldConfig.spawnProvider
        if (spawn != null) ref.addTeleport(spawn.getSpawnPoint(ref, ref.store))

        if (cfg.welcomeMessage.isNotBlank()) playerRef.sendRawMessage(cfg.welcomeMessage)
    }
}
