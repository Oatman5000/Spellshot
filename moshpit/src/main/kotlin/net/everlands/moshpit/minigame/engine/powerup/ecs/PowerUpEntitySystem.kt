package net.everlands.moshpit.minigame.engine.powerup.ecs

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import net.everlands.moshpit.minigame.engine.MiniGameEngine

@EcsService
class PowerUpEntitySystem(val engine: MiniGameEngine) : DelayedEntitySystem<EntityStore>(1.0f) {

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val component = chunk.getComponent(index, PowerUpComponent.componentType) ?: return
        if (component.gameSessionId == null || component.powerUpType == null) return
        if (engine.doesSessionExistsById(component.gameSessionId!!)) return
        val ref = chunk.getReferenceTo(index)
        buffer.removeEntity(ref, RemoveReason.REMOVE)
    }

    override fun getQuery(): Query<EntityStore> {
        return PowerUpComponent.componentType
    }

}
