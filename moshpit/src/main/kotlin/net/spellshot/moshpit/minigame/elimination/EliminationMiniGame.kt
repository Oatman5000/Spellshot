package net.spellshot.moshpit.minigame.elimination

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import kotlinx.coroutines.delay
import net.spellshot.core.ext.heal
import net.spellshot.core.ext.playerOrErr
import net.spellshot.core.ext.playerOrNull
import net.spellshot.core.ext.referenceOrErr
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.context
import net.spellshot.core.framework.launchGlobal
import net.spellshot.moshpit.item.ammo.hytale.UnlimitedAmmoComponent
import net.spellshot.moshpit.loadout.LoadoutService
import net.spellshot.moshpit.minigame.engine.MiniGameDeathEvent
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.MiniGameKillEvent
import net.spellshot.moshpit.minigame.engine.MiniGameRespawnEvent
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.ext.setTimeLeft
import net.spellshot.moshpit.minigame.engine.model.*
import net.spellshot.moshpit.minigame.engine.strategy.spawn.SpawnStrategy
import net.spellshot.moshpit.minigame.engine.strategy.spawn.TeamSpawnStrategy
import net.spellshot.moshpit.minigame.engine.strategy.team.BalancedTeamStrategy
import net.spellshot.moshpit.minigame.engine.strategy.team.TeamStrategy
import net.spellshot.moshpit.minigame.equipTeamArmor
import net.spellshot.moshpit.minigame.tdm.TDMTeamDef
import net.spellshot.moshpit.moshpit.MoshpitGame
import net.spellshot.moshpit.moshpit.MoshpitManager
import net.spellshot.moshpit.moshpit.vote.MoshpitVoteCountdown
import net.spellshot.moshpit.progression.ProgressionManager
import net.spellshot.moshpit.progression.applyStats
import net.spellshot.moshpit.progression.recordWin
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Elimination -- TDM with one life each.
 *
 * Die once and you are out for the rest of the match, watching over a team-mate's
 * shoulder ([SpectatorManager]). A team is beaten the moment its last player
 * falls; if the clock runs out first, the team with more players still standing
 * takes it, with total eliminations as the tiebreak.
 *
 * Teams, spawns and armour are all shared with TDM, so this behaves identically
 * up to the point where somebody dies.
 */
@Service
class EliminationMiniGame(
    engine: MiniGameEngine,
    val loadoutService: LoadoutService,
    val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager,
    val eliminationConfig: EliminationConfig.Binder,
) : MiniGame<EliminationMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    /**
     * No power-ups.
     *
     * A health pickup is worth far more when you cannot respawn, and whoever
     * happens to spawn nearest one gets a decisive advantage through no skill.
     */
    override val powerUps: Boolean = false

    /**
     * Killers are NOT healed.
     *
     * Every other mode tops you up so you can keep pushing. Here a won fight
     * should leave you weakened -- that lingering damage is most of the tension,
     * and healing it away turns the first player to get a kill into a juggernaut.
     */
    override val healKiller = false

    companion object {
        const val STATE_IN_GAME = 0
        const val STATE_VOTE = 1
    }

    val moshpitGameConfig get() = moshpitManager.config.value.getGameConfig(MoshpitGame.ELIMINATION)

    override val states: Map<Int, MiniGameStateConfig> = mapOf(
        STATE_IN_GAME to MiniGameStateConfig(
            pvp = true,
            countdownSupplier = { session ->
                EliminationCountdown(session as EliminationMiniGameSession, moshpitGameConfig.duration)
            }
        ),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as EliminationMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): EliminationMiniGameSession {
        val session = EliminationMiniGameSession(this, arena, loadoutService)
        sessions.add(session)
        return session
    }
}

