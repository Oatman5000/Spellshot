package net.spellshot.moshpit.minigame.gungame

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.ext.heal
import net.spellshot.core.ext.playerOrErr
import net.spellshot.core.ext.playerOrNull
import net.spellshot.core.ext.referenceOrErr
import net.spellshot.core.ext.referenceOrNull
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.context
import net.spellshot.core.framework.launchGlobal
import net.spellshot.moshpit.item.ammo.AmmoProvider
import net.spellshot.moshpit.item.ammo.hytale.UnlimitedAmmoComponent
import net.spellshot.moshpit.minigame.engine.MiniGameDeathEvent
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.MiniGameKillEvent
import net.spellshot.moshpit.minigame.engine.KillWeapon
import net.spellshot.moshpit.minigame.engine.KillWeapons
import net.spellshot.moshpit.minigame.engine.MiniGameRespawnEvent
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.ext.setTimeLeft
import net.spellshot.moshpit.minigame.engine.model.*
import net.spellshot.moshpit.minigame.engine.strategy.team.SingleTeamPerPlayerTeamStrategy
import net.spellshot.moshpit.minigame.engine.strategy.team.TeamStrategy
import net.spellshot.moshpit.moshpit.MoshpitGame
import net.spellshot.moshpit.moshpit.MoshpitManager
import net.spellshot.moshpit.moshpit.vote.MoshpitVoteCountdown
import net.spellshot.moshpit.progression.ProgressionManager
import net.spellshot.moshpit.progression.applyStats
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.moshpit.progression.recordWin
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Gun Game.
 *
 * Free-for-all with a weapon ladder ([GunGameConfig.ladder]). You carry one tier
 * weapon plus a permanent knife, and each kill moves you one rung up.
 *
 * **Completing the ladder wins the match outright.** The last two rungs are the
 * impact grenade and then the frag grenade, and those two only advance on a kill with
 * that exact explosive -- so the ladder ends on a deliberate act rather than on
 * whichever gun you happened to be holding. Every firearm rung accepts any kill,
 * knife included.
 *
 * Dying costs you ground rather than everything:
 *
 * | Killed by | Result |
 * |---|---|
 * | Knife | Back to tier 0 |
 * | Anything else | Back one rung |
 *
 * "Anything else" covers gunfire, grenades, the airstrike, falling, and your own
 * cooked frag. Only the knife is a full reset, which is what makes knifing worth the
 * risk when you could just shoot.
 *
 * The clock still runs, and if it expires with nobody finished the mode falls back to
 * the FFA rule: most kills wins. Kills are never lost, only rungs.
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
            val spawn = spawnStrategy.getSpawn(event.team, participant) ?: return@subscribeToEvent
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }

        // Demotion, and the record of what did it.
        //
        // MiniGameDeathHandler publishes the death event BEFORE the kill event in the
        // same tick, so this consumes the weapon mark and stashes it for the kill
        // subscriber below -- which needs to know what the killer used in order to
        // decide whether a grenade tier is satisfied.
        subscribeToEvent<MiniGameDeathEvent> { event ->
            val participant = event.participant as? GunGameParticipant ?: return@subscribeToEvent
            val weapon = KillWeapons.consume(participant.playerRef.uuid)
            participant.lastKilledBy = weapon
            val before = participant.tier
            participant.tier = if (weapon == KillWeapon.KNIFE) {
                0
            } else {
                // Everything that is not a knife costs one rung: gunfire, grenades,
                // the airstrike, falling off Rust, and your own cooked frag.
                (participant.tier - 1).coerceAtLeast(0)
            }
            if (participant.tier != before) {
                val reason = if (weapon == KillWeapon.KNIFE) "HUMILIATED" else "SET BACK"
                participant.playerRef.sendEventTitle(
                    reason,
                    "WEAPON ${participant.tier + 1} OF ${gunGameConfig.value.ladder.size}",
                    duration = 1.5f
                )
            }
        }

        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val world = arena.world
            val usedWeapon = (event.entityParticipant as? GunGameParticipant)?.lastKilledBy
            world.execute { promote(killerParticipant, usedWeapon) }
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
    /**
     * Advances [participant] one rung, if [usedWeapon] satisfies their current tier.
     *
     * Firearm tiers accept any kill -- including a knife kill, which is deliberate:
     * knifing someone both resets them and moves you up.
     *
     * The two grenade tiers are the exception and the whole point of them. They only
     * advance on a kill with that exact explosive, so the ladder ends with a genuine
     * skill gate rather than trailing off on whichever gun you happened to be holding.
     *
     * A kill at the top rung ends the match outright.
     */
    private fun promote(participant: MiniGameParticipant, usedWeapon: KillWeapon?) {
        val gunGameParticipant = participant as? GunGameParticipant ?: return
        val config = gunGameConfig.value

        val required = config.explosiveFor(gunGameParticipant.tier)
        if (required != null && usedWeapon != required) {
            // Not a failure worth a title card -- they killed someone, it just was not
            // with the right tool. Say so quietly so the tier does not look broken.
            participant.playerRef.sendRawMessage(
                "That tier needs a ${config.weaponFor(gunGameParticipant.tier)} kill."
            )
            return
        }

        if (gunGameParticipant.tier >= config.topTier) {
            win(gunGameParticipant)
            return
        }

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
     * Ends the match: someone completed the ladder.
     *
     * Moving to STATE_VOTE is how every mode here finishes -- it stops the countdown
     * and opens the map vote. Guarded against a double call, because two players
     * finishing in the same tick would otherwise both announce.
     */
    private fun win(winner: GunGameParticipant) {
        if (state == GunGameMiniGame.STATE_VOTE) return
        state = GunGameMiniGame.STATE_VOTE
        val username = winner.playerRef.username
        sendTitle("$username WINS", "COMPLETED THE LADDER")
        sendFormattedMessage(
            "\n<c><b>Game Over!</b></c>\n<7><c>$username</c><7> ran the whole ladder " +
                "and finished on the frag grenade!\n"
        )
        val user = winner.playerRef.userOrNull ?: return
        launchGlobal {
            // recordWin applies its own stats block; wrapping it in another would
            // nest the lock.
            user.runLockGuarded { user.recordWin(MoshpitGame.GUNGAME) }
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
            // The knife first, so it always sits in slot 1. Carried at every tier --
            // it is not a rung on the ladder, it is the permanent fallback, and the
            // thing that fully resets whoever you hit with it.
            config.sidearmItemId?.let { hotbar.addItemStack(ItemStack(it)) }

            if (config.isExplosiveTier(tier)) {
                // Grenades come as a stack rather than a single item, refreshed every
                // spawn. Running dry at a grenade tier would leave you unable to
                // advance at all, since only that explosive satisfies the rung.
                hotbar.addItemStack(ItemStack(weapon, config.grenadesPerLife))
                return@runCatching
            }

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

    /**
     * What killed this player most recently, stashed by the death subscriber.
     *
     * MiniGameKillEvent carries no damage cause, and the mark in [KillWeapons] is
     * single-shot, so the death subscriber -- which fires first -- reads it and parks
     * it here for the kill subscriber to decide whether a grenade tier was satisfied.
     */
    var lastKilledBy: KillWeapon? = null
}
