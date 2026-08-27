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
            val direction = (Vector3d(playerTransform.position).sub(position)).normalize()
            val result = Raycasts.raycastEntity(world, position, direction, range)
            if (result.hit != null) {
                execute(it)
            }
        }catch (ex:Exception){
            logger.warn("Error during executeRadial: ${ex.message}")
        }

    }, { filter(it) })
}

