package net.everlands.moshpit.item

import com.hypixel.hytale.server.core.asset.type.item.config.Item

fun Item.existsTag(key: String, value: String): Boolean {
    if (data.rawTags.containsKey(key)) {
        val tags = data.rawTags[key]!!
        return tags.contains(value)
    }
    return false
}

fun isPrimaryGun(itemId: String): Boolean {
    val item = Item.getAssetMap().getAsset(itemId) ?: return false
    return item.isPrimaryGun()
}

fun isSecondaryGun(itemId: String): Boolean {
    val item = Item.getAssetMap().getAsset(itemId) ?: return false
    return item.isSecondaryGun()
}

fun Item.isPrimaryGun() = existsTag("slot", "primary")
fun Item.isSecondaryGun() = existsTag("slot", "secondary")
