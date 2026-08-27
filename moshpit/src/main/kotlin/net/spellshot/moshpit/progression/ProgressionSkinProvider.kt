package net.spellshot.moshpit.progression

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.framework.SpellshotUser
import net.spellshot.moshpit.item.skin.Skin
import net.spellshot.moshpit.item.skin.SkinProvider
import org.springframework.stereotype.Service

@Service
class ProgressionSkinProvider(val config: ProgressionConfig.Binder) : SkinProvider {

    override fun provide(
        user: SpellshotUser, playerRef: PlayerRef, itemId: String
    ): List<Skin> {
        val config = config.value
        val itemProgression =
            config.items.flatMap { it.value }.firstOrNull { it.itemId.equals(itemId, ignoreCase = true) }
                ?: return emptyList()
        val level = user.level
        val prestige = user.prestige
        val skins = itemProgression.skins.filter {
            level >= it.requiredLevel && prestige >= it.requiredPrestige
        }
        return skins.map { Skin(it.itemId, it.name) }
    }

    override fun findDefaultItemId(user: SpellshotUser, skinId: String): String? {
        return config.value.items.values.flatten().firstOrNull { itemConfig ->
            itemConfig.skins.any { it.itemId == skinId }
        }?.itemId
    }

    override fun isSkin(skinId: String): Boolean {
        val skins = config.value.items.values.flatten().flatMap { it.skins }
        return skins.any { it.itemId == skinId }
    }
}
