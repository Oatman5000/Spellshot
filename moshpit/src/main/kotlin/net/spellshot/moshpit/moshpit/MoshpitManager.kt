package net.spellshot.moshpit.moshpit

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent
import net.spellshot.core.framework.OnStart
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.Universe
import net.spellshot.core.framework.context
import net.spellshot.core.framework.launchGlobal
import kotlinx.coroutines.delay
import net.spellshot.core.api.SpellshotLeave
import net.spellshot.core.functions.broadcastFormat
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArenaManager
import com.hypixel.hytale.protocol.GameMode
import net.spellshot.moshpit.ui.PlayerModes
import net.spellshot.moshpit.minigame.engine.model.MiniGame
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession
import net.spellshot.moshpit.moshpit.vote.MoshpitVoteManager
import org.bson.BsonDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MoshpitManager(
    val plugin: JavaPlugin, val config: MoshpitConfig.Binder, val arenaManager: MiniGameArenaManager,
) {

    val voteManager = MoshpitVoteManager(this)

    private val logger = LoggerFactory.getLogger(javaClass)

    var totalGamesStarted: Int = 0

    var currentGame: MoshpitGame? = null

    val isSessionActive: Boolean
        get() {
            return internalSession != null
        }

    var currentSession: MiniGameSession
        get() = internalSession ?: error("No active session")
        set(value) {
            internalSession = value
        }

    var isRestarting: Boolean = false

    private var internalSession: MiniGameSession? = null

    @OnStart
    fun start() {
        plugin.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            if (!isSessionActive) return@registerGlobal
            event.playerRef.store.externalData.world.execute {
                val playerRef = event.player.playerRefOrNull ?: return@execute
                // Creative is a build/spectate mode -- joining a session would
                // teleport the builder to a spawn and hand them a loadout.
                if (event.player.gameMode == GameMode.Creative) return@execute
                currentSession.addParticipant(playerRef)
            }
        }

        plugin.eventRegistry.registerGlobal(PlayerSetupConnectEvent::class.java) { event ->
            if (isSessionActive) return@registerGlobal
            createSession()
        }
        if (config.value.games.isEmpty()) return
        currentGame = config.value.games.random().game
    }

        fun restartServerRpc(doc: BsonDocument): BsonDocument {
        launchGlobal { restartServer() }
        return doc
    }

    /**
     * Counts down, then sends everyone to the lobby.
     *
     * The evacuation used to be `SpellshotLeave.leaveAll()` -- no arguments, which
     * resolved to the vararg overload with an empty array. So the countdown ran, the
     * "server is restarting" broadcast went out, and every player stayed exactly where
     * they were. That overload has since been removed.
     *
     * The process still cannot restart itself; AMP's scheduler or an operator does
     * that. What this does is make the restart safe to perform, by getting players off
     * first instead of dropping them to the main menu.
     */
    suspend fun restartServer() {
        val countdown = config.value.serverRestartCountdown ?: 5
        broadcastFormat("<c>The server will be restarted to free up system resources. Join us again shortly!")
        logger.info("Restarting server in $countdown seconds...")
        isRestarting = true
        for (i in countdown downTo 1) {
            broadcastFormat("<7>Server is restarting in <c>$i <7>seconds...")
            delay(1000)
        }
        broadcastFormat("<c>Server is restarting now! You can reconnect in a few moments.")

        val online = Universe.get().players.toList()
        if (SpellshotLeave.target == null) {
            logger.warn(
                "Restart requested but no lobby is configured - {} player(s) left connected",
                online.size
            )
        } else {
            logger.info("Restart requested - referring {} player(s) to the lobby", online.size)
            SpellshotLeave.leaveAll(online)
        }
        delay(1000)
        logger.info("Players evacuated - the process itself must be restarted by AMP")
    }


    private fun createSession(game: MoshpitGame, forcedArena: MiniGameArena? = null): MiniGameSession {
        val miniGame = game.getGame<MiniGame<*>>()
        val arenas = arenaManager.list()
        if (arenas.isEmpty()) error("No arenas available")
        totalGamesStarted++
        if (forcedArena != null) {
            return miniGame.createSession(forcedArena)
        }
        if (arenas.size == 1) return miniGame.createSession(arenas.first())
        val allowedArenas =
            arenas.filter { config.value.getGameConfig(game).arenas.contains(it.id) }.ifEmpty { arenas }
        if (allowedArenas.isEmpty()) error("No arenas available")
        val arena = findRandomArena(allowedArenas) ?: allowedArenas.random()
        return miniGame.createSession(arena)
    }

    private fun createSession(): MiniGameSession {
        val game = currentGame ?: error("No current game set")
        currentSession = createSession(game)
        return currentSession
    }

    suspend fun switchGame(game: MoshpitGame, forcedArena: MiniGameArena? = null) {
        val nextSession = createSession(game, forcedArena)
        if (isSessionActive) {
            logger.info("Current moshpit session found, transferring players to new session for game ${game.name}")
            val session = currentSession
            val world = session.arena.world
            val participants = session.teams.flatMap { it.participants.values }
            for (participant in participants) {
                try {
                    world.context {
                        try {
                            // don't drag Creative builders into the next map
                            if (PlayerModes.isCreative(participant.playerRef.uuid)) return@context
                            nextSession.addParticipant(participant.playerRef)
                            delay(200)
                        } catch (e: Exception) {
                            logger.error("Failed to transfer player ${participant.playerRef.uuid} to new session", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error during transfer preparation for player ${participant.playerRef.uuid}", e)
                }
            }
            logger.info("New session found, transferring players to new session for game ${game.name} completed, removing old session")
            session.remove()
            currentGame = game
            currentSession = nextSession
            logger.info("Session successfully moved to ${game.name}")
        }
    }

    /**
     * Finds a random arena that is not the same as the current one.
     */
    private fun findRandomArena(arenas: List<MiniGameArena>): MiniGameArena? {
        if (arenas.isEmpty()) return null
        if (!isSessionActive) return arenas.random()
        val currentArena = currentSession.arena
        var randomArena = arenas.random()
        while (randomArena == currentArena && arenas.size > 1) {
            randomArena = arenas.random()
        }
        return randomArena
    }

}


