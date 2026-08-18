package net.everlands.moshpit.minigame.hardpoint

import org.joml.Vector3d
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.framework.launchGlobal
import net.everlands.core.ext.*
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
import java.time.Instant

@Service
class HardpointMiniGame(
    engine: MiniGameEngine,
    val loadoutService: LoadoutService,
    val hardpointConfig: HardpointConfig.Binder,
    val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager
) :
    MiniGame<HardpointMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    override val powerUps: Boolean = true

    companion object {
        const val STATE_IN_GAME = 0
        const val STATE_VOTE = 1
    }

    val moshpitGameConfig get() = moshpitManager.config.value.getGameConfig(MoshpitGame.HARDPOINT)

    override val states: Map<Int, MiniGameStateConfig> = mapOf(
        STATE_IN_GAME to MiniGameStateConfig(
            pvp = true,
            countdownSupplier = { session ->
                HardpointCountdown(
                    this,
                    session as HardpointMiniGameSession,
                    moshpitGameConfig.duration
                )
            }
        ),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as HardpointMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): HardpointMiniGameSession {
        val session = HardpointMiniGameSession(this, arena, loadoutService)
        sessions.add(session)
        return session
    }
}

class HardpointMiniGameSession(
    val hpGame: HardpointMiniGame, arena: MiniGameArena, val loadoutService: LoadoutService
) :
    MiniGameSession(hpGame, arena) {


    override val teamStrategy: TeamStrategy = BalancedTeamStrategy(this) { HardpointParticipant(it) }

    override var spawnStrategy: SpawnStrategy = TeamSpawnStrategy(arena)

    override var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = { playerRef, builder ->
        builder.append("Hud/Hardpoint.ui")
    }

    val points get() = arena.locations.filter { it.key.startsWith("hardpoint:") }.map { it.value.position }

    var pointState = HardpointPointState.NEUTRAL
    var currentPoint: Vector3d = points.randomOrNull()
        ?: error("Arena '${arena.id}' has no 'hardpoint:' markers and cannot host Hardpoint")
    var nextPointChange: Instant = Instant.now().plusSeconds(hpGame.hardpointConfig.value.pointSwitchInterval.toLong())

    init {
        val world = arena.world
        world.worldMapManager.markerProviders?.clear()
        world.worldMapManager.addMarkerProvider("hardpoint", HardpointMarkerProvider(this))
        for (def in HardpointTeamDef.entries) {
            teams.add(HardpointTeam(def))
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
            val spawn = spawnStrategy.getSpawn(event.team, participant) ?: return@subscribeToEvent
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }
        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val experienceConfig = hpGame.progressionManager.experienceConfig.value
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.HARDPOINT, "kills", 1)
                        }
                        hpGame.progressionManager.addXP(this, experienceConfig.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.HARDPOINT, "deaths", 1)
                        }
                    }
                }
            }
        }
    }

    fun update() {
        val world = arena.world
        val hpGame = game as HardpointMiniGame
        world.execute {
            val position = org.joml.Vector3d(currentPoint)
            val refs = mutableListOf<PlayerRef>()
            Selector.selectNearbyEntities(
                world.entityStore.store,
                position,
                hpGame.hardpointConfig.value.pointRange,
                { ref ->
                    val playerRef = ref.playerRefOrNull ?: return@selectNearbyEntities
                    refs.add(playerRef)
                },
                { true })
            val firstTeam = getTeamByDef(HardpointTeamDef.ODA)
            val secondTeam = getTeamByDef(HardpointTeamDef.SENTINEL)
            val firstTeamParticipantCount = refs.count { firstTeam.participants.containsKey(it.uuid) }
            val secondTeamParticipantCount = refs.count { secondTeam.participants.containsKey(it.uuid) }

            pointState = when {
                firstTeamParticipantCount > 0 && secondTeamParticipantCount == 0 -> HardpointPointState.FIRST_TEAM_CONTROLLED
                secondTeamParticipantCount > 0 && firstTeamParticipantCount == 0 -> HardpointPointState.SECOND_TEAM_CONTROLLED
                else -> HardpointPointState.NEUTRAL
            }
            val controllingTeam = when (pointState) {
                HardpointPointState.FIRST_TEAM_CONTROLLED -> firstTeam
                HardpointPointState.SECOND_TEAM_CONTROLLED -> secondTeam
                HardpointPointState.NEUTRAL -> null
            }
            if (controllingTeam != null) {
                controllingTeam.points += hpGame.hardpointConfig.value.pointsPerSecond
                val experienceConfig = hpGame.progressionManager.experienceConfig.value
                if(participantCount > 1) {
                    controllingTeam.runForEachParticipant {
                        val user = it.playerRef.userOrNull ?: return@runForEachParticipant
                        launchGlobal {
                            user.runLockGuarded {
                                hpGame.progressionManager.addXP(
                                    user,
                                    experienceConfig.hardpoint.objectiveXpPerSecond
                                )
                            }
                        }
                    }
                }

                if(controllingTeam.points >= hpGame.hardpointConfig.value.maxPoints) {
                    win()
                }
            }
            runForEachParticipant {
                updateHud(it.playerRef) {
                    set("#FirstTeam.Text", "${firstTeam.name}: ${firstTeam.points}")
                    set("#SecondTeam.Text", "${secondTeam.name}: ${secondTeam.points}")
                    set("#FirstTeam.Style.TextColor", firstTeam.def.color)
                    set("#SecondTeam.Style.TextColor", secondTeam.def.color)

                    set("#FirstTeamPlayers.Text", getTeamByDef(HardpointTeamDef.ODA).participants.count().toString())
                    set(
                        "#SecondTeamPlayers.Text",
                        getTeamByDef(HardpointTeamDef.SENTINEL).participants.count().toString()
                    )

                    setTimeLeft(countdown?.remainingSeconds ?: -1)

                    val seconds = ((nextPointChange.epochSecond - Instant.now().epochSecond))
                    set(
                        "#PointChangeTimeLeft.Text",
                        if (seconds >= 0) String.format(
                            "Point Change in: %02d:%02d",
                            seconds / 60,
                            seconds % 60
                        ) else "00:00"
                    )
                }
            }
            val particleColor = when (controllingTeam != null) {
                true -> controllingTeam.def.particleColor
                else -> "Hardpoint_Neutral"
            }
            ParticleUtil.spawnParticleEffect(particleColor, currentPoint, world.entityStore.store)
        }
    }

    fun win() {
        state = HardpointMiniGame.STATE_VOTE
        val winner = findWinner()
        if (winner == null) {
            sendTitle("DRAW", "GAME HAS ENDED")
            sendFormattedMessage("\n<c><b>Game Over!</b></c>\n<7>No team has won the game. It was a draw!\n")
        } else {
            sendTitle("${winner.name} WINS", "GAME HAS ENDED")
            sendFormattedMessage("\n<c><b>Game Over!</b></c>\n<7>The winner is team <${winner.def.color}>${winner.name}</${winner.def.color}> <7>with <c>${winner.points} points</c>!\n")
            val experienceConfig = hpGame.progressionManager.experienceConfig.value
            if(participantCount > 1) {
                winner.runForEachParticipant {
                    val user = it.playerRef.userOrNull ?: return@runForEachParticipant
                    launchGlobal {
                        user.runLockGuarded {
                            user.recordWin(MoshpitGame.HARDPOINT)
                            hpGame.progressionManager.addXP(user, experienceConfig.hardpoint.teamWin)
                        }
                    }
                }
            }

        }
    }

    fun findWinner(): HardpointTeam? {
        val firstTeam = getTeamByDef(HardpointTeamDef.ODA)
        val secondTeam = getTeamByDef(HardpointTeamDef.SENTINEL)
        return when {
            firstTeam.points > secondTeam.points -> firstTeam
            secondTeam.points > firstTeam.points -> secondTeam
            else -> null
        }
    }

    fun switchPoint() {
        game.engine.logger.info("Switching hardpoint to next location in session $id")
        nextPointChange = Instant.now().plusSeconds(hpGame.hardpointConfig.value.pointSwitchInterval.toLong())
        if (points.size == 1) return
        currentPoint = findNextPoint()
        val soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Deployable_Totem_Heal_Spawn")
        runForEachParticipant {
            it.playerRef.sendEventTitle("POINT CHANGED", "OPEN YOUR MAP", true, duration = 5f, fadeInDuration = 0.3f)
            it.playerRef.play2DSound(soundIndex, 10f, 2f)
        }
    }

    private fun findNextPoint(): Vector3d {
        if (points.size == 1) return currentPoint
        val availablePoints = points.filter { it != currentPoint }
        return availablePoints.random()
    }

    fun getTeamByDef(team: HardpointTeamDef): HardpointTeam {
        return teams.firstOrNull { it is HardpointTeam && it.def == team } as? HardpointTeam
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
        playerRef.sendEventTitle("HARDPOINT", "NOW PLAYING", duration = 3.0f)
        return team
    }

    override fun respawnWithLoadout(playerRef: PlayerRef) {
        if (state == HardpointMiniGame.STATE_VOTE) return
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
        if (state == HardpointMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        loadoutService.giveLoadout(player, playerRef)
        player.heal()
        val team = getTeamByUUID(playerRef.uuid) as? HardpointTeam ?: error("Team ${playerRef.uuid} not found in team")
        equipTeamArmor(player, playerRef, team.def.armorPrefix)
    }
}

enum class HardpointPointState {
    NEUTRAL,
    FIRST_TEAM_CONTROLLED,
    SECOND_TEAM_CONTROLLED
}

class HardpointParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef)

