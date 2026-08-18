package net.everlands.moshpit.minigame.tdm

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
import net.everlands.moshpit.minigame.equipTeamArmor
import net.everlands.moshpit.minigame.engine.MiniGameRespawnEvent
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.ext.setTimeLeft
import net.everlands.moshpit.minigame.engine.model.*
import net.everlands.moshpit.minigame.engine.strategy.spawn.SpawnStrategy
import net.everlands.moshpit.minigame.engine.strategy.spawn.TeamSpawnStrategy
import net.everlands.moshpit.minigame.engine.strategy.team.BalancedTeamStrategy
import net.everlands.moshpit.minigame.engine.strategy.team.TeamStrategy
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.moshpit.MoshpitManager
import net.everlands.moshpit.moshpit.vote.MoshpitVoteCountdown
import net.everlands.moshpit.progression.ProgressionManager
import net.everlands.moshpit.progression.applyStats
import net.everlands.moshpit.progression.recordWin
import org.springframework.stereotype.Service

@Service
class TDMMiniGame(
    engine: MiniGameEngine,
    val loadoutService: LoadoutService,
    val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager,
    val config: TDMConfig.Binder
) :
    MiniGame<TDMMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    override val powerUps: Boolean = true

    companion object {
        const val STATE_IN_GAME = 0
        const val STATE_VOTE = 1
    }

    val moshpitGameConfig get() = moshpitManager.config.value.getGameConfig(MoshpitGame.TDM)

    override val states: Map<Int, MiniGameStateConfig> = mapOf(
        STATE_IN_GAME to MiniGameStateConfig(
            pvp = true,
            countdownSupplier = { session ->
                TDMCountdown(
                    this,
                    session as TDMMiniGameSession,
                    moshpitGameConfig.duration
                )
            }
        ),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as TDMMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): TDMMiniGameSession {
        val session = TDMMiniGameSession(this, arena, loadoutService)
        sessions.add(session)
        return session
    }
}

