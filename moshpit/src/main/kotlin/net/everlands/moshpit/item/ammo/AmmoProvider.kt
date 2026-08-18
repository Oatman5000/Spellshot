package net.everlands.moshpit.item.ammo

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

interface AmmoProvider {

    fun getAmmo(stack: ItemStack): Int?

    fun setAmmo(itemContainer: ItemContainer, slot: Short, stack: ItemStack, ammo: Int)

    fun buildItemStackWithAmmo(stack: ItemStack, ammo: Int): ItemStack

    fun getMaxAmmo(stack: ItemStack): Int?

    fun setClientAmmo(ref: Ref<EntityStore>, ammo: Int)

}
