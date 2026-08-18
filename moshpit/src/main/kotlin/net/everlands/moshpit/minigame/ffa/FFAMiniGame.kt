package net.everlands.moshpit.minigame.ffa

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.context
import net.everlands.core.framework.launchGlobal
import net.everlands.core.ext.heal
import net.everlands.core.ext.playerOrErr
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.sendEventTitle
import net.everlands.moshpit.item.ammo.hytale.UnlimitedAmmoComponent
import net.everlands.moshpit.loadout.LoadoutService
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.MiniGameKillEvent
import net.everlands.moshpit.minigame.engine.MiniGameRespawnEvent
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.ext.setTimeLeft
import net.everlands.moshpit.minigame.engine.model.*
import net.everlands.moshpit.minigame.engine.strategy.team.SingleTeamPerPlayerTeamStrategy
import net.everlands.moshpit.minigame.engine.strategy.team.TeamStrategy
import net.everlands.moshpit.minigame.ffa.countdown.FFACountdown
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.moshpit.MoshpitManager
import net.everlands.moshpit.moshpit.vote.MoshpitVoteCountdown
import net.everlands.moshpit.progression.ProgressionManager
import net.everlands.moshpit.progression.applyStats
import org.springframework.stereotype.Service

@Service
class FFAMiniGame(
    engine: MiniGameEngine, val loadoutService: LoadoutService, val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager
) :
    MiniGame<FFAMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    override val powerUps: Boolean = true

    companion object {
        const val STATE_IN_GAME = 0
        const val STATE_VOTE = 1
    }

    override val states: Map<Int, MiniGameStateConfig> = mapOf(
        STATE_IN_GAME to MiniGameStateConfig(
            pvp = true,
            countdownSupplier = { session ->
                FFACountdown(
                    session as FFAMiniGameSession,
                    moshpitManager.config.value.getGameConfig(MoshpitGame.FFA).duration
                )
            }),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as FFAMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): FFAMiniGameSession {
        val session = FFAMiniGameSession(this, arena, loadoutService)
        sessions.add(session)
        return session
    }
}

class FFAMiniGameSession(
    game: FFAMiniGame, arena: MiniGameArena, val loadoutService: LoadoutService
) :
    MiniGameSession(game, arena) {


    override val teamStrategy: TeamStrategy = SingleTeamPerPlayerTeamStrategy(
        this, participantProvider = { FFAParticipant(it) }
    )

    override var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = { playerRef, builder ->
        builder.append("Hud/FFA.ui")
        topList.forEachIndexed { index, participant ->
            builder.append("#Rows", "Hud/FFAScoreboardRow.ui")
            builder.set("#Rows[$index] #Placement.Text", "#${index + 1}")
            builder.set("#Rows[$index] #Name.Text", participant.playerRef.username)
            builder.set("#Rows[$index] #Kills.Text", participant.kills.toString())
        }
        builder.setTimeLeft(countdown?.remainingSeconds ?: -1)
    }

    val topList: List<FFAParticipant>
        get() = teams.mapNotNull { it.participants.values.firstOrNull() as? FFAParticipant }
            .sortedByDescending { it.kills }.take(10)

    init {
        subscribeToEvent<MiniGameRespawnEvent> { event ->
            val participant = event.participant
            val ref = participant.playerRef.reference ?: error("Player ref ${participant.playerRef.uuid} not found")
            val player = participant.playerRef.reference?.store?.getComponent(ref, Player.getComponentType())
                ?: error("Player entity for ref ${participant.playerRef.uuid} not found")
            // The loadout is applied AFTER this tick, deliberately.
            //
            // This subscriber runs inside RespawnSystems' onComponentRemoved
            // callback, i.e. while the Store is processing. Mutating inventory
            // containers there is the same hazard that produced "Store is
            // currently processing!" on addParticipant, and it is why items
            // sometimes vanished from the hotbar on respawn: the clear landed
            // but the re-add did not. Queuing it onto the world thread runs the
            // same work a tick later, once the teleport below has been applied.
            val world = ref.store.externalData.world
            world.execute { giveLoadout(player, participant.playerRef) }
            val spawn = spawnStrategy.getSpawn(event.team, participant)
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }
        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val world = arena.world
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.FFA, "kills", 1)
                        }
                        game.progressionManager.addXP(this, game.progressionManager.experienceConfig.value.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.FFA, "deaths", 1)
                        }
                    }
                }
                world.context {
                    refreshHud()
                }
            }
        }
    }

    fun findWinner(): FFAParticipant? {
        return teams.flatMap { it.participants.values }
            .maxByOrNull { it as FFAParticipant; it.kills } as FFAParticipant?
    }


    override fun addParticipant(playerRef: PlayerRef, teleport: Boolean): MiniGameTeam? {
        val team = super.addParticipant(playerRef, teleport) ?: return null
        val participant = team.participants[playerRef.uuid] ?: error("Participant ${playerRef.uuid} not found in team")
        val player = playerRef.playerOrErr
        giveLoadout(player, playerRef)
        val ref = playerRef.referenceOrErr
        ref.store.ensureComponent(ref, UnlimitedAmmoComponent.componentType)
        if (teleport) {
            spawnStrategy.teleport(team, participant)
        }
        player.heal()
        playerRef.sendEventTitle("FFA", "NOW PLAYING", duration = 3.0f)
        return team
    }

    override fun respawnWithLoadout(playerRef: PlayerRef) {
        if (state == FFAMiniGame.STATE_VOTE) return
        val pair = getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        val player = playerRef.playerOrNull ?: return
        giveLoadout(player, playerRef)
        spawnStrategy.teleport(pair.first, pair.second)
    }

    override fun removeParticipant(playerRef: PlayerRef): MiniGameTeam? {
        val team = super.removeParticipant(playerRef) ?: return null
        val player = playerRef.playerOrErr
        refreshHud()
        player.inventory.clear()
        player.heal()
        return team
    }

    private fun giveLoadout(player: Player, playerRef: PlayerRef) {
        if (state == FFAMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        loadoutService.giveLoadout(player, playerRef)
        player.heal()
    }
}

class FFAParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef)

