package net.everlands.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import net.everlands.core.ext.transformOrNull
import net.everlands.core.ext.velocityOrNull
import net.everlands.core.ext.world
import net.everlands.core.ext.referenceOrNull
import java.time.Instant
import kotlin.math.abs

@EcsService
class ThrowExplosiveSystem : DelayedEntitySystem<EntityStore>(0.1f) {

    companion object {
        private const val MAX_ZERO_TICKS = 2
        private const val IMPACT_DELTA_THRESHOLD = 5.0
        private val logger = org.slf4j.LoggerFactory.getLogger(ThrowExplosiveSystem::class.java)
    }

    private val deps = setOf(
        SystemDependency(Order.BEFORE, StandardPhysicsTickSystem::class.java)
    )

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
        // The thrower being absent no longer cancels the blast -- it only means the
        // damage goes unattributed. A live grenade is a live grenade whether or not
        // the person who threw it is still standing there.
        val playerRef = Universe.get().getPlayer(damagerUuid)
        if (playerRef != null && playerRef.world != buffer.store.externalData.world) {
            // Genuinely a different world, so this grenade is orphaned scenery.
            buffer.removeEntity(ref, RemoveReason.REMOVE)
            return
        }
        val playerEntityRef = playerRef?.referenceOrNull

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

    /**
     * Detonates at [transform].
     *
     * [source] is nullable and the blast no longer depends on it. Ordering matters
     * here and used to be wrong in two ways.
     *
     * The entity was removed and the particle played BEFORE `damageAmount`,
     * `damageFalloff` and the sound index were read -- each with `?: return`. Any one
     * of them missing gave a grenade that visibly exploded and did nothing, which is
     * the "only does damage sometimes" report. Everything needed is now resolved
     * first, so a missing field costs the sound or the particle, never the damage.
     *
     * The caller also used to abort the whole detonation if the thrower's entity
     * reference was gone -- `playerRef.referenceOrNull ?: return` -- so a grenade
     * thrown just before dying simply never went off. A blast with no live thrower
     * is still a blast; it just has no entity to credit.
     */
    private fun explode(
        ref: Ref<EntityStore>, source: Ref<EntityStore>?, transform: Transform, comp: ThrowExplosiveComponent,
        buffer: CommandBuffer<EntityStore>
    ) {
        buffer.removeEntity(ref, RemoveReason.REMOVE)
        Explosions.detonate(
            buffer = buffer,
            position = transform.position,
            source = source,
            radius = comp.damageRadius ?: 8.0,
            damageAmount = comp.damageAmount,
            damageFalloff = comp.damageFalloff,
            explosionParticleId = comp.explosionParticleId,
            explosionSoundId = comp.explosionSoundId,
            explosiveType = comp.type,
        )
    }

    override fun getQuery() = ThrowExplosiveComponent.componentType

}

