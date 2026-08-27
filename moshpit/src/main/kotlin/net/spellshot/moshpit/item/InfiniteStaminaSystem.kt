package net.spellshot.moshpit.item

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService

/**
 * Unlimited sprint: pins every player's stamina to its maximum.
 *
 * An arena shooter shouldn't gate movement on a stamina meter, so rather than
 * trying to intercept each drain site this simply tops the stat back up. Uses
 * maximizeStatValue (not resetStatValue, which restores the default rather than
 * the max), and only writes when the value has actually dropped so we aren't
 * dirtying the component every tick.
 *
 * The matching bar is hidden client-side in GunSelectUI.
 */
@EcsService
class InfiniteStaminaSystem : DelayedEntitySystem<EntityStore>(0.2f) {

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val stats = chunk.getComponent(index, EntityStatMap.getComponentType()) ?: return
        val stamina = DefaultEntityStatTypes.getStamina()
        val value = stats.get(stamina) ?: return
        if (value.get() < value.max) {
            stats.maximizeStatValue(stamina)
        }
    }
}
