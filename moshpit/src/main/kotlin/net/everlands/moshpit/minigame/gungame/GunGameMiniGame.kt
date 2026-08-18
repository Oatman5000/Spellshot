package net.everlands.moshpit.minigame.gungame

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.core.ext.heal
import net.everlands.core.ext.playerOrErr
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.referenceOrNull
import net.everlands.core.ext.sendEventTitle
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.context
import net.everlands.core.framework.launchGlobal
import net.everlands.moshpit.item.ammo.AmmoProvider
import net.everlands.moshpit.item.ammo.hytale.UnlimitedAmmoComponent
import net.everlands.moshpit.minigame.engine.MiniGameDeathEvent
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.MiniGameKillEvent
import net.everlands.moshpit.minigame.engine.MiniGameRespawnEvent
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.ext.setTimeLeft
import net.everlands.moshpit.minigame.engine.model.*
import net.everlands.moshpit.minigame.engine.strategy.team.SingleTeamPerPlayerTeamStrategy
import net.everlands.moshpit.minigame.engine.strategy.team.TeamStrategy
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.moshpit.MoshpitManager
import net.everlands.moshpit.moshpit.vote.MoshpitVoteCountdown
import net.everlands.moshpit.progression.ProgressionManager
import net.everlands.moshpit.progression.applyStats
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Gun Game.
 *
 * Timed free-for-all scored on total kills, with a weapon ladder layered on top:
 * you carry exactly one weapon, each kill promotes you to the next one up
 * ([GunGameConfig.ladder]), and dying drops you all the way back to the knife.
 *
 * Note the ladder is NOT the scoreboard. The winner is whoever has the most
 * kills when the clock runs out, exactly like FFA -- reaching the Famas is a
 * reward for a streak, not a win condition. That also means the tier reset on
 * death costs you firepower but never costs you points.
 */
@Service
class GunGameMiniGame(
    engine: MiniGameEngine,
    val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager,
    val ammoProvider: AmmoProvider,
    val gunGameConfig: GunGameConfig.Binder,
) : MiniGame<GunGameMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    /**
     * No power-ups.
     *
     * Your weapon is dictated entirely by your current streak; a pickup that
     * hands out damage or health cuts across the one thing the mode is about.
     */
    override val powerUps: Boolean = false

    companion object {
        const val STATE_IN_GAME = 0
        const val STATE_VOTE = 1
    }

    override val states: Map<Int, MiniGameStateConfig> = mapOf(
        STATE_IN_GAME to MiniGameStateConfig(
            pvp = true,
            countdownSupplier = { session ->
                GunGameCountdown(
                    session as GunGameMiniGameSession,
                    moshpitManager.config.value.getGameConfig(MoshpitGame.GUNGAME).duration
                )
            }
        ),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as GunGameMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): GunGameMiniGameSession {
        val session = GunGameMiniGameSession(this, arena, ammoProvider, gunGameConfig)
        sessions.add(session)
        return session
    }
}

