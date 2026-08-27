package net.spellshot.moshpit.minigame.oitc

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.ext.heal
import net.spellshot.core.ext.playerOrErr
import net.spellshot.core.ext.playerOrNull
import net.spellshot.core.ext.referenceOrNull
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.context
import net.spellshot.core.framework.launchGlobal
import net.spellshot.moshpit.item.ammo.AmmoProvider
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.MiniGameKillEvent
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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * One in the Chamber.
 *
 * Free-for-all, timed, most kills wins -- the same shape as [net.spellshot.moshpit.minigame.ffa.FFAMiniGame]
 * so it drops into the existing rotation and vote flow. What differs is the kit:
 *
 *  * everyone gets the same gun and knife, ignoring their saved loadout entirely
 *  * you spawn with a single bullet and reloading is disabled ([OITCRules])
 *  * any bullet that connects is lethal, wherever it hits ([net.spellshot.moshpit.minigame.engine.handler.MiniGameDamageHandler])
 *  * a kill refunds a bullet, so accuracy is the only thing keeping you armed
 *  * miss, and you are on the knife until you land one
 *
 * The knife keeps its normal damage, so it takes several swings -- it is the
 * punishment for missing, not a second one-shot weapon.
 */
@Service
class OITCMiniGame(
    engine: MiniGameEngine,
    val moshpitManager: MoshpitManager,
    val progressionManager: ProgressionManager,
    val ammoProvider: AmmoProvider,
    val oitcConfig: OITCConfig.Binder,
) : MiniGame<OITCMiniGameSession>(engine) {

    init {
        engine.registerGame(this)
    }

    /**
     * No power-ups.
     *
     * The whole mode rests on "one bullet kills, and you only have one". A health
     * or damage pickup does not make that more interesting, it makes it noise.
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
                OITCCountdown(
                    session as OITCMiniGameSession,
                    moshpitManager.config.value.getGameConfig(MoshpitGame.OITC).duration
                )
            }),
        STATE_VOTE to MiniGameStateConfig(
            pvp = false,
            countdownSupplier = { session ->
                MoshpitVoteCountdown(
                    moshpitManager,
                    session as OITCMiniGameSession,
                    moshpitManager.config.value.voteDuration
                )
            }
        )
    )

    override fun createSession(arena: MiniGameArena): OITCMiniGameSession {
        val session = OITCMiniGameSession(this, arena, ammoProvider, oitcConfig)
        sessions.add(session)
        return session
    }
}

class OITCMiniGameSession(
    game: OITCMiniGame,
    arena: MiniGameArena,
    val ammoProvider: AmmoProvider,
    val oitcConfig: OITCConfig.Binder,
) : MiniGameSession(game, arena) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val teamStrategy: TeamStrategy = SingleTeamPerPlayerTeamStrategy(
        this, participantProvider = { OITCParticipant(it) }
    )

    /** Reuses the FFA scoreboard -- it is a generic "name / kills" list. */
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

    val topList: List<OITCParticipant>
        get() = teams.mapNotNull { it.participants.values.firstOrNull() as? OITCParticipant }
            .sortedByDescending { it.kills }.take(10)

    init {
        subscribeToEvent<MiniGameRespawnEvent> { event ->
            val participant = event.participant
            val ref = participant.playerRef.reference ?: error("Player ref ${participant.playerRef.uuid} not found")
            val player = participant.playerRef.reference?.store?.getComponent(ref, Player.getComponentType())
                ?: error("Player entity for ref ${participant.playerRef.uuid} not found")
            // Deferred to the world thread for the same reason FFA defers it:
            // this runs inside RespawnSystems' onComponentRemoved callback, and
            // mutating inventory containers while the Store is processing is what
            // made items vanish from the hotbar on respawn.
            val world = ref.store.externalData.world
            world.execute { giveKit(player, participant.playerRef) }
            val spawn = spawnStrategy.getSpawn(event.team, participant) ?: return@subscribeToEvent
            event.buffer.addComponent(
                ref,
                Teleport.getComponentType(),
                Teleport(world, spawn.position, spawn.rotation)
            )
        }

        subscribeToEvent<MiniGameKillEvent> { event ->
            val killerParticipant = event.killerParticipant
            val world = arena.world
            // Only RECORD the refund here. Paying it out immediately raced the
            // shot's own ammo bookkeeping -- see reconcileAmmo.
            (killerParticipant as? OITCParticipant)?.let {
                it.bulletsOwed += oitcConfig.value.bulletsPerKill
            }
            launchGlobal {
                killerParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.OITC, "kills", 1)
                        }
                        game.progressionManager.addXP(this, game.progressionManager.experienceConfig.value.xpPerKill)
                    }
                }
                event.entityParticipant.playerRef.userOrNull?.run {
                    runLockGuarded {
                        applyStats {
                            incrementMiniGame(MoshpitGame.OITC, "deaths", 1)
                        }
                    }
                }
                world.context {
                    refreshHud()
                }
            }
        }
    }

    fun findWinner(): OITCParticipant? {
        return teams.flatMap { it.participants.values }
            .maxByOrNull { it as OITCParticipant; it.kills } as OITCParticipant?
    }

    override fun addParticipant(playerRef: PlayerRef, teleport: Boolean): MiniGameTeam? {
        val team = super.addParticipant(playerRef, teleport) ?: return null
        val participant = team.participants[playerRef.uuid] ?: error("Participant ${playerRef.uuid} not found in team")
        val player = playerRef.playerOrErr
        giveKit(player, playerRef)
        // Deliberately no UnlimitedAmmoComponent, unlike every other mode. That
        // component makes ReloadInteraction refill for free; here the bullet
        // count IS the resource.
        if (teleport) {
            spawnStrategy.teleport(team, participant)
        }
        player.heal()
        playerRef.sendEventTitle("ONE IN THE CHAMBER", "ONE BULLET. MAKE IT COUNT.", duration = 3.0f)
        return team
    }

    override fun respawnWithLoadout(playerRef: PlayerRef) {
        if (state == OITCMiniGame.STATE_VOTE) return
        val pair = getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        val player = playerRef.playerOrNull ?: return
        giveKit(player, playerRef)
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

    /**
     * The fixed OitC kit: gun in slot 0, knife in slot 1, nothing else.
     *
     * The player's saved loadout is ignored on purpose -- LoadoutService is never
     * consulted. Everyone is on identical equipment, which is the point of the
     * mode, and it also means an unlocked-weapon check can never hand someone an
     * assault rifle here.
     */
    private fun giveKit(player: Player, playerRef: PlayerRef) {
        if (state == OITCMiniGame.STATE_VOTE) {
            player.heal()
            return
        }
        val config = oitcConfig.value
        player.inventory.clear()
        val hotbar = player.inventory.hotbar
        if (hotbar == null) {
            logger.warn("no hotbar for ${playerRef.username}, cannot give OitC kit")
            player.heal()
            return
        }
        runCatching {
            // Built with the starting bullet count rather than a full magazine --
            // buildItemStackWithAmmo stamps the per-stack ammoId the provider keys
            // its cache on, so this is what makes "1 bullet" stick.
            hotbar.addItemStack(
                ammoProvider.buildItemStackWithAmmo(ItemStack(config.gunItemId), config.startingBullets)
            )
        }.onFailure { logger.warn("Could not give ${config.gunItemId} to ${playerRef.username}: ${it.message}") }
        runCatching {
            hotbar.addItemStack(ItemStack(config.meleeItemId))
        }.onFailure { logger.warn("Could not give ${config.meleeItemId} to ${playerRef.username}: ${it.message}") }

        val ref = player.referenceOrNull
        if (ref != null) ammoProvider.setClientAmmo(ref, config.startingBullets)
        player.heal()
    }

    /**
     * Refunds a bullet to the killer.
     *
     * Scans the hotbar for the OitC gun rather than assuming slot 0, because the
     * player may have picked things up or reordered. Capped at [OITCConfig.maxBullets],
     * defaulting to the gun's own magazine size so the ammo readout never shows a
     * number above its denominator.
     */
    /**
     * Single owner of the gun's ammo, run every tick by [OITCAmmoWatcher].
     *
     * Both halves of the mode live here on purpose. They used to be split -- the
     * kill event paid out the bullet immediately while the watcher confiscated an
     * empty gun -- and the two raced each other, which is why a kill sometimes
     * left you with nothing.
     *
     * The race is in ShootInteraction. It decrements ammo synchronously on line
     * 57, but a KILLING shot defers its damage through `world.execute`, so the
     * kill event lands a tick or more later. Meanwhile the shot's own
     * `ChangeStat` step is still adjusting the client's GunAmmo stat. Paying the
     * refund in that window could set the stack to 1 and then have the trailing
     * -1 stat change leave the client believing it was empty: gun in hand,
     * nothing to fire.
     *
     * Recording the debt and settling it here, one watcher tick later, means the
     * shot has fully finished before anything is credited, and one place decides
     * whether the gun is armed, confiscated or handed back.
     */
    fun reconcileAmmo(player: Player, playerRef: PlayerRef) {
        if (state == OITCMiniGame.STATE_VOTE) return
        val participant = getTeamAndParticipantByUUID(playerRef.uuid)?.second as? OITCParticipant ?: return
        val hotbar = player.inventory.hotbar ?: return
        val config = oitcConfig.value
        val ref = player.referenceOrNull

        var slot = -1
        var stack: ItemStack? = null
        for (i in 0 until HOTBAR_SCAN_SLOTS) {
            val candidate = runCatching { hotbar.getItemStack(i.toShort()) }.getOrNull() ?: continue
            if (candidate.itemId == config.gunItemId) {
                slot = i
                stack = candidate
                break
            }
        }

        val owed = participant.bulletsOwed
        if (owed > 0) {
            participant.bulletsOwed = 0
            val current = stack
            if (current == null) {
                // Gun was confiscated when it ran dry, so this was a knife kill.
                // Hand it back loaded -- the whole loop of the mode.
                runCatching {
                    hotbar.addItemStack(
                        ammoProvider.buildItemStackWithAmmo(ItemStack(config.gunItemId), owed)
                    )
                    ref?.let { ammoProvider.setClientAmmo(it, owed) }
                }.onFailure { logger.warn("Could not return the gun to ${playerRef.username}: ${it.message}") }
            } else {
                val cap = config.maxBullets ?: (ammoProvider.getMaxAmmo(current) ?: DEFAULT_MAX_BULLETS)
                val next = ((ammoProvider.getAmmo(current) ?: 0) + owed).coerceAtMost(cap)
                runCatching {
                    ammoProvider.setAmmo(hotbar, slot.toShort(), current, next)
                    ref?.let { ammoProvider.setClientAmmo(it, next) }
                }.onFailure { logger.warn("Could not credit a bullet to ${playerRef.username}: ${it.message}") }
            }
            return
        }

        // Nothing owed: take the gun away once the chamber is empty. A null ammo
        // reading means the provider has not seen this stack yet, which is not
        // the same as empty, so it is left alone.
        val current = stack ?: return
        val ammo = ammoProvider.getAmmo(current) ?: return
        if (ammo <= 0) {
            runCatching { hotbar.removeItemStackFromSlot(slot.toShort()) }
                .onFailure { logger.warn("Could not remove the empty gun: ${it.message}") }
            return
        }

        // Pin the client's GunAmmo stat to the authoritative cache.
        //
        // The revolver spends GunAmmo TWICE per shot: Revolver.json gates Primary
        // on a StatsCondition with "Costs": {"GunAmmo": 1}, and Revolver_Shoot
        // then chains a ChangeStat of -1. ShootInteraction meanwhile decrements
        // the provider cache by exactly 1, so the stat drains at double the rate
        // of the real bullet count.
        //
        // Every other mode hides this because UnlimitedAmmoComponent lets reload
        // refill the stat to max constantly. OitC has no reload, so the drift
        // accumulates: with four banked bullets the stat hits zero while two are
        // still in the chamber, StatsCondition fails, and Primary routes to
        // Revolver_Reload -- the "it just keeps reloading" jam.
        //
        // Corrected here rather than in the item assets because those are shared
        // with every other gun and mode; re-pointing the stat at the cache each
        // tick keeps the fix contained to OitC.
        ref?.let { ammoProvider.setClientAmmo(it, ammo) }
    }


    private companion object {
        /** Hotbar slots to search for the gun. Matches the selector's four class slots plus the reward slot. */
        const val HOTBAR_SCAN_SLOTS = 5

        /** Used only if the configured gun has no 'ammo' tag to read a magazine size from. */
        const val DEFAULT_MAX_BULLETS = 4
    }
}

class OITCParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef) {

    /**
     * Bullets earned but not yet handed over.
     *
     * The kill event only records the debt; [OITCMiniGameSession.reconcileAmmo]
     * pays it on the next watcher tick. That indirection is deliberate -- see the
     * note on reconcileAmmo for the race it avoids.
     */
    var bulletsOwed: Int = 0
}
