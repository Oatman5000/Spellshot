package net.everlands.moshpit.minigame.engine.arena

import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.universe.Universe
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class MiniGameArena(
    val id: String,
    val name: String,
    val author: String,
    val dieOnWater: Boolean = false,
    val dieOnWaterHeight: Double? = null,
    val weather: String = "Cave_Deep"
) {
    val world get() = Universe.get().worlds.values.firstOrNull { w -> runCatching { w.arenaResource.entries.containsKey(id) }.getOrDefault(false) } ?: Universe.get().defaultWorld ?: error("Default world not found")

    val locations: Map<String, Transform>
        get() {
            try {
                return world.arenaResource.entries[id]?.locations ?: emptyMap()
            } catch (e: Exception) {
                logger.warn("Failed to get locations for arena $name: ${e.message}")
                return emptyMap()
            }
        }

    companion object {
        private val logger = LoggerFactory.getLogger(MiniGameArena::class.java)
    }
}
