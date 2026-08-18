package net.everlands.moshpit.minigame.engine

import net.everlands.core.framework.HytaleService

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import org.springframework.stereotype.Service
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.arena.MiniGameArenaManager
import net.everlands.moshpit.minigame.engine.handler.MiniGameDamageHandler
import net.everlands.moshpit.minigame.engine.handler.MiniGameDeathHandler
import net.everlands.moshpit.minigame.engine.handler.MiniGameDisconnectHandler
import net.everlands.moshpit.minigame.engine.handler.MiniGameRespawnHandler
import net.everlands.moshpit.minigame.engine.model.MiniGame
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import net.everlands.moshpit.ui.HUDComposer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.ApplicationEventMulticaster
import java.util.*

@Service
class MiniGameEngine(
    val plugin: JavaPlugin,
    val hudComposer: HUDComposer,
    val arenaManager: MiniGameArenaManager,
    val eventPublisher: ApplicationEventPublisher,
    val eventSubscriber: ApplicationEventMulticaster,
    hytaleService: HytaleService
) {

    val logger: Logger = LoggerFactory.getLogger(javaClass)
    val games: MutableList<MiniGame<*>> = mutableListOf()

    init {
        instance = this
        hytaleService.onSetup(::setup)
    }

    fun setup() {
        // arenaManager.setup() is NOT called here. It carries @OnSetup, so the
        // framework already runs it -- and calling it twice is what produced
        // "id 'MiniGameArenaResource' already exists!", which left the resource
        // type unregistered and let a world save wipe every arena marker on disk.
        // The registration is idempotent now as well, but one path is better than
        // two plus a guard.
        plugin.entityStoreRegistry.registerSystem(MiniGameDamageHandler(this))
        plugin.entityStoreRegistry.registerSystem(MiniGameDeathHandler(this))
        plugin.entityStoreRegistry.registerSystem(MiniGameRespawnHandler(this))
        plugin.eventRegistry.register(PlayerDisconnectEvent::class.java, MiniGameDisconnectHandler(this))
    }


    fun registerGame(game: MiniGame<*>) {
        games.add(game)
    }

    fun doesSessionExistsById(id: String): Boolean {
        return games.any { game ->
            game.sessions.any { session ->
                session.id == id
            }
        }
    }

    fun listUnoccupiedArenas(): List<MiniGameArena> {
        return arenaManager.list().filter { arena ->
            !isArenaOccupied(arena)
        }
    }

    fun isArenaOccupied(arena: MiniGameArena) = games.any { game ->
        game.sessions.any { session ->
            session.arena.id == arena.id
        }
    }

    final inline fun <reified T : MiniGameSession> getSessionByPlayerUUID(uuid: UUID): T? {
        games.forEach { game ->
            game.sessions.forEach { session ->
                if (session.isParticipantInSession(uuid)) {
                    return session as T
                }
            }
        }
        return null
    }

    final inline fun <reified T : MiniGame<*>> getGameByTypeOrNull(): T? {
        games.forEach { game ->
            if (game is T) {
                return game
            }
        }
        return null
    }

    final inline fun <reified T : MiniGame<*>> getGameByType(): T {
        return getGameByTypeOrNull() ?: error("MiniGame of type ${T::class.java} not found")
    }

    fun getGameByPlayerUUID(uuid: UUID): MiniGame<*>? {
        return games.find { game -> game.isPlaying(uuid) }
    }

    fun getGameByClass(clazz: Class<out MiniGame<*>>): MiniGame<*>? {
        return games.find { game -> game::class.java == clazz }
    }

    companion object {
        lateinit var instance: MiniGameEngine

        const val DEFAULT_HUD_KEY = "MiniGame"

        const val KILL_SOUND = "SFX_MiniGame_Kill"

        const val DEATH_SOUND = "SFX_MiniGame_Death"
    }
}

