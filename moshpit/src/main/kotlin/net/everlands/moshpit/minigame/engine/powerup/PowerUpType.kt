package net.everlands.moshpit.minigame.engine.powerup

import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.entity.entities.BlockEntity
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.*
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Shared by the enum constants' lambdas, which cannot see an instance logger.
 * These were `println` calls, which bypass the log config entirely: no level, no
 * timestamp, and no way to turn them off on a busy server.
 */
private val logger = LoggerFactory.getLogger("everlands.powerups")

enum class PowerUpType(
    val respawnTimeInSeconds: Long, val componentModifier: (holder: Holder<EntityStore>) -> Unit = {},
    val execute: (playerRef: PlayerRef, player: Player) -> Unit
) {

    HEALTH(30, {
        it.addComponent(BlockEntity.getComponentType(), BlockEntity("Potion_Health_Large"))
    }, execute = { playerRef, player ->
        player.heal()
        playerRef.play2DSound("SFX_Potion_Drink_Success", pitchMultiplier = 12f)
    }),
    SPEED(30, {
        it.addComponent(BlockEntity.getComponentType(), BlockEntity("Potion_Morph_Mouse"))
    }, execute = { playerRef, _ ->
        playerRef.play2DSound("SFX_Potion_Drink_Success", pitchMultiplier = 12f)
        SpeedBoost.apply(playerRef)
    }),
}

/**
 * The speed power-up, made non-compounding.
 *
 * `changeBaseSpeedMultiplier` is `baseSpeed *= multiplier`, and this used to call it
 * once per pickup with a bare five second timer each time. Two orbs inside that
 * window multiplied twice -- 1.4 x 1.4 = 1.96 -- three gave 2.74, and so on with no
 * ceiling. Then the FIRST timer fired and reset movement outright, cutting the later
 * boosts short. That is the "sometimes insane, most of the time fine" report: it
 * depended entirely on whether you grabbed a second orb within five seconds.
 *
 * Two changes fix it.
 *
 * Reset before applying, so the multiplier always lands on the true base speed and
 * the result is exactly 1.4x however many orbs you take. It also self-heals: a
 * player whose speed was left modified by a crash or a missed timer is corrected on
 * their next pickup rather than compounding from a bad value.
 *
 * A generation token, so only the most recent pickup clears the boost. Overlapping
 * orbs now EXTEND the duration instead of the earlier one cancelling the later.
 */
private object SpeedBoost {

    private const val MULTIPLIER = 1.4f
    private const val DURATION_SECONDS = 5L

    /** Player -> the pickup that currently owns their boost. Only it may clear. */
    private val generation = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()

    fun apply(playerRef: PlayerRef) {
        val uuid = playerRef.uuid
        val mine = generation.merge(uuid, 1L) { old, _ -> old + 1L } ?: 1L

        runCatching {
            playerRef.resetMovement()
            playerRef.changeBaseSpeedMultiplier(MULTIPLIER)
        }.onFailure {
            generation.remove(uuid)
            logger.warn("Could not apply the speed boost to {}: {}", playerRef.username, it.message)
            return
        }
        logger.debug("Speed boost applied to {} (generation {})", playerRef.username, mine)

        HytaleServer.SCHEDULED_EXECUTOR.schedule({
            // A later pickup has taken ownership; that one's timer will clear it.
            if (generation[uuid] != mine) return@schedule
            generation.remove(uuid)
            if (!playerRef.isValid) return@schedule
            logger.debug("Speed boost expired for {}", playerRef.username)
            runCatching {
                playerRef.referenceOrErr.store.externalData.world.execute {
                    runCatching { playerRef.resetMovement() }
                        .onFailure { logger.warn("Could not reset speed for {}: {}", playerRef.username, it.message) }
                }
            }.onFailure { logger.warn("Could not schedule the speed reset: {}", it.message) }
        }, DURATION_SECONDS, TimeUnit.SECONDS)
    }
}
