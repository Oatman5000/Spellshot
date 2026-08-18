package net.everlands.lobby.system

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.EcsService

/**
 * Unlimited sprint in the lobby.
 *
 * Same approach as moshpit's InfiniteStaminaSystem: top the stat back up rather
 * than try to intercept each drain site. The lobby previously tried to do this
 * with a Server/Entity/Stats/Stamina.json override inherited from the original
 * Everlands lobby pack, which redefined the entire vanilla stamina asset -- and
 * left players unable to sprint. That file is now in lobby/unported.
 *
 * maximizeStatValue, not resetStatValue: reset restores the default, not the
 * max. Only writes when the value has actually dropped.
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
