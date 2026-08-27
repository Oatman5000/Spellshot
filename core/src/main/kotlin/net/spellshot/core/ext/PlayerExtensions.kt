package net.spellshot.core.ext

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.vector.Transform
import org.joml.Vector3d
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.EventTitleUtil
import org.slf4j.LoggerFactory

fun Store<EntityStore>.teleport(ref: Ref<EntityStore>, transform: Transform) {
    teleport(ref, ref.store.externalData.world, transform)
}

fun Store<EntityStore>.teleport(ref: Ref<EntityStore>, world: World, transform: Transform) {
    this.addComponent(
        ref,
        Teleport.getComponentType(),
        Teleport(world, Vector3d(transform.position), transform.rotation.clone())
    )
}

fun Store<EntityStore>.heal(ref: Ref<EntityStore>) {
    val comp = getComponent(ref, EntityStatMap.getComponentType())
    comp?.resetStatValue(DefaultEntityStatTypes.getHealth())
}

/**
 * Best-effort heal. A queued teleport can transiently clear the entity
 * reference, and healing is not important enough to abort participant setup --
 * this used to throw and skip everything after it in addParticipant().
 */
fun Player.heal() {
    val ref = reference ?: return
    ref.store.heal(ref)
}

fun PlayerRef.resetMovement() {
    val ref = referenceOrErr
    val movementManager =
        ref.store.getComponent(ref, MovementManager.getComponentType()) ?: error("Player has no MovementManager!")
    movementManager.resetDefaultsAndUpdate(ref, ref.store)
}

fun PlayerRef.changeBaseSpeedMultiplier(multiplier: Float) {
    val ref = referenceOrErr
    val movementManager =
        ref.store.getComponent(ref, MovementManager.getComponentType()) ?: error("Player has no MovementManager!")
    movementManager.settings.baseSpeed *= multiplier
    movementManager.update(this.packetHandler)
}

fun PlayerRef.changeBaseSpeed(speed: Float) {
    val ref = referenceOrErr
    val movementManager =
        ref.store.getComponent(ref, MovementManager.getComponentType()) ?: error("Player has no MovementManager!")
    movementManager.settings.baseSpeed = speed
    movementManager.update(this.packetHandler)
}

fun PlayerRef.teleport(transform: Transform) {
    val ref = reference ?: error("PlayerRef ${this.uuid} has no reference!")
    ref.store.teleport(ref, transform)
}

fun PlayerRef.teleport(world: World, transform: Transform) {
    val ref = reference ?: error("PlayerRef ${this.uuid} has no reference!")
    ref.store.teleport(ref, world, transform)
}

fun PlayerRef.play2DSound(soundKey: String, volumeMultiplier: Float = 1f, pitchMultiplier: Float = 1f) {
    try {
        val soundIndex = SoundEvent.getAssetMap().getIndex(soundKey)
        SoundUtil.playSoundEvent2dToPlayer(this, soundIndex, SoundCategory.SFX, volumeMultiplier, pitchMultiplier)
    } catch (ex: Exception) {
        logger.error("Error playing sound $soundKey to player ${this.username}: ${ex.message}")
    }
}

fun PlayerRef.play2DSound(soundIndex: Int, volumeMultiplier: Float = 1f, pitchMultiplier: Float = 1f) {
    SoundUtil.playSoundEvent2dToPlayer(this, soundIndex, SoundCategory.SFX, volumeMultiplier, pitchMultiplier)
}

val PlayerRef.referenceOrErr get() = reference ?: error("Player has no reference!")
val Player.referenceOrErr get() = reference ?: error("Player has no reference!")

val PlayerRef.playerOrErr: Player
    get() {
        val ref = referenceOrErr
        return ref.store.getComponent(ref, Player.getComponentType()) ?: error("Player not found!")
    }

val Player.playerRefOrErr: PlayerRef
    get() {
        val ref = referenceOrErr
        return ref.store.getComponent(ref, PlayerRef.getComponentType()) ?: error("PlayerRef not found!")
    }


private val logger = LoggerFactory.getLogger("ECS")

val PlayerRef.referenceOrNull: Ref<EntityStore>?
    get() {
        val ref = reference
        if (ref == null) {
            logger.error("Could not find reference for PlayerRef ${this.username}")
            return null
        }
        return ref
    }

val Player.referenceOrNull: Ref<EntityStore>?
    get() {
        val ref = reference
        if (ref == null) {
            // Must NOT use playerRefOrNull here: that getter calls back into this
            // one, so a null reference recursed between the two until the world
            // thread died with StackOverflowError (seen when a player changes world).
            logger.error("Could not find reference for Player $this")
            return null
        }
        return ref
    }

val PlayerRef.playerOrNull: Player?
    get() {
        val ref = referenceOrNull ?: return null
        val player = ref.store.getComponent(ref, Player.getComponentType())
        if (player == null) {
            logger.error("Could not find player component for PlayerRef ${this.username}")
            return null
        }
        return player
    }

val Player.playerRefOrNull: PlayerRef?
    get() {
        val ref = referenceOrNull ?: return null
        val playerRef = ref.store.getComponent(ref, PlayerRef.getComponentType())
        if (playerRef == null) {
            // Same trap as above, but self-recursive: this getter cannot log via
            // itself.
            logger.error("Could not find PlayerRef component for Player $this")
            return null
        }
        return playerRef
    }

fun PlayerRef.sendEventTitle(
    primaryMessage: String, secondaryMessage: String, isMajor: Boolean = false, duration: Float = 4.0F,
    fadeInDuration: Float = 1.0F, fadeOutDuration: Float = 1.0F
) {
    EventTitleUtil.showEventTitleToPlayer(
        this,
        Message.raw(primaryMessage),
        Message.raw(secondaryMessage),
        isMajor,
        null,
        duration,
        fadeInDuration,
        fadeOutDuration
    )
}
