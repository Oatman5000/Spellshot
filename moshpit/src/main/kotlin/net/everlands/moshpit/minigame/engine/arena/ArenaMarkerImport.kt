package net.everlands.moshpit.minigame.engine.arena

import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.math.vector.Transform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.joml.Vector3d
import java.io.File

/**
 * Reads a `MiniGameArenaResource.json` from disk so `/arena import` can load it into a
 * running world.
 *
 * Why this exists. The markers live inside the world, at
 * `universe/worlds/<world>/resources/MiniGameArenaResource.json`, and the server holds
 * that file's contents in memory for as long as it is up. Dropping a new copy in place
 * on a live server therefore does nothing -- and worse, the next world save writes the
 * in-memory version straight back over it. Getting the file in cleanly means stopping
 * the server, uploading, and starting again, in that order, and any slip silently
 * reverts to whatever was in memory. That sequence has now destroyed this marker set
 * twice.
 *
 * So instead of moving a file into a directory the server owns, this reads a file from
 * a directory *you* own and writes it into the live world through the same path
 * `/arena loc set` uses. Nothing to overwrite, no restart, no ordering to get wrong.
 * The next save persists it to the right place on its own.
 *
 * This deliberately does its own parsing rather than reusing [MiniGameArenaResource.CODEC].
 * The codec is wired into the chunk store's own load path; borrowing it here would mean
 * constructing a decode context by hand for a format that is six lines of JSON.
 */
object ArenaMarkerImport {

    /**
     * Where `/arena import` looks, in order, relative to the server's working directory.
     *
     * `mods/` first and deliberately: it is the one directory an operator uploads to
     * routinely and can confirm worked, because a bad jar upload fails loudly at boot.
     * A marker file that lands in the wrong place fails silently, which is the whole
     * problem this is working around.
     */
    val SEARCH_PATHS = listOf(
        "mods/arena-markers.json",
        "mods/MiniGameArenaResource.json",
        "arena-markers.json",
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileShape(
        @SerialName("Entries") val entries: Map<String, ArenaShape> = emptyMap(),
    )

    @Serializable
    private data class ArenaShape(
        @SerialName("Locations") val locations: Map<String, PointShape> = emptyMap(),
    )

    /**
     * Rotation defaults to zero because only the yaw of a spawn matters and an import
     * that omits it should still place the player in the right spot, facing north,
     * rather than fail to parse.
     */
    @Serializable
    private data class PointShape(
        @SerialName("X") val x: Double,
        @SerialName("Y") val y: Double,
        @SerialName("Z") val z: Double,
        @SerialName("Pitch") val pitch: Float = 0f,
        @SerialName("Yaw") val yaw: Float = 0f,
        @SerialName("Roll") val roll: Float = 0f,
    ) {
        // Argument order matches the field order on Rotation3f, which is also the key
        // order the codec writes. If spawns ever come back facing oddly, this is the
        // line to check -- it affects facing only, never position.
        fun toTransform(): Transform = Transform(Vector3d(x, y, z), Rotation3f(pitch, yaw, roll))
    }

    sealed interface Result {
        data class NotFound(val looked: List<String>) : Result
        data class Unreadable(val path: String, val reason: String) : Result
        data class Empty(val path: String) : Result
        data class Parsed(
            val path: String,
            val arenas: Map<String, Map<String, Transform>>,
        ) : Result {
            val markerCount: Int get() = arenas.values.sumOf { it.size }
        }
    }

    /** Finds and parses the first file that exists. Never throws. */
    fun load(explicitPath: String? = null): Result {
        val candidates = explicitPath?.let { listOf(it) } ?: SEARCH_PATHS
        val file = candidates.map(::File).firstOrNull { it.isFile }
            ?: return Result.NotFound(candidates.map { File(it).absolutePath })

        val text = runCatching { file.readText() }
            .getOrElse { return Result.Unreadable(file.path, it.message ?: it::class.java.simpleName) }

        val decoded = runCatching { json.decodeFromString<FileShape>(text) }
            .getOrElse { return Result.Unreadable(file.path, it.message ?: "not valid marker JSON") }

        val arenas = decoded.entries
            .mapValues { (_, arena) -> arena.locations.mapValues { (_, p) -> p.toTransform() } }
            .filterValues { it.isNotEmpty() }

        if (arenas.isEmpty()) return Result.Empty(file.path)
        return Result.Parsed(file.path, arenas)
    }
}
