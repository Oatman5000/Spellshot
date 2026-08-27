package net.everlands.lobby.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.sendRawMessage
import net.everlands.core.framework.OnStart
import net.everlands.lobby.LobbyConfig
import net.everlands.lobby.page.ServerSelectorPage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * `/servers` opens the server selector.
 *
 * It used to print the address list to chat as well, as a fallback for the menu
 * failing to load. That is gone: `referToServer` moves the player directly, so an
 * address is no longer something they can usefully act on.
 */
@Service
class LobbyCommand(
    val plugin: JavaPlugin,
    val config: LobbyConfig.Binder,
) : AbstractPlayerCommand("servers", "List and open the Everlands server selector") {

    @OnStart
    fun start() {
        plugin.commandRegistry.registerCommand(this)
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore?>,
        refStore: Ref<EntityStore?>,
        ref: PlayerRef,
        world: World
    ) {
        // No chat listing any more -- the selector opens straight away and
        // referToServer means the player never needs to see an address.
        val player = store.getComponent(refStore, Player.getComponentType()) ?: return
        runCatching {
            player.pageManager.openCustomPage(refStore, store, ServerSelectorPage(ref, config))
        }.onFailure {
            // Silently doing nothing is the worst outcome here: /servers is the only
            // way off the lobby, so a failure has to say so rather than look like a
            // command that does not exist.
            logger.error("Could not open the server selector for ${ref.username}", it)
            ref.sendRawMessage("Could not open the server list. Ask an admin to check the console.")
        }
    }

    /** 0.6.0 replaced the `canGeneratePermission()` override with this call. */
    init {
        requireNoPermission()
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LobbyCommand::class.java)
    }
}
