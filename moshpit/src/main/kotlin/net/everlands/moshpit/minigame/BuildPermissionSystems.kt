package net.everlands.moshpit.minigame

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
import net.everlands.core.framework.EcsService

/**
 * Building is Creative-only.
 *
 * MiniGame.json's WorldConfig now permits breaking, gathering and placement --
 * it has to, because those flags are per-world and cannot distinguish players.
 * The per-player rule is enforced here instead: both events extend
 * CancellableEcsEvent, so anyone not in Creative simply has the action vetoed.
 *
 * Doing it this way round matters. WorldConfig.isBlockBreakingAllowed is
 * checked before the player's game mode, so leaving the flags false would have
 * blocked Creative too -- Creative does not bypass them.
 */
@EcsService
class NoBreakOutsideCreativeSystem :
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
class NoPlaceOutsideCreativeSystem :
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
