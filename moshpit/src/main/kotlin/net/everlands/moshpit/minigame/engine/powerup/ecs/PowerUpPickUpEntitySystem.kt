package net.everlands.moshpit.minigame.engine.powerup.ecs

import org.joml.Vector3d

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.everlands.core.ext.referenceOrErr
import net.everlands.moshpit.minigame.engine.MiniGameEngine

@EcsService
class PowerUpPickUpEntitySystem(val engine: MiniGameEngine) : EntityTickingSystem<EntityStore>() {

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return
        val component = chunk.getComponent(index, PowerUpComponent.componentType) ?: return

        val refs = ObjectArrayList<Ref<EntityStore>>()
        val position = Vector3d(transform.position)
        position.y = component.anchorY ?: position.y
        Selector.selectNearbyEntities(buffer, position, 1.0, refs::add, { true })
        val players = refs.mapNotNull { ref ->
            store.getComponent(ref, PlayerRef.getComponentType())
        }
        val playerRef = players.firstOrNull() ?: return
        val player = store.getComponent(playerRef.referenceOrErr, Player.getComponentType()) ?: return
        component.powerUpType?.execute?.invoke(playerRef, player)
        buffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
    }

    override fun getQuery(): Query<EntityStore> {
        return PowerUpComponent.componentType
    }

}
