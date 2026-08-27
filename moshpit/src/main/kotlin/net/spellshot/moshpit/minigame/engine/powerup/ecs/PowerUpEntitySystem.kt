package net.spellshot.moshpit.minigame.engine.powerup.ecs

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService
import net.spellshot.moshpit.minigame.engine.MiniGameEngine

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
        // Bound to a local so Kotlin can smart-cast it. Checking the property and then
        // asserting on it needed a !!, because a property of another object can change
        // between the two reads as far as the compiler is concerned.
        val sessionId = component.gameSessionId ?: return
        if (component.powerUpType == null) return
        if (engine.doesSessionExistsById(sessionId)) return
        val ref = chunk.getReferenceTo(index)
        buffer.removeEntity(ref, RemoveReason.REMOVE)
    }

    override fun getQuery(): Query<EntityStore> {
        return PowerUpComponent.componentType
    }

}
