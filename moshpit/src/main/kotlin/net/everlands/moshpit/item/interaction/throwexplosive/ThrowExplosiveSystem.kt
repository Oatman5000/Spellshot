package net.everlands.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.transformOrNull
import net.everlands.core.ext.velocityOrNull
import net.everlands.core.ext.world
import net.everlands.core.functions.executeRadial
import net.everlands.core.ext.referenceOrNull
import java.time.Instant
import kotlin.math.abs

@EcsService
class ThrowExplosiveSystem : DelayedEntitySystem<EntityStore>(0.1f) {

    companion object {
        private const val MAX_ZERO_TICKS = 2
        private const val IMPACT_DELTA_THRESHOLD = 5.0
    }

    private val deps = setOf(
        SystemDependency(Order.BEFORE, StandardPhysicsTickSystem::class.java)
    )

    private val damageCause by lazy {
        DamageCause.getAssetMap().getIndex("Gun")
    }

    override fun getDependencies() = deps

    override fun tick(
        delta: Float, index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val ref = chunk.getReferenceTo(index)
        val comp = chunk.getComponent(index, ThrowExplosiveComponent.componentType) ?: return
        val transform = chunk.transformOrNull(index) ?: return
        val damagerUuid = comp.damager ?: return
        val playerRef = Universe.get().getPlayer(damagerUuid)
        if (playerRef == null) {
            buffer.removeEntity(ref, RemoveReason.REMOVE)
            return
        }
        if (playerRef.world != buffer.store.externalData.world) {
            buffer.removeEntity(ref, RemoveReason.REMOVE)
            return
        }
        val playerEntityRef = playerRef.referenceOrNull ?: return

        // Two unrelated fuse models share this tick: IMPACT watches velocity for a
        // sudden stop, TIMED just waits for a deadline. They have no code in common
        // beyond the guards above, so the natural shape is one strategy per type on
        // ThrowExplosiveType rather than a branch here. Left as-is deliberately --
        // it is two branches, not six, and splitting it buys little.
        if (comp.type == ThrowExplosiveType.IMPACT) {
            val velocity = chunk.velocityOrNull(index) ?: return
            val currentVelocityLength = velocity.velocity.length()
            if (currentVelocityLength == 0.0) {
                comp.ticksAtZero++
                if (comp.ticksAtZero > MAX_ZERO_TICKS) {
                    explode(ref, playerEntityRef, transform.transform, comp, buffer)
                    return
                }
                if (comp.lastVelocityLength == null) return
            } else {
                comp.ticksAtZero = 0
            }
            // Bound to a local so it smart-casts; the property read needed a !!.
            val lastVelocityLength = comp.lastVelocityLength
            if (lastVelocityLength != null) {
                val delta = abs(lastVelocityLength - currentVelocityLength)
                if (delta > IMPACT_DELTA_THRESHOLD) {
                    explode(ref, playerEntityRef, transform.transform, comp, buffer)
                    return
                }
            }
            comp.lastVelocityLength = currentVelocityLength
            return
        }

        val explodeAt = comp.explodeAt
        if (explodeAt != null) {
            if (Instant.now().isAfter(explodeAt)) {
                explode(ref, playerEntityRef, transform.transform, comp, buffer)
            }
        }
    }

    private fun explode(
        ref: Ref<EntityStore>, source: Ref<EntityStore>, transform: Transform, comp: ThrowExplosiveComponent,
        buffer: CommandBuffer<EntityStore>
    ) {
        val explosionParticleId = comp.explosionParticleId ?: return
        val explosionSoundId = comp.explosionSoundId ?: return
        val radius = comp.damageRadius ?: 8.0
        buffer.removeEntity(ref, RemoveReason.REMOVE)
        ParticleUtil.spawnParticleEffect(explosionParticleId, transform.position, buffer)
        val soundIndex = SoundEvent.getAssetMap().getIndex(explosionSoundId) ?: return
        SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, transform.position, buffer)
        val damageAmount = comp.damageAmount ?: return
        val damageFalloff = comp.damageFalloff ?: return
        executeRadial(buffer, transform.position, radius, { it.playerRefOrNull != null }) {
            val targetTransform = it.transformOrNull ?: return@executeRadial
            val distance = transform.position.distance(targetTransform.position)
            val finalDamage = if (distance >= radius) {
                0.0
            } else {
                val falloffDamage = damageFalloff * distance
                val dmg = damageAmount - falloffDamage
                if (dmg < 0.0) 0.0 else dmg
            }
            if (finalDamage <= 0.0) return@executeRadial
            DamageSystems.executeDamage(
                it, buffer, Damage(
                    Damage.EntitySource(source),
                    damageCause,
                    finalDamage.toFloat()
                )
            )
        }
    }

    override fun getQuery() = ThrowExplosiveComponent.componentType

}

