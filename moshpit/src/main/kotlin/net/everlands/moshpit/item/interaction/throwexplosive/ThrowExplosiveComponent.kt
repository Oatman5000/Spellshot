package net.everlands.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.codecs.EnumCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.ComponentDescriptor
import net.everlands.core.ext.builderCodec
import net.everlands.core.ext.property
import java.time.Instant
import java.util.*

class ThrowExplosiveComponent(
    var explodeAt: Instant? = null,
    var damager: UUID? = null,
    var damageRadius: Double? = null,
    var damageAmount: Double? = null,
    var damageFalloff: Double? = null,
    var explosionParticleId: String? = null,
    var explosionSoundId: String? = null,
    var type: ThrowExplosiveType? = null
) : Component<EntityStore> {

    var ticksAtZero = 0
    var lastVelocityLength: Double? = null

    companion object : ComponentDescriptor<ThrowExplosiveComponent>(
        ThrowExplosiveComponent::class.java,
        ::ThrowExplosiveComponent, {
            builderCodec(::ThrowExplosiveComponent)
                .property(ThrowExplosiveComponent::explodeAt, Codec.INSTANT)
                .property(ThrowExplosiveComponent::damager, Codec.UUID_BINARY)
                .property(ThrowExplosiveComponent::damageRadius, Codec.DOUBLE)
                .property(ThrowExplosiveComponent::explosionParticleId, Codec.STRING)
                .property(ThrowExplosiveComponent::explosionSoundId, Codec.STRING)
                .property(ThrowExplosiveComponent::damageAmount, Codec.DOUBLE)
                .property(ThrowExplosiveComponent::damageFalloff, Codec.DOUBLE)
                .property(ThrowExplosiveComponent::type, EnumCodec(ThrowExplosiveType::class.java))
        }
    )

    override fun clone() = ThrowExplosiveComponent(
        explodeAt = explodeAt,
        damager = damager,
        damageRadius = damageRadius,
        explosionParticleId = explosionParticleId,
        explosionSoundId = explosionSoundId,
        damageAmount = damageAmount,
        damageFalloff = damageFalloff,
        type = type
    )

}


