package net.spellshot.lobby.leaderboard

import kotlinx.coroutines.delay
import net.spellshot.core.framework.OnStart
import net.spellshot.core.framework.launchGlobal
import net.spellshot.core.leaderboard.BoardId
import net.spellshot.core.leaderboard.LeaderboardSnapshot
import net.spellshot.core.leaderboard.RedisClient
import net.spellshot.core.leaderboard.RedisConfig
import net.spellshot.lobby.LobbyConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** A tab: which board to show and what to call it. */
data class BoardTab(val id: String, val label: String)

/**
 * Holds the latest leaderboard snapshot for the lobby.
 *
 * Polled on a timer and cached, so opening the menu never blocks on a network
 * call -- the page reads whatever was last fetched. If Redis is unreachable the
 * cache simply goes stale and the page says so; nothing throws.
 */
@Service
class LeaderboardService(
    private val redis: RedisClient,
    private val redisConfig: RedisConfig.Binder,
    private val lobbyConfig: LobbyConfig.Binder,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Backing field plus a read-only accessor, rather than `var ... private set`.
    // The Spring compiler plugin makes @Service classes open, and Kotlin forbids
    // a private setter on an open property.
    @Volatile
    private var cached: LeaderboardSnapshot? = null

    val snapshot: LeaderboardSnapshot? get() = cached

    val enabled: Boolean get() = redisConfig.value.enabled

    /**
     * Tab order. The four summary boards first, then one per game mode.
     *
     * Mode names come from config rather than an enum because the lobby jar does
     * not depend on moshpit -- it cannot see MoshpitGame.
     */
    val tabs: List<BoardTab>
        get() = buildList {
            add(BoardTab(BoardId.GLOBAL_KILLS, "Global"))
            add(BoardTab(BoardId.WINS, "Wins"))
            add(BoardTab(BoardId.LEVEL, "Level"))
            add(BoardTab(BoardId.KD, "K/D"))
            for (mode in lobbyConfig.value.leaderboardModes) {
                add(BoardTab(BoardId.killsFor(mode), mode))
            }
        }

    @OnStart
    fun start() {
        if (!enabled) {
            logger.info("Leaderboard disabled - no Redis host configured")
            return
        }
        launchGlobal {
            while (true) {
                runCatching { refresh() }.onFailure { logger.warn("Leaderboard refresh failed: ${it.message}") }
                delay(lobbyConfig.value.leaderboardRefreshSeconds * 1000L)
            }
        }
    }

    fun refresh() {
        val fetched = redis.readSnapshot() ?: return
        cached = fetched
    }

    /** Human-readable age of the cached snapshot, for the header line. */
    fun updatedText(): String {
        val snap = snapshot ?: return if (enabled) "Waiting for data from the game server" else "Leaderboard unavailable"
        val ageSeconds = ((System.currentTimeMillis() - snap.generatedAt) / 1000L).coerceAtLeast(0)
        return when {
            ageSeconds < 60 -> "Updated just now"
            ageSeconds < 3600 -> "Updated ${ageSeconds / 60} min ago"
            else -> "Updated ${ageSeconds / 3600} h ago"
        }
    }
}
