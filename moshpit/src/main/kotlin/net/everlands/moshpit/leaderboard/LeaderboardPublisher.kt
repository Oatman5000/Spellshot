package net.everlands.moshpit.leaderboard

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.framework.Config
import net.everlands.core.framework.OnStart
import net.everlands.core.framework.UserStore
import net.everlands.core.framework.launchGlobal
import net.everlands.core.leaderboard.BoardId
import net.everlands.core.leaderboard.LeaderboardRow
import net.everlands.core.leaderboard.LeaderboardSnapshot
import net.everlands.core.leaderboard.RedisClient
import net.everlands.core.leaderboard.killDeathRatio
import net.everlands.moshpit.moshpit.MoshpitGame
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Serializable
@SerialName("minigame.LeaderboardPublisherConfig")
data class LeaderboardPublisherConfig(
    @Description("Seconds between snapshot publishes.")
    val intervalSeconds: Long = 120,

    @Description("Rows kept per board. The UI shows far fewer; this is the cap on what is published.")
    val topN: Int = 50,

    @Description(
        "Minimum kills before a player appears on the K/D board. Without a floor " +
            "one player with a single kill and no deaths tops it forever."
    )
    val minKillsForKd: Int = 25,
) {
    @Component
    class Binder : Config<LeaderboardPublisherConfig>(LeaderboardPublisherConfig())
}

/**
 * Publishes the leaderboard for the lobby to read.
 *
 * The game server owns the stats, so it does the ranking and writes a finished
 * snapshot; the lobby only draws it. See [LeaderboardSnapshot] for why it is one
 * blob rather than live queries.
 *
 * Does nothing at all when Redis is not configured -- a standalone game server
 * carries no cost for this.
 */
@Service
class LeaderboardPublisher(
    private val redis: RedisClient,
    private val config: LeaderboardPublisherConfig.Binder,
    private val redisConfig: net.everlands.core.leaderboard.RedisConfig.Binder,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @OnStart
    fun start() {
        if (!redisConfig.value.enabled) {
            logger.info("Leaderboard publishing disabled - no Redis host configured")
            return
        }
        launchGlobal {
            // Let the server finish booting before the first publish; UserStore
            // is loaded in setup() but players hydrate as they connect.
            delay(10_000)
            while (true) {
                runCatching { publishOnce() }
                    .onFailure { logger.warn("Leaderboard publish failed: ${it.message}") }
                delay(config.value.intervalSeconds * 1000L)
            }
        }
    }

    fun publishOnce() {
        val cfg = config.value
        val users = UserStore.snapshotAll()
        if (users.isEmpty()) return

        // One pass over the store, building every board's raw rows together --
        // reading users.json repeatedly per board would be wasteful and could
        // produce boards from slightly different moments.
        val rows = users.map { stored ->
            val data = stored.data
            fun stat(path: String): Int = (data[path.lowercase()] as? Number)?.toInt() ?: 0

            val perGameKills = MoshpitGame.entries.associate { game ->
                game.name to stat("minigame_${game.name.lowercase()}_kills")
            }
            val totalKills = perGameKills.values.sum()
            val totalDeaths = MoshpitGame.entries.sumOf { stat("minigame_${it.name.lowercase()}_deaths") }
            val totalWins = MoshpitGame.entries.sumOf { stat("minigame_${it.name.lowercase()}_wins") }

            Aggregate(
                uuid = stored.uuid.toString(),
                username = stored.username,
                perGameKills = perGameKills,
                kills = totalKills,
                deaths = totalDeaths,
                wins = totalWins,
                level = stat("level"),
                xp = stat("xp"),
            )
        }

        val boards = buildMap<String, List<LeaderboardRow>> {
            put(BoardId.GLOBAL_KILLS, rows.rank(cfg.topN) { it.kills.toLong() })
            put(BoardId.WINS, rows.rank(cfg.topN) { it.wins.toLong() })
            // Ranked on XP but displayed as level -- level alone ties constantly.
            put(BoardId.LEVEL, rows.rank(cfg.topN) { it.xp.toLong() })
            put(
                BoardId.KD,
                rows.filter { it.kills >= cfg.minKillsForKd }
                    .rank(cfg.topN) { (it.kd * 1000).toLong() }
            )
            for (game in MoshpitGame.entries) {
                put(
                    BoardId.killsFor(game.name),
                    rows.rank(cfg.topN) { (it.perGameKills[game.name] ?: 0).toLong() }
                )
            }
        }

        val snapshot = LeaderboardSnapshot(System.currentTimeMillis(), boards)
        if (redis.publish(snapshot)) {
            logger.debug("Published leaderboard: {} players, {} boards", rows.size, boards.size)
        }
    }

    private class Aggregate(
        val uuid: String,
        val username: String,
        val perGameKills: Map<String, Int>,
        val kills: Int,
        val deaths: Int,
        val wins: Int,
        val level: Int,
        val xp: Int,
    ) {
        val kd: Double get() = killDeathRatio(kills, deaths)
    }

    /** Sorts by [value] descending, drops zero-scores, and caps at [limit]. */
    private fun List<Aggregate>.rank(limit: Int, value: (Aggregate) -> Long): List<LeaderboardRow> =
        asSequence()
            .map { it to value(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (a, v) ->
                LeaderboardRow(
                    uuid = a.uuid,
                    username = a.username,
                    value = v,
                    kills = a.kills,
                    deaths = a.deaths,
                    wins = a.wins,
                    level = a.level,
                )
            }
            .toList()
}
