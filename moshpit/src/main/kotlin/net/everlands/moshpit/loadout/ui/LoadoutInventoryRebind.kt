package net.everlands.moshpit.loadout.ui

import com.hypixel.hytale.protocol.Packet
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow
import com.hypixel.hytale.protocol.packets.window.CloseWindow
import com.hypixel.hytale.protocol.packets.window.WindowType
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters
import com.hypixel.hytale.server.core.io.adapter.PacketFilter
import com.hypixel.hytale.server.core.universe.Universe
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.OnSetup
import net.everlands.core.framework.dsl.EverlandsCommand
import net.everlands.core.framework.dsl.buildCommand
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrNull
import net.everlands.core.ext.requireGamePermission
import net.everlands.core.ext.sendRawMessage
import net.everlands.moshpit.loadout.LoadoutService
import net.everlands.moshpit.ui.PlayerModes
import org.springframework.stereotype.Service

@Service
class LoadoutInventoryRebind(val service: LoadoutService) {

    var enabled: Boolean = true

    @OnSetup
    fun setup() {
        PacketAdapters.registerInbound(object : PacketFilter {
            override fun test(
                handler: PacketHandler, packet: Packet
            ): Boolean {
                if (!enabled) return false
                if (packet is ClientOpenWindow) {
                    if (packet.type == WindowType.PocketCrafting) {
                        val auth = handler.auth ?: return false
                        // Creative players get the stock inventory instead of the
                        // loadout screen. Read the cached mode: this runs on a
                        // network thread and must not touch the ECS store.
                        if (PlayerModes.isCreative(auth.uuid)) return false
                        handler.writeNoCache(CloseWindow(0))
                        val playerRef = Universe.get().getPlayer(auth.uuid) ?: return false
                        val ref = playerRef.referenceOrNull ?: return false

                        ref.store.externalData.world.execute {
                            val player = playerRef.playerOrNull ?: return@execute
                            val user = playerRef.userOrNull ?: return@execute
                            player.pageManager.openCustomPage(
                                ref,
                                ref.store,
                                LoadoutPage(player, playerRef, user, service)
                            )
                        }
                        return true
                    }
                }
                return false
            }
        })
    }


}

@Service
class LoadoutInventoryRebindCommand(val rebind: LoadoutInventoryRebind) : EverlandsCommand({
    buildCommand("loadoutinventoryrebind") {
        command.requireGamePermission("loadoutinventoryrebind")
        command.addAliases("lir")
        handle {
            rebind.enabled = !rebind.enabled
            context.sendRawMessage("Loadout inventory rebind is now ${if (rebind.enabled) "enabled" else "disabled"}.")
        }
    }
})