class EliminationMiniGameSession(
    val eliminationGame: EliminationMiniGame,
    arena: MiniGameArena,
    val loadoutService: LoadoutService,
) : MiniGameSession(eliminationGame, arena) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** 1-based round counter within the series. */
    private var roundNumber: Int = 1

    /**
     * False during the intermission between rounds.
     *
     * Without it a death landing after the wipe -- or during the revive -- would
     * run the round-scoring path a second time.
     */
    private var roundActive: Boolean = true

    override val teamStrategy: TeamStrategy = BalancedTeamStrategy(this) { EliminationParticipant(it) }

    override var spawnStrategy: SpawnStrategy = TeamSpawnStrategy(arena)

    /** Reuses the TDM/Hardpoint bar; the two numbers are alive counts, not points. */
    override var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = { _, builder ->
        builder.append("Hud/Hardpoint.ui")
    }

    init {
        for (def in TDMTeamDef.entries) {
            teams.add(EliminationTeam(def))
        }

        // Elimination is recorded on death rather than on kill, so falling, the
        // airstrike and self-damage all count. A kill event would miss them and
        // leave a dead player permanently "alive" for the win check.
        subscribeToEvent<MiniGameDeathEvent> { event ->
            val participant = event.participant as? EliminationParticipant ?: return@subscribeToEvent
            if (participant.eliminated) return@subscribeToEvent
            participant.eliminated = true
            val team = event.team as? EliminationTeam ?: return@subscribeToEvent
            sendFormattedMessage(
                "<${team.def.color}>${participant.playerRef.username}</${team.def.color}> <7>was eliminated. " +
                    "<c>${team.aliveCount}</c> <7>left on ${team.name}."
            )
            // Anyone watching the player who just died needs a new camera target.
            SpectatorManager.retarget(team)
            checkForWin()
        }

        subscribeToEvent<MiniGameRespawnEvent> { event ->
            val participant = event.participant
            val ref = participant.playerRef.reference ?: error("Player ref ${participant.playerRef.uuid} not found")
            val player = participant.playerRef.reference?.store?.getComponent(ref, Player.getComponentType())
                ?: error("Player entity for ref ${participant.playerRef.uuid} not found")
            val world = ref.store.externalData.world
            val eliminated = (participant as? EliminationParticipant)?.eliminated ?: false

            if (eliminated) {
                // No loadout, no revival for the rest of THIS round.
                //
                // Deliberately NOT teleported. On 0.5.7 the body was moved to the team
                // spawn to get it out of the firefight, because a player entity could
                // not be despawned and spectating was only a camera attached to a
                // team-mate. 0.6.0's Spectating component takes the body out of the
                // world properly, and adding a Teleport in the same tick fights it --
                // the entity gets yanked to a spawn point while the spectator systems
                // are trying to attach it to someone else, which is what makes it look
                // like spectator mode never engaged.
                world.execute {
                    runCatching { player.inventory.clear() }
                        .onFailure { logger.warn("could not clear inventory for spectator: ${it.message}") }
                    SpectatorManager.beginSpectating(
                        participant.playerRef,
                        event.team as? EliminationTeam ?: return@execute
                    )
                }
                return@subscribeToEvent
            } else {
                // Deferred for the same reason as TDM: this runs inside
                // RespawnSystems' onComponentRemoved while the Store is
                // processing, and mutating inventory there is what made items
                // vanish from the hotbar.
                world.execute { giveLoadout(player, participant.playerRef) }
            }

            val spawn = spawnStrategy.getSpawn(event.team, participant) ?: return@subscribeToEvent
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }

        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val experienceConfig = eliminationGame.progressionManager.experienceConfig.value
            val world = arena.world
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.ELIMINATION, "kills", 1)
                        }
                        eliminationGame.progressionManager.addXP(this, experienceConfig.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.ELIMINATION, "deaths", 1)
                        }
                    }
                }
                world.context { update() }
            }
        }
    }

    /**
     * Releases every spectator camera when the session ends.
     *
     * [win] already does this, but a session can also be torn down by map
     * rotation or a forced game switch, which never runs the win path. Without
     * this an eliminated player would carry a camera bolted to a team-mate into
     * the next match.
     */
    override fun stop() {
        SpectatorManager.stopAll(this)
        super.stop()
    }

    /** Used by the damage filter to make spectators untouchable in both directions. */
    fun isEliminated(uuid: java.util.UUID): Boolean =
        (getTeamAndParticipantByUUID(uuid)?.second as? EliminationParticipant)?.eliminated == true

    fun getTeamByDef(def: TDMTeamDef): EliminationTeam =
        teams.firstOrNull { it is EliminationTeam && it.def == def } as? EliminationTeam
            ?: error("Team $def not found in session")

    /**
     * Ends the ROUND as soon as one side has nobody left.
     *
     * Guarded on both the state and [roundActive] so a second death landing in
     * the same tick as the wipe -- or one arriving during the intermission --
     * cannot score the round twice.
     */
    private fun checkForWin() {
        if (state == EliminationMiniGame.STATE_VOTE || !roundActive) return
        val first = getTeamByDef(TDMTeamDef.ODA)
        val second = getTeamByDef(TDMTeamDef.SENTINEL)
        when {
            first.isWiped && second.isWiped -> endRound(null)
            first.isWiped -> endRound(second)
            second.isWiped -> endRound(first)
        }
    }

    /**
     * Scores a round and either starts the next one or ends the series.
     *
     * A double wipe (both teams gone in the same exchange) scores for nobody and
     * the round is simply replayed -- otherwise a mutual grenade could hand out a
     * round win at random.
     */
    private fun endRound(winner: EliminationTeam?) {
        if (!roundActive) return
        roundActive = false
        val config = eliminationGame.eliminationConfig.value
        val first = getTeamByDef(TDMTeamDef.ODA)
        val second = getTeamByDef(TDMTeamDef.SENTINEL)

        if (winner == null) {
            sendTitle("ROUND DRAW", "ROUND $roundNumber")
            sendFormattedMessage("<7>Round <c>$roundNumber</c> was a double wipe -- no round point awarded.")
        } else {
            winner.roundWins += 1
            sendTitle("${winner.name} TAKES THE ROUND", "ROUND $roundNumber")
            sendFormattedMessage(
                "<7>Round <c>$roundNumber</c> to <${winner.def.color}>${winner.name}</${winner.def.color}><7>. " +
                    "Series: <${first.def.color}>${first.name} ${first.roundWins}</${first.def.color}> <7>- " +
                    "<${second.def.color}>${second.roundWins} ${second.name}</${second.def.color}> " +
                    "<7>(first to <c>${config.roundsToWin}</c>)"
            )
        }

        if (winner != null && winner.roundWins >= config.roundsToWin) {
            win(winner)
            return
        }

        // Intermission, then everyone back up for the next round.
        launchGlobal {
            delay(config.intermissionSeconds * 1000L)
            arena.world.context { startRound() }
        }
    }

    /**
     * Revives everybody and starts the next round.
     *
     * "Revive" is cheap here because an eliminated player was never actually
     * removed -- their entity stayed alive with an empty inventory and a camera
     * bolted to a team-mate. Clearing the flag, releasing the camera and handing
     * back a loadout is the whole of it.
     */
    private fun startRound() {
        if (state == EliminationMiniGame.STATE_VOTE) return
        roundNumber += 1
        for (team in teams) {
            val eliminationTeam = team as? EliminationTeam ?: continue
            team.runForEachParticipant { participant ->
                val p = participant as? EliminationParticipant ?: return@runForEachParticipant
                p.eliminated = false
                SpectatorManager.stopSpectating(p.playerRef)
                val player = p.playerRef.playerOrNull ?: return@runForEachParticipant
                runCatching {
                    giveLoadout(player, p.playerRef)
                    spawnStrategy.teleport(eliminationTeam, p)
                }.onFailure { logger.warn("Could not revive ${p.playerRef.username}: ${it.message}") }
            }
        }
        roundActive = true
        val config = eliminationGame.eliminationConfig.value
        sendTitle("ROUND $roundNumber", "FIGHT")
        sendFormattedMessage("<c>Round $roundNumber<7> of up to <c>${config.maxRounds}<7> -- one life each.")
        launchGlobal { update() }
    }

    /**
     * Result when the match clock expires mid-series: most rounds won, then most
     * players alive in the round underway, then most eliminations, then a draw.
     */
    fun findWinnerOnTimeout(): EliminationTeam? {
        val first = getTeamByDef(TDMTeamDef.ODA)
        val second = getTeamByDef(TDMTeamDef.SENTINEL)
        return when {
            first.roundWins != second.roundWins ->
                if (first.roundWins > second.roundWins) first else second

            first.aliveCount != second.aliveCount ->
                if (first.aliveCount > second.aliveCount) first else second

            first.kills != second.kills ->
                if (first.kills > second.kills) first else second

            else -> null
        }
    }

    fun win(winner: EliminationTeam?) {
        if (state == EliminationMiniGame.STATE_VOTE) return
        state = EliminationMiniGame.STATE_VOTE
        // Hand every camera back before the vote screen opens, or eliminated
        // players would vote while still bolted to a team-mate's shoulder.
        SpectatorManager.stopAll(this)
        if (winner == null) {
            sendTitle("DRAW", "GAME HAS ENDED")
            sendFormattedMessage("\n<c><b>Game Over!</b></c>\n<7>Neither team was left standing. It was a draw!\n")
            return
        }
        val other = if (winner.def == TDMTeamDef.ODA) getTeamByDef(TDMTeamDef.SENTINEL) else getTeamByDef(TDMTeamDef.ODA)
        sendTitle("${winner.name} WINS", "GAME HAS ENDED")
        sendFormattedMessage(
            "\n<c><b>Game Over!</b></c>\n<7>Team <${winner.def.color}>${winner.name}</${winner.def.color}> " +
                "<7>takes the series <c>${winner.roundWins}</c><7>-<c>${other.roundWins}</c>!\n"
        )
        if (participantCount <= 1) return
        val experienceConfig = eliminationGame.progressionManager.experienceConfig.value
        winner.runForEachParticipant {
            val user = it.playerRef.userOrNull ?: return@runForEachParticipant
            launchGlobal {
                user.runLockGuarded {
                    user.recordWin(MoshpitGame.ELIMINATION)
                    eliminationGame.progressionManager.addXP(user, experienceConfig.elimination.teamWin)
                }
            }
        }
    }

    /** Repaints the scoreboard bar with alive counts. */
    suspend fun update() {
        val world = arena.world
        val first = getTeamByDef(TDMTeamDef.ODA)
        val second = getTeamByDef(TDMTeamDef.SENTINEL)
        world.context {
            runForEachParticipant {
                updateHud(it.playerRef) {
                    // Big label is the SERIES score; the small counts below it are
                    // players still alive in the round underway.
                    set("#FirstTeam.Text", "${first.name}: ${first.roundWins}")
                    set("#SecondTeam.Text", "${second.name}: ${second.roundWins}")
                    set("#FirstTeam.Style.TextColor", first.def.color)
                    set("#SecondTeam.Style.TextColor", second.def.color)
                    set("#FirstTeamPlayers.Text", first.aliveCount.toString())
                    set("#SecondTeamPlayers.Text", second.aliveCount.toString())
                    setTimeLeft(countdown?.remainingSeconds ?: -1)
                    set("#PointChangeTimeLeft.Visible", false)
                }
            }
        }
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
        playerRef.sendEventTitle("ELIMINATION", "ONE LIFE. NO RESPAWNS.", duration = 3.0f)
        return team
    }

    /**
     * Loadout selection cannot revive you.
     *
     * The base implementation re-runs the spawn strategy; for an eliminated
     * player that would teleport a spectator back into the fight.
     */
    override fun respawnWithLoadout(playerRef: PlayerRef) {
        if (state == EliminationMiniGame.STATE_VOTE) return
        val pair = getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        if ((pair.second as? EliminationParticipant)?.eliminated == true) return
        val player = playerRef.playerOrNull ?: return
        giveLoadout(player, playerRef)
        spawnStrategy.teleport(pair.first, pair.second)
    }

    override fun removeParticipant(playerRef: PlayerRef): MiniGameTeam? {
        // Leaving mid-match frees the camera and may well end the round -- a team
        // whose last member disconnects has lost just as surely as one wiped out.
        SpectatorManager.stopSpectating(playerRef)
        val team = super.removeParticipant(playerRef) ?: return null
        val player = playerRef.playerOrErr
        eliminationGame.engine.hudComposer.unregisterHUD(player, playerRef, MiniGameEngine.DEFAULT_HUD_KEY)
        (team as? EliminationTeam)?.let { SpectatorManager.retarget(it) }
        checkForWin()
        return team
    }

    private fun giveLoadout(player: Player, playerRef: PlayerRef) {
        if (state == EliminationMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        loadoutService.giveLoadout(player, playerRef)
        player.heal()
        val team = getTeamByUUID(playerRef.uuid) as? EliminationTeam
            ?: error("Team for ${playerRef.uuid} not found")
        equipTeamArmor(player, playerRef, team.def.armorPrefix)
    }
}
