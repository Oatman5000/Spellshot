package net.spellshot.moshpit.item.skin

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.framework.SpellshotUser
import org.springframework.stereotype.Service

@Service
class PermissionSkinsProvider(val config: PermissionSkinConfig.Binder) : SkinProvider {

    override fun provide(
        user: SpellshotUser, playerRef: PlayerRef,
        itemId: String
    ): List<Skin> {
        return config.value.skins.firstOrNull { it.defaultSkinId == itemId }?.skins?.filter { user.hasPermission("skin.$itemId.${it.itemId}") }
            ?: emptyList()
    }

    override fun findDefaultItemId(user: SpellshotUser, skinId: String): String? {
        return config.value.skins.firstOrNull { entry -> entry.skins.any { it.itemId == skinId } }?.defaultSkinId
    }

    override fun isSkin(skinId: String): Boolean {
        return config.value.skins.any { entry -> entry.skins.any { it.itemId == skinId } }
    }
}