class TDMMiniGameSession(
    val tdmGame: TDMMiniGame, arena: MiniGameArena, val loadoutService: LoadoutService
) :
    MiniGameSession(tdmGame, arena) {


    override val teamStrategy: TeamStrategy = BalancedTeamStrategy(this) { TDMParticipant(it) }

    override var spawnStrategy: SpawnStrategy = TeamSpawnStrategy(arena)

    override var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = { playerRef, builder ->
        builder.append("Hud/Hardpoint.ui")
    }

    init {
        for (def in TDMTeamDef.entries) {
            teams.add(TDMTeam(def))
        }
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
            world.execute { giveLoadout(player, event.participant.playerRef) }
            val spawn = spawnStrategy.getSpawn(event.team, participant)
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }
        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val experienceConfig = tdmGame.progressionManager.experienceConfig.value
            val team = getTeamByUUID(killerParticipant.uuid)
            if (team != null && team is TDMTeam) {
                team.points += 1
                if (team.points >= tdmGame.config.value.maxPoints) {
                    launchGlobal {
                        update()
                    }
                    win()
                }
            }
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.TDM, "kills", 1)
                        }
                        tdmGame.progressionManager.addXP(this, experienceConfig.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.TDM, "deaths", 1)
                        }
                    }
                }
            }
        }
    }

    fun win() {
        state = TDMMiniGame.STATE_VOTE
        val winner = findWinner()
        if (winner == null) {
            sendTitle("DRAW", "GAME HAS ENDED")
            sendFormattedMessage("\n<c><b>Game Over!</b></c>\n<7>No team has won the game. It was a draw!\n")
        } else {
            sendTitle("${winner.name} WINS", "GAME HAS ENDED")
            sendFormattedMessage("\n<c><b>Game Over!</b></c>\n<7>The winner is team <${winner.def.color}>${winner.name}</${winner.def.color}> <7>with <c>${winner.points} points</c>!\n")
            if(participantCount > 1) {
                val experienceConfig = tdmGame.progressionManager.experienceConfig.value
                winner.runForEachParticipant {
                    val user = it.playerRef.userOrNull ?: return@runForEachParticipant
                    launchGlobal {
                        user.runLockGuarded {
                            user.recordWin(MoshpitGame.TDM)
                            tdmGame.progressionManager.addXP(user, experienceConfig.tdm.teamWin)
                        }
                    }
                }
            }
        }
    }

    suspend fun update() {
        val world = arena.world
        val firstTeam = getTeamByDef(TDMTeamDef.ODA)
        val secondTeam = getTeamByDef(TDMTeamDef.SENTINEL)
        world.context {
            runForEachParticipant {
                updateHud(it.playerRef) {
                    set("#FirstTeam.Text", "${firstTeam.name}: ${firstTeam.points}")
                    set("#SecondTeam.Text", "${secondTeam.name}: ${secondTeam.points}")
                    set("#FirstTeam.Style.TextColor", firstTeam.def.color)
                    set("#SecondTeam.Style.TextColor", secondTeam.def.color)

                    set("#FirstTeamPlayers.Text", getTeamByDef(TDMTeamDef.ODA).participants.count().toString())
                    set(
                        "#SecondTeamPlayers.Text",
                        getTeamByDef(TDMTeamDef.SENTINEL).participants.count().toString()
                    )

                    setTimeLeft(countdown?.remainingSeconds ?: -1)

                    set("#PointChangeTimeLeft.Visible", false)
                }
            }
        }
    }

    fun findWinner(): TDMTeam? {
        val firstTeam = getTeamByDef(TDMTeamDef.ODA)
        val secondTeam = getTeamByDef(TDMTeamDef.SENTINEL)
        return when {
            firstTeam.points > secondTeam.points -> firstTeam
            secondTeam.points > firstTeam.points -> secondTeam
            else -> null
        }
    }

    fun getTeamByDef(team: TDMTeamDef): TDMTeam {
        return teams.firstOrNull { it is TDMTeam && it.def == team } as? TDMTeam
            ?: error("Team $team not found in session")
    }


    override fun addParticipant(playerRef: PlayerRef, teleport: Boolean): MiniGameTeam? {
        val team = super.addParticipant(playerRef, teleport) ?: return null
        val participant = team.participants[playerRef.uuid] ?: error("Participant ${playerRef.uuid} not found in team")
        val player = playerRef.playerOrErr
        val ref = playerRef.referenceOrErr
        ref.store.ensureComponent(ref, UnlimitedAmmoComponent.componentType)
        giveLoadout(player, playerRef)
        if (teleport) {
            spawnStrategy.teleport(team, participant)
        }
        player.heal()
        playerRef.sendEventTitle("TDM", "NOW PLAYING", duration = 3.0f)
        return team
    }

    override fun respawnWithLoadout(playerRef: PlayerRef) {
        if (state == TDMMiniGame.STATE_VOTE) return
        val pair = getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        val player = playerRef.playerOrNull ?: return
        giveLoadout(player, playerRef)
        spawnStrategy.teleport(pair.first, pair.second)
    }

    override fun removeParticipant(playerRef: PlayerRef): MiniGameTeam? {
        val team = super.removeParticipant(playerRef) ?: return null
        val player = playerRef.playerOrErr
        game.engine.hudComposer.unregisterHUD(player, playerRef, MiniGameEngine.DEFAULT_HUD_KEY)
        return team
    }


    private fun giveLoadout(player: Player, playerRef: PlayerRef) {
        if (state == TDMMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        loadoutService.giveLoadout(player, playerRef)
        player.heal()
        val team = getTeamByUUID(playerRef.uuid) as? TDMTeam ?: error("Team ${playerRef.uuid} not found in team")
        equipTeamArmor(player, playerRef, team.def.armorPrefix)
    }
}

class TDMParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef)

