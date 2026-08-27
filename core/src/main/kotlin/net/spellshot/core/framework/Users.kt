package net.spellshot.core.framework

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class SpellshotUser(val uuid: UUID, val username: String) {

    val lock = ReentrantLock()
    private val permissions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var playerRef: PlayerRef? = null

    /**
     * Progression lives in [customData] and is exposed by the extensions in
     * net.spellshot.moshpit.progression (level, xp, prestige, xpForNextLevel,
     * xpForNextLevelPercentage).
     *
     * Do NOT re-add members with those names. Kotlin resolves a member ahead of
     * an extension, so fields here would silently shadow the extensions: every
     * read returned the field (always 0) while addXP/addPrestige wrote only to
     * customData. That froze XP at 0, capped level at 1 and made prestige
     * unreachable, with no error anywhere.
     */
    val customData = CustomData()

    fun hasPermission(permission: String): Boolean = GRANT_ALL || permissions.contains(permission)

    fun grant(permission: String) { permissions.add(permission) }
    fun revoke(permission: String) { permissions.remove(permission) }

    inline fun <T> runLockGuarded(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }

    fun setCustomData(path: String, value: Any?) { customData[path] = value }

    class CustomData {
        private val values = ConcurrentHashMap<String, Any>()
        operator fun set(path: String, value: Any?) {
            if (value == null) values.remove(path.lowercase()) else values[path.lowercase()] = value
            UserStore.markDirty()
        }

        /** Used by [UserStore.hydrate] to restore saved values without re-flagging them dirty. */
        internal fun putRaw(path: String, value: Any) { values[path.lowercase()] = value }
        operator fun get(path: String): Any? = values[path.lowercase()]
        fun getInt(path: String): Int? = when (val v = values[path.lowercase()]) {
            is Int -> v
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
        fun asMap(): Map<String, Any> = values.toMap()
    }

    companion object {
        @Volatile @JvmStatic var GRANT_ALL: Boolean = true
    }
}

object Users {
    private val users = ConcurrentHashMap<UUID, SpellshotUser>()

    /**
     * Hydrating inside computeIfAbsent means saved progression is restored the
     * first time anything touches the user, whichever code path gets there
     * first -- there is no separate join hook to miss.
     */
    fun of(uuid: UUID, username: String): SpellshotUser =
        users.computeIfAbsent(uuid) { SpellshotUser(it, username).also(UserStore::hydrate) }

    fun forget(uuid: UUID) { users.remove(uuid) }
    fun all(): Collection<SpellshotUser> = users.values

    val PlayerRef.userOrNull: SpellshotUser?
        get() = runCatching { of(uuid, username).also { it.playerRef = this } }.getOrNull()

    val PlayerRef.user: SpellshotUser
        get() = userOrNull ?: error("No SpellshotUser for PlayerRef")

    val Player.userOrNull: SpellshotUser?
        get() {
            val ref = reference ?: return null
            val playerRef = ref.store.getComponent(ref, PlayerRef.getComponentType()) ?: return null
            return playerRef.userOrNull
        }
}

