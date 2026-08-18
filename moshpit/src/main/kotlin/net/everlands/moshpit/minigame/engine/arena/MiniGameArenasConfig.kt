package net.everlands.moshpit.minigame.engine.arena

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.ArenasConfig")
data class MiniGameArenasConfig(
    val arenas: List<MiniGameArena> = listOf(
        MiniGameArena("mirage", "Mirage", "everlands"),
        MiniGameArena("dust2", "Dust2", "everlands"),
        MiniGameArena("raid", "Raid", "everlands"),
        MiniGameArena("rust", "Rust", "everlands"),
        MiniGameArena("hijacked", "Hijacked", "everlands"),
        // Nuketown ships with plain spawns, powerups, airstrike and plane, but no
        // team:ODA/team:SENTINEL spawns and no hardpoint markers -- so it is only
        // offered for FFA. See defaultArenasFor() in MoshpitConfig.
        MiniGameArena("nuketown", "Nuketown", "everlands")
    ),
) {
    @Component
    class Binder : Config<MiniGameArenasConfig>(MiniGameArenasConfig())
}

