package net.everlands.moshpit.item.airstrike

import org.joml.Vector3d

import net.everlands.core.framework.BlockBoxRaycaster

import com.google.common.cache.CacheBuilder
import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.transformOrNull
import net.everlands.core.ext.referenceOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@EcsService
class MissileEntitySystem : DelayedEntitySystem<EntityStore>(0.05f) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val damageCause by lazy {
        DamageCause.getAssetMap().getAsset("Gun")
    }

    private val cooldowns = CacheBuilder.newBuilder()
        .expireAfterAccess(2, TimeUnit.SECONDS)
        .build<Ref<EntityStore>, Long>()

    override fun tick(
        delta: Float, index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        try {
            val ref = chunk.getReferenceTo(index)
            val missile = chunk.getComponent(index, MissileComponent.componentType) ?: return
            val spawnerId = missile.id ?: return
            val spawner = MissileSpawner.SPAWNERS.getIfPresent(spawnerId)
            if (spawner == null) {
                MissileSpawner.SPAWNERS.asMap().remove(spawnerId)
                buffer.removeEntity(ref, RemoveReason.REMOVE)
                return
            }
            val transform = chunk.transformOrNull(index) ?: return
            val physics = chunk.getComponent(index, StandardPhysicsProvider.getComponentType()) ?: return
            val difference = System.currentTimeMillis() - missile.spawned
            if (physics.velocity.y >= 0.0 && difference > 1000) {
                val world = buffer.store.externalData.world
                world.execute {
                    try {
                        val playerRef = spawner.playerRef.referenceOrNull ?: return@execute
                        // Blast centre is lifted off the impact point. The missile
                        // detonates the moment it stops falling, so its position is
                        // level with (or just inside) the block it struck -- tracing
                        // from there puts the ground itself between the explosion and
                        // everyone standing on it.
                        val blast = Vector3d(transform.position).add(0.0, BLAST_ORIGIN_LIFT, 0.0)
                        Selector.selectNearbyEntities(buffer, transform.position, BLAST_RADIUS, {
                            if (it == playerRef) return@selectNearbyEntities
                            val playerTransform = it.transformOrNull ?: return@selectNearbyEntities
                            // Walls stop the blast. This previously called
                            // Raycasts.raycastEntity, which only ever tests entity
                            // boxes and never looks at world geometry -- and its
                            // result was not even compared against the candidate, so
                            // "result.hit != null" was true whenever ANY player lay
                            // roughly in that direction, which the target themselves
                            // always satisfied. The check therefore always passed and
                            // players indoors died through the roof.
                            if (!hasLineOfSight(world, blast, playerTransform.position)) {
                                return@selectNearbyEntities
                            }
                            val cause = damageCause
                            if (cause != null) {
                                val timestamp = cooldowns.getIfPresent(it) ?: 0L
                                if (System.currentTimeMillis() - timestamp > 2000) {
                                    // Flag the victim before the damage lands: the kill
                                    // event that follows carries no damage cause, so this
                                    // is the last point an airstrike kill is identifiable.
                                    it.playerRefOrNull?.uuid?.let(AirstrikeKills::mark)
                                    DeathComponent.tryAddComponent(
                                        buffer,
                                        it,
                                        Damage(Damage.EntitySource(playerRef), cause, 2.1474836E9f)
                                    )
                                    cooldowns.put(it, System.currentTimeMillis())
                                }
                            }
                        }, { it.playerRefOrNull != null })
                    } catch (e: Exception) {
                        logger.warn("Exception while trying to execute hit", e)
                    }

                }
                buffer.removeEntity(ref, RemoveReason.REMOVE)
                spawner.explode(buffer, transform.position)

            }
        } catch (e: Exception) {
            // This runs per missile per tick, so an unattributed stdout trace was both
            // unfindable and capable of flooding the console during one airstrike.
            logger.warn("Missile tick failed: {}", e.message, e)
        }
    }

    /**
     * True if any part of the target is exposed to the blast.
     *
     * Three points up the body are sampled rather than one. Tracing only to the
     * feet makes anyone standing behind a one-block lip, on a slab, or on the far
     * side of a fence immune; tracing only to the head does the opposite on
     * low-ceilinged rooftops. If any sample is reachable the player is caught in
     * the open enough to be hit.
     *
     * [BlockBoxRaycaster] is the same tracer the guns use, so it respects real
     * block shapes -- a cloth roof or a slab blocks, foliage does not.
     */
    private fun hasLineOfSight(world: World, blast: Vector3d, feet: Vector3d): Boolean {
        for (dy in BODY_SAMPLES) {
            val target = Vector3d(feet.x, feet.y + dy, feet.z)
            val delta = Vector3d(target).sub(blast)
            val distance = delta.length()
            if (distance <= 1e-4) return true
            // Stop just short of the target: a player pressed against a wall would
            // otherwise have the ray run on into that wall and read as covered.
            val reach = distance - LOS_EPSILON
            if (reach <= 0.0) return true
            val trace = BlockBoxRaycaster.raycast(world, blast, delta.normalize(), reach)
            if (trace.hitPos == null) return true
        }
        return false
    }

    override fun getQuery(): Query<EntityStore> {
        return MissileComponent.componentType
    }

    private companion object {
        /** Horizontal reach of a single missile. */
        const val BLAST_RADIUS = 12.0

        /** How far above the impact point the explosion is treated as originating. */
        const val BLAST_ORIGIN_LIFT = 0.5

        /** Heights above the feet to test, spanning Raycasts.HIT_HEIGHT (1.85). */
        val BODY_SAMPLES = doubleArrayOf(0.15, 0.9, 1.7)

        /** Slack left at the end of a line-of-sight trace, in blocks. */
        const val LOS_EPSILON = 0.1
    }
}


