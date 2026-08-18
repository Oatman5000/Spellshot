package net.everlands.moshpit.item.interaction.throwexplosive

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
import net.everlands.core.framework.InteractionBinder
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.property
import org.slf4j.LoggerFactory
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
        val playerRef = context.entity.playerRefOrNull ?: return
        val buffer = context.commandBuffer ?: return
        val lookVec = TargetUtil.getLook(ref, buffer)
        val projectileConfig = ProjectileConfig.getAssetMap().getAsset(projectileConfigId) ?: return
        if (timerInMillis == null) {
            return
        }
        val itemContext = context.createHeldItemContext() ?: return
        val item = itemContext.itemStack
        if (item.quantity > 1) {
            itemContext.container.setItemStackForSlot(itemContext.slot, item.withQuantity(item.quantity - 1))
        } else {
            itemContext.container.removeItemStackFromSlot(itemContext.slot)
        }
        val projectile = ProjectileModule.get()
            .spawnProjectile(ref, buffer, projectileConfig, lookVec.position, lookVec.direction)
        buffer.addComponent(
            projectile, ThrowExplosiveComponent.componentType, ThrowExplosiveComponent(
                damager = playerRef.uuid,
                explodeAt = Instant.now().plusMillis(timerInMillis!!),
                explosionParticleId = explosionParticleId,
                explosionSoundId = explosionSoundId,
                damageRadius = damageRadius,
                damageAmount = damageAmount,
                damageFalloff = damageFalloff,
                type = explosiveType
            )
        )
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

    companion object {
        private val logger = LoggerFactory.getLogger(ThrowExplosiveInteraction::class.java)
    }
}
