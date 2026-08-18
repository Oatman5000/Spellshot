package net.everlands.moshpit.minigame.engine.powerup.ecs

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import kotlin.math.sin

@EcsService
class PowerUpRotationEntitySystem : EntityTickingSystem<EntityStore>() {

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val powerUpComponent = chunk.getComponent(index, PowerUpComponent.componentType) ?: return
        val headRotation = chunk.getComponent(index, HeadRotation.getComponentType()) ?: return
        headRotation.rotation.add(0f, delta * 1f, 0f)
        val anchorY = powerUpComponent.anchorY ?: return
        val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return
        transform.position.y = (sin(System.currentTimeMillis() / 500.0) * 0.2) + anchorY
    }

    override fun getQuery(): Query<EntityStore> {
        return Query.and(
            PowerUpComponent.componentType,
            HeadRotation.getComponentType(),
            TransformComponent.getComponentType()
        )
    }

}