class GunGameMiniGameSession(
    game: GunGameMiniGame,
    arena: MiniGameArena,
    val ammoProvider: AmmoProvider,
    val gunGameConfig: GunGameConfig.Binder,
) : MiniGameSession(game, arena) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val teamStrategy: TeamStrategy = SingleTeamPerPlayerTeamStrategy(
        this, participantProvider = { GunGameParticipant(it) }
    )

    /** FFA's scoreboard, with the tier shown next to each name. */
    override var hudProvider: ((playerRef: PlayerRef, builder: UICommandBuilder) -> Unit)? = { _, builder ->
        builder.append("Hud/FFA.ui")
        topList.forEachIndexed { index, participant ->
            builder.append("#Rows", "Hud/FFAScoreboardRow.ui")
            builder.set("#Rows[$index] #Placement.Text", "#${index + 1}")
            builder.set("#Rows[$index] #Name.Text", participant.playerRef.username)
            builder.set("#Rows[$index] #Kills.Text", participant.kills.toString())
        }
        builder.setTimeLeft(countdown?.remainingSeconds ?: -1)
    }

    val topList: List<GunGameParticipant>
        get() = teams.mapNotNull { it.participants.values.firstOrNull() as? GunGameParticipant }
            .sortedByDescending { it.kills }.take(10)

    init {
        subscribeToEvent<MiniGameRespawnEvent> { event ->
            val participant = event.participant
            val ref = participant.playerRef.reference ?: error("Player ref ${participant.playerRef.uuid} not found")
            val player = participant.playerRef.reference?.store?.getComponent(ref, Player.getComponentType())
                ?: error("Player entity for ref ${participant.playerRef.uuid} not found")
            // Deferred to the world thread for the same reason every other mode
            // defers it: this runs inside RespawnSystems' onComponentRemoved while
            // the Store is processing, and mutating inventory there is what made
            // items vanish from the hotbar.
            val world = ref.store.externalData.world
            world.execute { giveTierWeapon(player, participant.playerRef) }
            val spawn = spawnStrategy.getSpawn(event.team, participant)
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }

        // Reset on death rather than on being killed, so falling, the airstrike
        // and self-damage demote you too. The weapon itself is re-issued by the
        // respawn subscriber above, which runs a moment later.
        subscribeToEvent<MiniGameDeathEvent> { event ->
            (event.participant as? GunGameParticipant)?.tier = 0
        }

        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val world = arena.world
            world.execute { promote(killerParticipant) }
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.GUNGAME, "kills", 1)
                        }
                        game.progressionManager.addXP(this, game.progressionManager.experienceConfig.value.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.GUNGAME, "deaths", 1)
                        }
                    }
                }
                world.context { refreshHud() }
            }
        }
    }

    fun findWinner(): GunGameParticipant? {
        return teams.flatMap { it.participants.values }
            .maxByOrNull { it as GunGameParticipant; it.kills } as GunGameParticipant?
    }

    /**
     * Moves the killer one rung up the ladder and swaps their weapon.
     *
     * Already at the top: no promotion, no title card, they simply keep the
     * Famas and carry on scoring.
     */
    private fun promote(participant: MiniGameParticipant) {
        val gunGameParticipant = participant as? GunGameParticipant ?: return
        val config = gunGameConfig.value
        if (gunGameParticipant.tier >= config.topTier) return
        gunGameParticipant.tier += 1
        val player = participant.playerRef.playerOrNull ?: return
        giveTierWeapon(player, participant.playerRef)

        val weapon = config.weaponFor(gunGameParticipant.tier) ?: return
        if (config.announcePromotions) {
            participant.playerRef.sendEventTitle(
                weapon.uppercase(),
                "WEAPON ${gunGameParticipant.tier + 1} OF ${config.ladder.size}",
                duration = 1.5f
            )
        }
        if (config.announceFinalTier && gunGameParticipant.tier == config.topTier) {
            sendFormattedMessage("<c>${participant.playerRef.username}<7> reached the final weapon: <c>$weapon<7>!")
        }
    }

    /**
     * Replaces the inventory with exactly the current tier's weapon.
     *
     * The whole inventory is cleared first -- "locked to one gun" means the
     * previous rung has to go, otherwise a player would accumulate the entire
     * ladder and simply pick their favourite.
     */
    private fun giveTierWeapon(player: Player, playerRef: PlayerRef) {
        if (state == GunGameMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        val config = gunGameConfig.value
        val participant = getTeamAndParticipantByUUID(playerRef.uuid)?.second as? GunGameParticipant
        val tier = participant?.tier ?: 0
        val weapon = config.weaponFor(tier)
        if (weapon == null) {
            logger.warn("Gun Game ladder is empty, cannot arm ${playerRef.username}")
            return
        }
        val hotbar = player.inventory.hotbar
        if (hotbar == null) {
            logger.warn("no hotbar for ${playerRef.username}, cannot give tier weapon")
            return
        }
        player.inventory.clear()
        runCatching {
            val stack = ItemStack(weapon)
            // Full magazine on issue. Guns get an ammo tag, the knife does not,
            // and buildItemStackWithAmmo is what stamps the per-stack ammoId the
            // provider keys its cache on.
            val maxAmmo = ammoProvider.getMaxAmmo(stack)
            hotbar.addItemStack(if (maxAmmo != null) ammoProvider.buildItemStackWithAmmo(stack, maxAmmo) else stack)
            val ref = player.referenceOrNull
            if (ref != null && maxAmmo != null) ammoProvider.setClientAmmo(ref, maxAmmo)
        }.onFailure { logger.warn("Could not give $weapon to ${playerRef.username}: ${it.message}") }
    }

    override fun addParticipant(playerRef: PlayerRef, teleport: Boolean): MiniGameTeam? {
        val team = super.addParticipant(playerRef, teleport) ?: return null
        val participant = team.participants[playerRef.uuid] ?: error("Participant ${playerRef.uuid} not found in team")
        val player = playerRef.playerOrErr
        val ref = playerRef.referenceOrErr
        // Reloading stays free -- the ladder is the constraint here, not ammo.
        ref.store.ensureComponent(ref, UnlimitedAmmoComponent.componentType)
        giveTierWeapon(player, playerRef)
        if (teleport) {
            spawnStrategy.teleport(team, participant)
        }
        player.heal()
        playerRef.sendEventTitle("GUN GAME", "EVERY KILL UPGRADES YOUR WEAPON", duration = 3.0f)
        return team
    }

    /**
     * Loadout selection does nothing here.
     *
     * Your weapon comes from your tier, so re-running the spawn strategy would
     * teleport the player mid-match for no gain.
     */
    override fun respawnWithLoadout(playerRef: PlayerRef) = Unit

    override fun removeParticipant(playerRef: PlayerRef): MiniGameTeam? {
        val team = super.removeParticipant(playerRef) ?: return null
        val player = playerRef.playerOrErr
        refreshHud()
        player.inventory.clear()
        player.heal()
        return team
    }
}

class GunGameParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef) {

    /**
     * Index into [GunGameConfig.ladder]. 0 is the knife.
     *
     * Separate from [kills] on purpose: kills are the score and only ever go up,
     * while the tier resets to 0 on every death.
     */
    var tier: Int = 0
}
