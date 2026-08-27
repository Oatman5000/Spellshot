package net.spellshot.core.leaderboard

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.spellshot.core.framework.Config
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration

@Serializable
@SerialName("spellshot.RedisConfig")
data class RedisConfig(
    @Description("Redis host. Blank disables Redis entirely -- no publishing, no leaderboard.")
    //
    // 127.0.0.1 because the lobby and game server run on the same Ubuntu VM as
    // Redis. Safe to hardcode: a loopback address is not a secret, and Redis
    // bound to 127.0.0.1 is unreachable from outside the box.
    //
    // Override with SPELLSHOT_REDIS_HOST if Redis ever moves off the VM -- and
    // if it does, it will need a password, which belongs in the env var rather
    // than in here.
    val host: String = "127.0.0.1",

    val port: Int = 6379,

    @Description("Password, or blank for none.")
    val password: String = "",

    @Description("Key the leaderboard snapshot is stored under.")
    val leaderboardKey: String = "spellshot:leaderboard:v1",

    @Description("Seconds before a published snapshot expires. Stops the lobby showing a board from a game server that has been down for hours.")
    val snapshotTtlSeconds: Long = 900,
) {
    /**
     * Environment overrides.
     *
     * Config<T> in this codebase reads no file, so without these the Redis host
     * and password would be compiled into both jars and changing either would
     * mean a rebuild. Env vars keep the credential out of the artifact and let
     * the two AMP instances be pointed somewhere new by restarting.
     *
     *   SPELLSHOT_REDIS_HOST      overrides host
     *   SPELLSHOT_REDIS_PORT      overrides port
     *   SPELLSHOT_REDIS_PASSWORD  overrides password
     */
    val effectiveHost: String get() = env("SPELLSHOT_REDIS_HOST") ?: host
    val effectivePort: Int get() = env("SPELLSHOT_REDIS_PORT")?.toIntOrNull() ?: port
    val effectivePassword: String get() = env("SPELLSHOT_REDIS_PASSWORD") ?: password

    val enabled: Boolean get() = effectiveHost.isNotBlank()

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    @Component
    class Binder : Config<RedisConfig>(RedisConfig())
}

/**
 * Minimal Redis access for the leaderboard.
 *
 * Only two operations are needed -- publish a snapshot, read a snapshot -- so
 * this stays a thin wrapper rather than a general Redis service. Every call is
 * guarded: Redis being down must never take a game server or the lobby with it,
 * it should only mean the board is stale or empty.
 *
 * The pool is created lazily so a server with no Redis configured never opens a
 * socket.
 */
@Component
class RedisClient(private val config: RedisConfig.Binder) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var pool: JedisPool? = null

    @Volatile
    private var warnedUnreachable = false

    private fun poolOrNull(): JedisPool? {
        val cfg = config.value
        if (!cfg.enabled) return null
        pool?.let { return it }
        synchronized(this) {
            pool?.let { return it }
            return runCatching {
                val poolConfig = JedisPoolConfig().apply {
                    maxTotal = 4
                    maxIdle = 2
                    minIdle = 0
                    setMaxWait(Duration.ofSeconds(2))
                }
                val created = if (cfg.effectivePassword.isBlank()) {
                    JedisPool(poolConfig, cfg.effectiveHost, cfg.effectivePort, CONNECT_TIMEOUT_MS)
                } else {
                    JedisPool(poolConfig, cfg.effectiveHost, cfg.effectivePort, CONNECT_TIMEOUT_MS, cfg.effectivePassword)
                }
                pool = created
                logger.info("Redis pool created for {}:{}", cfg.effectiveHost, cfg.effectivePort)
                created
            }.onFailure { logger.warn("Could not create Redis pool: ${it.message}") }.getOrNull()
        }
    }

    /** Writes the snapshot with a TTL. Returns false if Redis is off or unreachable. */
    fun publish(snapshot: LeaderboardSnapshot): Boolean {
        val cfg = config.value
        val p = poolOrNull() ?: return false
        return runCatching {
            p.resource.use { jedis ->
                jedis.setex(cfg.leaderboardKey, cfg.snapshotTtlSeconds, snapshot.encode())
            }
            warnedUnreachable = false
            true
        }.onFailure { warnOnce("publish", it) }.getOrDefault(false)
    }

    /** Reads the snapshot, or null if Redis is off, unreachable, or the key expired. */
    fun readSnapshot(): LeaderboardSnapshot? {
        val cfg = config.value
        val p = poolOrNull() ?: return null
        return runCatching {
            val raw = p.resource.use { jedis -> jedis.get(cfg.leaderboardKey) }
            warnedUnreachable = false
            raw?.let(LeaderboardSnapshot::decode)
        }.onFailure { warnOnce("read", it) }.getOrNull()
    }

    /**
     * Logs the first failure then goes quiet.
     *
     * Both callers run on a timer, so an unreachable Redis would otherwise fill
     * the log with the same line every few seconds.
     */
    private fun warnOnce(op: String, t: Throwable) {
        if (warnedUnreachable) return
        warnedUnreachable = true
        logger.warn("Redis $op failed (further failures suppressed): ${t.message}")
    }

    fun shutdown() {
        // Deliberately unlogged: the process is going away, a pool that will not close
        // cleanly changes nothing, and a stack trace here would be noise during a
        // shutdown that is already succeeding.
        runCatching { pool?.close() }
        pool = null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2000
    }
}
