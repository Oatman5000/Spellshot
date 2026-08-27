package net.spellshot.core.functions

import net.spellshot.core.framework.Raycasts

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import org.joml.Vector3d
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.transformOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RaycastFunctions")

/**
 * Runs [execute] for every entity within [range] of [position] that can be seen from
 * it, so that walls block a blast.
 *
 * Point blank is a special case and must stay one. The line of sight test needs a
 * direction, and at zero distance there is no direction to compute: subtracting the
 * two positions gives a zero vector, and normalising that yields NaN rather than
 * throwing. The raycast then travels nowhere, reports no hit, and the entity standing
 * exactly on the blast is the one entity it fails to damage.
 *
 * That is not a corner case here -- it is the common one. A grenade that cooks off in
 * your hand detonates at your own feet, so the holder and the blast share a position
 * exactly, and the person who should certainly die is the only person who survives.
 * The same applies to anyone a thrown grenade lands on.
 *
 * Nothing can be between two things in the same place, so within [POINT_BLANK] there
 * is no occlusion to test for and the check is skipped rather than approximated.
 */
fun executeRadial(
    buffer: CommandBuffer<EntityStore>, position: Vector3d, range: Double,
    filter: (Ref<EntityStore>) -> Boolean = { true }, execute: (Ref<EntityStore>) -> Unit
) {
    val entities = mutableSetOf<Ref<EntityStore>>()
    Selector.selectNearbyEntities(buffer, position, range, {
        try {
            if(entities.contains(it)) return@selectNearbyEntities
            entities.add(it)
            val playerTransform = it.transformOrNull ?: return@selectNearbyEntities
            val world = buffer.externalData.world
            val delta = Vector3d(playerTransform.position).sub(position)
            if (delta.lengthSquared() < POINT_BLANK * POINT_BLANK) {
                execute(it)
                return@selectNearbyEntities
            }
            val result = Raycasts.raycastEntity(world, position, delta.normalize(), range)
            if (result.hit != null) {
                execute(it)
            }
        }catch (ex:Exception){
            logger.warn("Error during executeRadial: ${ex.message}")
        }

    }, { filter(it) })
}

/** Distance in blocks below which line of sight is assumed rather than traced. */
private const val POINT_BLANK = 0.1

