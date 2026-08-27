package net.spellshot.moshpit.loadout

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.OnStart
import net.spellshot.moshpit.loadout.ui.LoadoutPage
import org.springframework.stereotype.Service

@Service
class MenuCommand(val plugin: JavaPlugin, val loadoutService: LoadoutService) :
    AbstractPlayerCommand("menu", "Menu open inventory") {

    @OnStart
    fun start() {
        plugin.commandRegistry.registerCommand(this)
    }

    override fun execute(
        context: CommandContext, store: Store<EntityStore?>, refStore: Ref<EntityStore?>, ref: PlayerRef, world: World
    ) {
        val player = store.getComponent(refStore, Player.getComponentType()) ?: return
        val user = ref.userOrNull ?: return
        player.pageManager.openCustomPage(refStore, store, LoadoutPage(player, ref, user, loadoutService))
    }

    /** 0.6.0 replaced the `canGeneratePermission()` override with this call. */
    init {
        requireNoPermission()
    }
}
