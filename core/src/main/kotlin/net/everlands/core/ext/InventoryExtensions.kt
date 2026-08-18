package net.everlands.core.ext

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer

fun ItemContainer.toList() = (0..<capacity).mapNotNull { getItemStack(it.toShort()) }

fun Player.countItemsInInventory(itemId: String): Int {
    var amount = 0
    val containers = listOfNotNull(inventory.hotbar, inventory.storage)
    for (container in containers) {
        for (slot in 0 until container.capacity - 1) {
            val itemStack: ItemStack? = container.getItemStack(slot.toShort())
            if (itemStack != null && itemStack.itemId == itemId) {
                amount += itemStack.quantity
            }
        }
    }
    return amount
}

fun Player.takeItemsFromInventory(itemId: String, amount: Int): Int {
    var remaining = amount
    val containers = listOfNotNull(inventory.hotbar, inventory.storage)
    for (container in containers) {
        for (slot in 0 until container.capacity) {
            if (remaining <= 0) break

            val slotIndex = slot.toShort()
            val itemStack = container.getItemStack(slotIndex) ?: continue
            if (itemStack.itemId != itemId) continue

            val toTake = itemStack.quantity.coerceAtMost(remaining)
            val newQty = itemStack.quantity - toTake

            if (newQty <= 0) {
                container.removeItemStackFromSlot(slotIndex)
            } else {
                container.setItemStackForSlot(slotIndex, itemStack.withQuantity(newQty))
            }
            remaining -= toTake
        }
    }
    return amount - remaining
}
