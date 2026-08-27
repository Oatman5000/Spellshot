package net.spellshot.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService

/**
 * Building in the lobby is Creative-only.
 *
 * Ported from moshpit, and the same reasoning applies: Lobby.json now permits
 * breaking, gathering and placement because those flags are per-world and
 * cannot distinguish players. WorldConfig is checked BEFORE game mode, so
 * leaving them false blocks Creative builders as well -- Creative does not
 * bypass them. The per-player rule is enforced here instead.
 */
@EcsService
class LobbyNoBreakOutsideCreativeSystem :
    EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        if (player.gameMode != GameMode.Creative) event.isCancelled = true
    }
}

@EcsService
class LobbyNoPlaceOutsideCreativeSystem :
    EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        if (player.gameMode != GameMode.Creative) event.isCancelled = true
    }
}
