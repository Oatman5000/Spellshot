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

        // One press is one throw. This used to be a two-click design -- click to cook,
        // click again to throw -- which could not work: the first click consumed the
        // grenade, so the player was holding nothing and no second click could ever
        // reach this interaction again. Grenades simply stopped being throwable.
        //
        // The fuse instead starts on the press, in CookExplosiveInteraction, and the
        // hold burns it down. Falling back to a full fuse keeps the grenade throwable
        // if the cook step is ever missing from an interaction chain, rather than
        // failing silently the way the two-click version did.
        val explodeAt = CookingExplosives.take(playerRef.uuid)?.explodeAt
            ?: Instant.now().plusMillis(fuseMillis)


        throwIt(ref, playerRef, buffer, projectileConfig, explodeAt, context)
    }

    /**
     * Spawns the projectile, attaches the fuse, and only then takes the grenade.
     *
     * Order matters. The hotbar item used to be removed immediately *before* the
     * spawn call, with the result never checked -- so a spawn that returned null ate
     * the grenade and threw nothing. That is the "sometimes it just doesn't throw"
     * report. Consuming last means a failed spawn costs the player nothing and leaves
     * a loud line in the log instead of a silent disappearance.
     */
    private fun throwIt(
        ref: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        playerRef: com.hypixel.hytale.server.core.universe.PlayerRef,
        buffer: com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        projectileConfig: com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig,
        explodeAt: Instant,
        context: InteractionContext,
    ) {
        val lookVec = TargetUtil.getLook(ref, buffer)
        val projectile = ProjectileModule.get()
            .spawnProjectile(ref, buffer, projectileConfig, lookVec.position, lookVec.direction)
        if (projectile == null) {
            logger.error(
                "Explosive: spawnProjectile returned null for '{}' - {} keeps the grenade",
                projectileConfigId, playerRef.username
            )
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

        val itemContext = context.createHeldItemContext() ?: run {
            // Thrown but not paid for. Worth a line: it means grenades are effectively
            // infinite, which is a balance bug rather than a crash and so would
            // otherwise only ever be noticed by players exploiting it.
            logger.warn("Explosive: no held item context for {}; grenade was not consumed", playerRef.username)
            return
        }
        val item = itemContext.itemStack
        if (item.quantity > 1) {
            itemContext.container.setItemStackForSlot(itemContext.slot, item.withQuantity(item.quantity - 1))
        } else {
            itemContext.container.removeItemStackFromSlot(itemContext.slot)
        }
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
