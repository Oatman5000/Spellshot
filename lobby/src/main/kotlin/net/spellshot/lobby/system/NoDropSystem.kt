package net.spellshot.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService

/**
 * The navigator and leaderboard cannot be dropped -- otherwise the lobby floor
 * fills with them and players lose the only way to open the menus.
 *
 * Ported from the original Everlands lobby module.
 */
@EcsService
class NoDropSystem :
    EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest>(DropItemEvent.PlayerRequest::class.java) {

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: DropItemEvent.PlayerRequest
    ) {
        event.isCancelled = true
    }

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()
}
