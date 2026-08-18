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

    /**
     * Every marker for this arena, or an empty map with a diagnostic in the log.
     *
     * Empty is never normal -- it means nothing teleports -- so each way of getting
     * there is reported distinctly. The previous version collapsed all of them into
     * one `warn` that only fired on an exception, so the common case (resource loaded
     * fine, this arena simply is not in it) produced no output at all and the first
     * sign of trouble was `NoSuchElementException: Collection is empty` from a spawn
     * strategy, several frames away from the cause.
     */
    val locations: Map<String, Transform>
        get() {
            val w = try {
                world
            } catch (e: Exception) {
                report("could not resolve a world: ${e.message}")
                return emptyMap()
            }

            val resource = try {
                w.arenaResource
            } catch (e: Exception) {
                report(
                    "marker store unavailable on world '${w.name}' (${e.message}). " +
                        "If the log also shows ARENAS ARE DISABLED, registration failed at boot"
                )
                return emptyMap()
            }

            val entry = resource.entries[id]
            if (entry == null) {
                report(
                    "not present in the marker store on world '${w.name}', which holds " +
                        "${resource.entries.size} arena(s): ${resource.entries.keys}. " +
                        "Either that world's MiniGameArenaResource.json is empty/missing, " +
                        "or the server is running a different world than the one the " +
                        "markers were placed in"
                )
                return emptyMap()
            }

            if (entry.locations.isEmpty()) {
                report("present on world '${w.name}' but has zero markers")
                return emptyMap()
            }

            // One line per arena per boot, so the log shows what actually loaded rather
            // than only what failed. Without it, a healthy server is indistinguishable
            // from one that has not been asked for spawns yet.
            if (announced.add(id)) {
                logger.info(
                    "Arena '{}' loaded {} markers from world '{}'", id, entry.locations.size, w.name
                )
            }

            return entry.locations
        }

    /** Rate-limited to one line per arena per minute; this is read on every spawn. */
    private fun report(problem: String) {
        val now = System.currentTimeMillis()
        val last = lastReport[id] ?: 0L
        if (now - last < 60_000) return
        lastReport[id] = now
        logger.error("Arena '{}' ({}) has no usable markers: {}", id, name, problem)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MiniGameArena::class.java)
        private val lastReport = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val announced = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }
}
