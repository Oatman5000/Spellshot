package net.spellshot.lobby

import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import net.spellshot.core.framework.SpellshotPlugin
import org.springframework.context.annotation.ComponentScan

/**
 * Spellshot lobby.
 *
 * A separate plugin from Moshpit on purpose -- it is meant to run on its own
 * Hytale instance. Do NOT install moshpit-plugin.jar alongside it: Moshpit
 * creates a match on PlayerSetupConnectEvent and errors with "No arenas
 * available" on a world that has no arena markers.
 *
 * Salvaged from the lobby module of the original Everlands source, which was
 * dropped during the standalone port. The original moved players by calling
 * `matchmakingService.joinQueue(...)` against Spookline's private hyboot
 * coordinator, which we do not have -- but Hytale ships its own equivalent:
 * `PlayerRef.referToServer(host, port)`. The selector uses that, so picking a
 * server hands the player over directly.
 */
@ComponentScan(basePackages = ["net.spellshot"])
class Application

class LobbyPlugin(init: JavaPluginInit) : SpellshotPlugin(init) {
    override fun configurationClass(): Class<*> = Application::class.java
}
