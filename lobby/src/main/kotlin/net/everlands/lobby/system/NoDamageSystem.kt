package net.everlands.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService

/**
 * Nothing hurts in the lobby.
 *
 * Lobby.json already sets DisablePlayerIncomingDamage, but that is a client
 * presentation config; this cancels the damage server-side so fall damage, void
 * damage and anything a future plugin adds cannot kill an idle player.
 *
 * Ported from the original Everlands lobby module.
 */
@EcsService
class NoDamageSystem : DamageEventSystem() {

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        dmg: Damage
    ) {
        dmg.isCancelled = true
    }

    override fun getGroup(): SystemGroup<EntityStore> = DamageModule.get().filterDamageGroup

    override fun getQuery(): Query<EntityStore?> = Player.getComponentType()
}
