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
    }, execute = { playerRef, player ->
        playerRef.play2DSound("SFX_Potion_Drink_Success", pitchMultiplier = 12f)
        playerRef.changeBaseSpeedMultiplier(1.4f)
        logger.debug("Speed boost applied to {}", playerRef.username)
        HytaleServer.SCHEDULED_EXECUTOR.schedule({
            logger.debug("Speed boost expired for {}", playerRef.username)
            if (!playerRef.isValid) return@schedule
            playerRef.referenceOrErr.store.externalData.world.execute {
                playerRef.resetMovement()
            }
        }, 5, TimeUnit.SECONDS)
    }),
}
