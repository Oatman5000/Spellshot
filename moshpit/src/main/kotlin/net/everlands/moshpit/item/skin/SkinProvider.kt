package net.everlands.moshpit.item.skin

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.core.framework.EverlandsUser
import kotlinx.serialization.Serializable

interface SkinProvider {

    fun provide(user: EverlandsUser, playerRef: PlayerRef, itemId: String): List<Skin>

    fun findDefaultItemId(user: EverlandsUser, skinId: String): String?

    fun isSkin(skinId: String): Boolean

}

@Serializable
data class Skin(
    val itemId: String,
    val name: String,
)
