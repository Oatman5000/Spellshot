package net.everlands.core.framework

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Disk persistence for [EverlandsUser.customData].
 *
 * Without this, progression is pure in-memory state: xp, level, prestige and
 * every minigame_* counter live in a ConcurrentHashMap that starts empty on
 * each boot, so restarting the server silently reset every player to level 1.
 *
 * Only primitives are persisted. customData also holds a live PlayerLoadouts
 * object under "loadouts"; that is session state referencing runtime objects,
 * so it is skipped rather than half-serialised.
 */
object UserStore {

    private val logger = LoggerFactory.getLogger("everlands.users")

    /**
     * The server's working directory is the devserver root -- it writes ./logs,
     * ./auth.enc and ./telemetry there. Sitting beside the world data keeps this
     * file outside the source tree, so rebuilding and recopying the plugin jar
     * cannot wipe it.
     */
    private val file: Path = Paths.get("everlands", "users.json").toAbsolutePath()

    /** Retains data for players who are offline, so a save never drops them. */
    private val records = ConcurrentHashMap<UUID, Record>()

    private val dirty = AtomicBoolean(false)
    private val loaded = AtomicBoolean(false)
    private val shutdownHookInstalled = AtomicBoolean(false)
    private var flusher: ScheduledExecutorService? = null

    private val json = Json { prettyPrint = true }

    private class Record(val username: String, val data: Map<String, Any>)

    // ---------------------------------------------------------------- loading

    /** Idempotent: safe to call from setup() and again defensively on first use. */
    fun load() {
        if (!loaded.compareAndSet(false, true)) return
        if (!Files.exists(file)) {
            logger.info("No saved progression at {} - starting fresh", file)
            return
        }
        runCatching {
            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
            for ((key, element) in root) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val obj = element.jsonObject
                val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val data = HashMap<String, Any>()
                obj["data"]?.jsonObject?.forEach { (path, value) ->
                    decode(value.jsonPrimitive)?.let { data[path.lowercase()] = it }
                }
                records[uuid] = Record(username, data)
            }
            logger.info("Loaded progression for {} players from {}", records.size, file)
        }.onFailure {
            // Never let a corrupt file stop the server booting; keep the bad file
            // aside so it can be inspected rather than silently overwritten.
            logger.error("Failed to read {} - starting fresh, old file kept as .broken", file, it)
            runCatching {
                Files.move(file, file.resolveSibling("users.json.broken"), StandardCopyOption.REPLACE_EXISTING)
            }.onFailure { move ->
                // If the rename fails the corrupt file stays where it is and the next
                // save overwrites it, destroying the only copy of whatever went wrong.
                logger.error("Could not set {} aside - it will be overwritten on next save", file, move)
            }
        }
    }

    /** Copies any saved values onto a freshly created user. Called from [Users.of]. */
    fun hydrate(user: EverlandsUser) {
        load()
        val record = records[user.uuid] ?: return
        for ((path, value) in record.data) user.customData.putRaw(path, value)
        logger.info(
            "Restored {} (level {}, prestige {})",
            user.username,
            user.customData.getInt("level") ?: 1,
            user.customData.getInt("prestige") ?: 0
        )
    }

    // ---------------------------------------------------------------- saving

    fun markDirty() { dirty.set(true) }

    /** A stored player, for read-only consumers such as the leaderboard. */
    class StoredUser(val uuid: UUID, val username: String, val data: Map<String, Any>)

    /**
     * Every player on disk, not just the ones loaded this session.
     *
     * Users.all() only holds players seen since boot, which would make a
     * leaderboard reset on every restart. Live users are merged over the stored
     * record so stats earned in the current session are included.
     */
    fun snapshotAll(): List<StoredUser> {
        val merged = LinkedHashMap<UUID, StoredUser>()
        for ((uuid, record) in records) {
            merged[uuid] = StoredUser(uuid, record.username, record.data)
        }
        for (user in Users.all()) {
            merged[user.uuid] = StoredUser(user.uuid, user.username, user.customData.asMap())
        }
        return merged.values.toList()
    }

    /** Periodic flush so a crash loses at most one interval of progress. */
    fun startAutoSave(intervalSeconds: Long = 30) {
        if (flusher != null) return
        // Ctrl+C on the dev server kills the JVM without reaching plugin shutdown(),
        // which is how most test sessions end -- catch that too.
        if (shutdownHookInstalled.compareAndSet(false, true)) {
            runCatching {
                Runtime.getRuntime().addShutdownHook(
                    Thread({
                        // This is the last chance to persist progression. If it fails
                        // silently, everything since the previous flush is gone with no
                        // trace -- which reads to players as lost levels after a restart.
                        runCatching { saveNow() }
                            .onFailure { logger.error("Shutdown save failed - progression since the last flush is lost", it) }
                    }, "everlands-userstore-shutdown")
                )
            }.onFailure {
                // Thrown if the JVM is already shutting down. Worth a line, since it
                // means the final save will not happen at all.
                logger.warn("Could not install the shutdown save hook: {}", it.message)
            }
        }
        flusher = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "everlands-userstore").apply { isDaemon = true }
        }.also {
            it.scheduleWithFixedDelay(
                { runCatching { saveIfDirty() }.onFailure { e -> logger.error("Auto-save failed", e) } },
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS
            )
        }
    }

    fun stopAutoSave() {
        flusher?.shutdownNow()
        flusher = null
    }

    fun saveIfDirty() {
        if (dirty.get()) saveNow()
    }

    @Synchronized
    fun saveNow() {
        // Fold live users into the record set first, so players who logged out
        // earlier this session are still written out.
        for (user in Users.all()) {
            val data = user.customData.asMap()
                .mapNotNull { (path, value) -> encode(value)?.let { path to value } }
                .toMap()
            records[user.uuid] = Record(user.username, data)
        }
        if (records.isEmpty()) { dirty.set(false); return }

        val root = buildMap<String, JsonObject> {
            for ((uuid, record) in records) {
                val data = buildMap<String, JsonPrimitive> {
                    for ((path, value) in record.data) encode(value)?.let { put(path, it) }
                }
                put(
                    uuid.toString(),
                    JsonObject(mapOf("username" to JsonPrimitive(record.username), "data" to JsonObject(data)))
                )
            }
        }

        runCatching {
            Files.createDirectories(file.parent)
            // Write-then-move so a crash mid-write cannot truncate the real file.
            val tmp = file.resolveSibling("users.json.tmp")
            Files.writeString(tmp, json.encodeToString(JsonObject.serializer(), JsonObject(root)))
            // ATOMIC_MOVE is not universally supported alongside REPLACE_EXISTING
            // on Windows, so fall back to a plain replace rather than losing the save.
            runCatching {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.recoverCatching {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
            dirty.set(false)
            logger.debug("Saved progression for {} players", root.size)
        }.onFailure { logger.error("Failed to save progression to {}", file, it) }
    }

    // ------------------------------------------------------------ conversion

    private fun encode(value: Any): JsonPrimitive? = when (value) {
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> null // e.g. PlayerLoadouts - live session state, not persisted
    }

    private fun decode(value: JsonPrimitive): Any? {
        if (value.isString) return value.contentOrNull
        value.intOrNull?.let { return it }
        value.longOrNull?.let { return it }
        value.booleanOrNull?.let { return it }
        return null
    }
}
