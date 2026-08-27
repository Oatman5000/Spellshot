package net.spellshot.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.EnumCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig
import com.hypixel.hytale.server.core.util.TargetUtil
import net.spellshot.core.framework.InteractionBinder
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.ext.property
import org.springframework.stereotype.Service
import java.time.Instant

class ThrowExplosiveInteraction(
    var projectileConfigId: String = "",
    var explosionParticleId: String = "",
    var explosionSoundId: String = "",
    var damageRadius: Double = 8.0,
    var damageAmount: Double = 8.0,
    var damageFalloff: Double = 0.5,
    var timerInMillis: Long? = null,
    var explosiveType: ThrowExplosiveType = ThrowExplosiveType.TIMED
) : SimpleInstantInteraction() {


    override fun firstRun(
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler
    ) {
        val ref = context.entity
        val playerRef = context.entity.playerRefOrNull ?: run {
            logger.warn("Explosive used by a non-player entity; ignoring")
            return
        }
        val buffer = context.commandBuffer ?: run {
            logger.warn("Explosive: no command buffer for {}", playerRef.username)
            return
        }
        val projectileConfig = ProjectileConfig.getAssetMap().getAsset(projectileConfigId) ?: run {
            logger.error("Explosive: no ProjectileConfig named '{}'", projectileConfigId)
            return
        }
        // Bound to a local so it smart-casts. Checking the property and then reading it
        // again below needed a !!, because this is a `var` on the interaction config.
        val fuseMillis = timerInMillis ?: run {
            logger.error("Explosive '{}' has no timerInMillis; it can never detonate", projectileConfigId)
            return
        }

        // Second click on an already-cooking grenade: throw it, keeping the fuse that
        // has been burning in your hand.
        val cooking = CookingExplosives.take(playerRef.uuid)
        if (cooking != null) {
            throwIt(ref, playerRef, buffer, projectileConfig, cooking.explodeAt)
            return
        }

        // First click: consume the item and start the fuse in hand. The grenade is
        // taken from the hotbar now, deliberately -- it is committed the moment it is
        // pulled, so it cannot be un-cooked by swapping slots or dropping it.
        val itemContext = context.createHeldItemContext() ?: run {
            logger.warn("Explosive: no held item context for {}", playerRef.username)
            return
        }
        val item = itemContext.itemStack
        if (item.quantity > 1) {
            itemContext.container.setItemStackForSlot(itemContext.slot, item.withQuantity(item.quantity - 1))
        } else {
            itemContext.container.removeItemStackFromSlot(itemContext.slot)
        }

        CookingExplosives.start(
            playerRef.uuid,
            CookingExplosive(
                explodeAt = Instant.now().plusMillis(fuseMillis),
                projectileConfigId = projectileConfigId,
                explosionParticleId = explosionParticleId,
                explosionSoundId = explosionSoundId,
                damageRadius = damageRadius,
                damageAmount = damageAmount,
                damageFalloff = damageFalloff,
                type = explosiveType
            )
        )
        logger.debug("{} started cooking a {} ({}ms fuse)", playerRef.username, projectileConfigId, fuseMillis)
    }

    /**
     * Spawns the projectile and attaches the fuse.
     *
     * The item is NOT consumed here -- it was taken when the fuse started. It used to
     * be removed from the hotbar immediately before this call with the spawn result
     * never checked, so a failed spawn ate the grenade and threw nothing, which is the
     * "sometimes it just doesn't throw" report.
     */
    private fun throwIt(
        ref: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        playerRef: com.hypixel.hytale.server.core.universe.PlayerRef,
        buffer: com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        projectileConfig: com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig,
        explodeAt: Instant,
    ) {
        val lookVec = TargetUtil.getLook(ref, buffer)
        val projectile = ProjectileModule.get()
            .spawnProjectile(ref, buffer, projectileConfig, lookVec.position, lookVec.direction)
        if (projectile == null) {
            logger.error("Explosive: spawnProjectile returned null for '{}'", projectileConfigId)
            return
        }
        buffer.addComponent(
            projectile, ThrowExplosiveComponent.componentType, ThrowExplosiveComponent(
                damager = playerRef.uuid,
                explodeAt = explodeAt,
                explosionParticleId = explosionParticleId,
                explosionSoundId = explosionSoundId,
                damageRadius = damageRadius,
                damageAmount = damageAmount,
                damageFalloff = damageFalloff,
                type = explosiveType
            )
        )
    }

    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ThrowExplosiveInteraction::class.java)
    }

    @Service
    class Binder : InteractionBinder<ThrowExplosiveInteraction>(
        ThrowExplosiveInteraction::class, ::ThrowExplosiveInteraction, {
            BuilderCodec.builder(
                ThrowExplosiveInteraction::class.java, { ThrowExplosiveInteraction() }, CODEC
            )
                .property(
                    ThrowExplosiveInteraction::projectileConfigId,
                    Codec.STRING
                ) { addValidator(ProjectileConfig.VALIDATOR_CACHE.validator.late()) }
                .property(
                    ThrowExplosiveInteraction::timerInMillis,
                    Codec.LONG
                )
                .property(
                    ThrowExplosiveInteraction::damageRadius,
                    Codec.DOUBLE
                )
                .property(
                    ThrowExplosiveInteraction::explosionParticleId,
                    Codec.STRING
                )
                .property(
                    ThrowExplosiveInteraction::explosionSoundId,
                    Codec.STRING
                )
                .property(
                    ThrowExplosiveInteraction::damageAmount,
                    Codec.DOUBLE
                )
                .property(
                    ThrowExplosiveInteraction::damageFalloff,
                    Codec.DOUBLE
                )
                .property(
                    ThrowExplosiveInteraction::explosiveType,
                    EnumCodec(ThrowExplosiveType::class.java)
                )
        }
    )
}
