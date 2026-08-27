package net.spellshot.moshpit.minigame.engine.model

import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.util.NotificationUtil
import net.spellshot.core.ext.playerOrNull
import net.spellshot.core.framework.MessageParser
import net.spellshot.core.ext.referenceOrErr
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.ext.subscribe
import net.spellshot.moshpit.minigame.engine.MiniGameAddParticipantEvent
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.MiniGameEvent
import net.spellshot.moshpit.minigame.engine.MiniGameStopSessionEvent
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.countdown.MiniGameCountdown
import net.spellshot.moshpit.minigame.engine.strategy.spawn.RandomSpawnStrategy
import net.spellshot.moshpit.minigame.engine.strategy.spawn.SpawnStrategy
import net.spellshot.moshpit.minigame.engine.strategy.team.SingleTeamPerPlayerTeamStrategy
import net.spellshot.moshpit.minigame.engine.ui.TeamAnnouncer
import net.spellshot.moshpit.minigame.engine.strategy.team.TeamStrategy
import java.awt.Color
import java.util.*

open class MiniGameSession(
    val game: MiniGame<*>, var arena: MiniGameArena, val id: String = UUID.randomUUID().toString()
) {

    val engine get() = game.engine

    var state: Int
        get() = internalCurrentState
        set(value) {
            if (!game.states.containsKey(value)) error("State $value is not defined in states map!")
            internalCurrentState = value
            countdown?.stop()
            countdown = currentStateConfig.countdownSupplier.invoke(this)
            countdown?.start()
        }

    open val teamStrategy: TeamStrategy = SingleTeamPerPlayerTeamStrategy(this)

    open var spawnStrategy: SpawnStrategy = RandomSpawnStrategy(arena)

    open var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = null

    val currentStateConfig: MiniGameStateConfig
        get() = game.states[state] ?: error("State $state is not defined in states map!")

    var countdown: MiniGameCountdown<*>? = null
    val teams: MutableList<MiniGameTeam> = mutableListOf()

    val participantCount: Int
        get() = teams.sumOf { team -> team.participants.size }

    private var internalCurrentState: Int = 0

    init {
        state = 0

        // Change weather according to arena settings
        val world = arena.world
        val resource = world.entityStore.store.getResource(WeatherResource.getResourceType())
        resource.setForcedWeather(arena.weather)
        val config = world.worldConfig
        config.forcedWeather = arena.weather
        config.markChanged()
    }

    open fun stop() {
        countdown?.stop()
        try {
            engine.eventPublisher.publishEvent(MiniGameStopSessionEvent(this))
        } catch (e: Exception) {
            engine.logger.error("Error publishing MiniGameStopSessionEvent for session $id", e)
        }
    }

    fun remove() {
        stop()
        game.sessions.remove(this)
        game.engine.logger.info("Removed mini-game session $id")
    }

    inline fun <reified T : MiniGameEvent> subscribeToEvent(crossinline handle: (T) -> Unit) {
        game.engine.eventSubscriber.subscribe<T> {
            if (it.session == this) {
                handle(it)
            }
        }
    }

    open fun addParticipant(playerRef: PlayerRef, teleport: Boolean = true): MiniGameTeam? {
        if (teams.any { it.participants.containsKey(playerRef.uuid) }) return null
        val team = teamStrategy.distributePlayer(playerRef)
        val participant = team.participants[playerRef.uuid] ?: error("Participant ${playerRef.uuid} not found!")
        engine.eventPublisher.publishEvent(MiniGameAddParticipantEvent(this, team, participant))
        // Flash the side on screen. No-ops in FFA/OitC/Gun Game, where every player is
        // their own team and there is nothing to announce.
        TeamAnnouncer.announce(team, participant)
        if (hudProvider != null) {
            val player = playerRef.playerOrNull
            if (player != null) {
                game.engine.hudComposer.registerHud(player, playerRef, MiniGameEngine.DEFAULT_HUD_KEY) {
                    handle {
                        hudProvider?.invoke(playerRef, it)
                    }
                }
            }
        }
        return team
    }

    /**
     * Re-applies the player's current loadout and returns them to a spawn,
     * without touching team assignment or their kill/death tally.
     *
     * Called when a player picks a loadout mid-match so the change takes effect
     * immediately instead of waiting for them to die.
     */
    open fun respawnWithLoadout(playerRef: PlayerRef) {}

    open fun removeParticipant(playerRef: PlayerRef): MiniGameTeam? {
        val team = getTeamByPlayer(playerRef) ?: return null
        teamStrategy.leaveTeam(playerRef, team)
        if (hudProvider != null) {
            val player = playerRef.playerOrNull
            if (player != null) {
                game.engine.hudComposer.unregisterHUD(player, playerRef, MiniGameEngine.DEFAULT_HUD_KEY)
            }
        }
        return team
    }

    fun sendTitle(primaryMessage: String, secondaryMessage: String) {
        runForEachParticipant {
            it.playerRef.sendEventTitle(primaryMessage, secondaryMessage, true, duration = 5f, fadeInDuration = 0.3f)
        }
    }

    fun refreshHud(player: Player, playerRef: PlayerRef) {
        game.engine.hudComposer.refresh(player, playerRef)
    }

    fun updateHud(playerRef: PlayerRef, builder: UICommandBuilder.() -> Unit) {
        game.engine.hudComposer.update(playerRef, MiniGameEngine.DEFAULT_HUD_KEY) {
            builder()
        }
    }

    fun refreshHud() {
        runForEachParticipant {
            val player = it.playerRef.playerOrNull ?: return@runForEachParticipant
            refreshHud(player, it.playerRef)
        }
    }

    fun isParticipantInSession(uuid: UUID): Boolean {
        return teams.any { team ->
            team.participants.containsKey(uuid)
        }
    }

    fun isInSameTeam(uuid: UUID, otherUUID: UUID): Boolean {
        val team = getTeamByUUID(uuid) ?: return false
        return team.participants.containsKey(otherUUID)
    }

    fun getTeamAndParticipantByUUID(uuid: UUID): Pair<MiniGameTeam, MiniGameParticipant>? {
        return teams.firstNotNullOfOrNull { team ->
            val participant = team.participants[uuid]
            if (participant != null) {
                Pair(team, participant)
            } else {
                null
            }
        }
    }

    fun getTeamByUUID(uuid: UUID) = teams.find { team ->
        team.participants.containsKey(uuid)
    }

    fun getTeamByPlayer(player: PlayerRef) = getTeamByUUID(player.uuid)

    fun sendMessage(message: Message) {
        teams.forEach { team ->
            team.sendMessage(message)
        }
    }

    fun sendFormattedMessage(message: String) {
        val parsedMessage = MessageParser.parse(message)
        teams.forEach { team ->
            team.sendMessage(parsedMessage)
        }
    }

    inline fun runForEachParticipant(action: (MiniGameParticipant) -> Unit) {
        teams.forEach { team ->
            team.runForEachParticipant(action)
        }
    }

    fun switchArena(callback: (participant: MiniGameParticipant) -> Unit = {}) {
        val arenas = game.engine.listUnoccupiedArenas()
        if (arenas.isEmpty()) return
        arena = arenas.random()
        spawnStrategy = RandomSpawnStrategy(arena)
        runForEachParticipant { participant ->
            val team = getTeamByPlayer(participant.playerRef) ?: error("Could not find player")
            val ref = participant.playerRef.referenceOrErr
            ref.store.externalData.world.execute {
                spawnStrategy.teleport(team, participant)
                NotificationUtil.sendNotification(
                    participant.playerRef.packetHandler,
                    Message.raw("Now playing ${arena.name}").color(Color.GREEN),
                    Message.raw("Made by ${arena.author}"),
                    NotificationStyle.Success
                )
                callback(participant)
            }
        }
    }
}

open class MiniGameTeam(val id: String = UUID.randomUUID().toString(), val name: String) {

    val participants: MutableMap<UUID, MiniGameParticipant> = mutableMapOf()


    fun sendMessage(message: Message) {
        participants.values.forEach { participant ->
            participant.playerRef.sendMessage(message)
        }
    }

    inline fun runForEachParticipant(action: (MiniGameParticipant) -> Unit) {
        participants.values.forEach { participant ->
            action(participant)
        }
    }
}

open class MiniGameParticipant(
    val uuid: UUID,
    val playerRef: PlayerRef,
)

open class StatisticalMiniGameParticipant(
    uuid: UUID,
    playerRef: PlayerRef,
) : MiniGameParticipant(uuid, playerRef) {
    var kills: Int = 0
    var deaths: Int = 0
}
