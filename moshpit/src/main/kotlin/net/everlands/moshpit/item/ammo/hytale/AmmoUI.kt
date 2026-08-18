package net.everlands.moshpit.item.ammo.hytale

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import net.everlands.core.framework.OnStart
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.everlands.core.ext.playerRefOrNull
import net.everlands.moshpit.ui.HUDComposer
import org.springframework.stereotype.Service

@Service
class AmmoUI(val plugin: JavaPlugin, val composer: HUDComposer) {

    @OnStart
    fun start() {
        plugin.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            event.playerRef.store.externalData.world.execute {
                val playerRef = event.player.playerRefOrNull ?: return@execute
                composer.registerHud(event.player, playerRef, "Ammo") {
                    handle { builder ->
                        builder.append("Hud/Ammo.ui")
                    }
                }
            }

        }
    }
}

