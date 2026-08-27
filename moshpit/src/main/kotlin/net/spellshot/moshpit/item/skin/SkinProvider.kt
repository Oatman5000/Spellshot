package net.spellshot.moshpit.item.skin

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.framework.SpellshotUser
import kotlinx.serialization.Serializable

interface SkinProvider {

    fun provide(user: SpellshotUser, playerRef: PlayerRef, itemId: String): List<Skin>

    fun findDefaultItemId(user: SpellshotUser, skinId: String): String?

    fun isSkin(skinId: String): Boolean

}

@Serializable
data class Skin(
    val itemId: String,
    val name: String,
)
