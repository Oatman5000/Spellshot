package net.everlands.moshpit.item.skin

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.PermissionSkinConfig")
data class PermissionSkinConfig(
    val skins: List<PermissionSkinEntry> = emptyList()
) {
    @Component
    class Binder : Config<PermissionSkinConfig>(PermissionSkinConfig())
}

@Serializable
data class PermissionSkinEntry(
    val defaultSkinId: String,
    val skins: List<Skin> = emptyList()
)
