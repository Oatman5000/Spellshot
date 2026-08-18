package net.everlands.moshpit.item.ammo

import com.google.common.cache.CacheBuilder
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.moshpit.item.interaction.ReloadInteraction.Companion.ammoAsset
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class LocalAmmoProvider : AmmoProvider {

    private val cache = CacheBuilder<String, Int>.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String, Int>()

    override fun getAmmo(
        stack: ItemStack
    ): Int? {
        val ammoId = stack.getFromMetadataOrNull("ammoId", Codec.STRING) ?: return null
        return cache.getIfPresent(ammoId)
    }

    override fun setAmmo(itemContainer: ItemContainer, slot: Short, stack: ItemStack, ammo: Int) {
        var ammoId = stack.getFromMetadataOrNull("ammoId", Codec.STRING)
        if (ammoId == null) {
            ammoId = UUID.randomUUID().toString()
            itemContainer.setItemStackForSlot(slot, stack.withMetadata("ammoId", Codec.STRING, ammoId))
        }
        cache.put(ammoId, ammo)
    }

    override fun buildItemStackWithAmmo(stack: ItemStack, ammo: Int): ItemStack {
        val ammoId = stack.getFromMetadataOrNull("ammoId", Codec.STRING)
        if (ammoId == null) {
            val newAmmoId = UUID.randomUUID().toString()
            cache.put(newAmmoId, ammo)
            return stack.withMetadata("ammoId", Codec.STRING, newAmmoId)
        }
        cache.put(ammoId, ammo)
        return stack
    }

    override fun setClientAmmo(ref: Ref<EntityStore>, ammo: Int) {
        val entityStatMap = ref.store.getComponent(ref, EntityStatMap.getComponentType())
        entityStatMap?.setStatValue(ammoAsset, ammo.toFloat())
    }

    override fun getMaxAmmo(stack: ItemStack): Int? {
        val item = stack.item
        if (item.data == null) return null
        if (item.data.rawTags.containsKey("ammo")) {
            val tags = item.data.rawTags["ammo"]!!
            if (tags.size > 0) {
                val ammo = tags[0].toIntOrNull()
                if (ammo != null) {
                    return ammo
                }
            }
        }
        return null
    }

}
