package net.everlands.core.leaderboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The leaderboard payload shared between the game server and the lobby.
 *
 * Lives in :core so both modules compile against one definition -- if this drifts
 * between the two jars the lobby silently renders nothing.
 *
 * Deliberately a single snapshot rather than live queries. The game server owns
 * the data and publishes a finished, already-ranked view; the lobby only reads
 * and draws. That keeps Redis usage to one SET and one GET, and means a lobby
 * with no Redis connection degrades to "no data yet" instead of erroring.
 */
@Serializable
@SerialName("everlands.LeaderboardSnapshot")
data class LeaderboardSnapshot(
    /** Epoch millis the snapshot was built, used for the "updated N ago" line. */
    val generatedAt: Long = 0L,

    /** Board id -> ranked rows, best first. See [BoardId]. */
    val boards: Map<String, List<LeaderboardRow>> = emptyMap(),
) {
    fun board(id: String): List<LeaderboardRow> = boards[id].orEmpty()

    companion object {
        /** Lenient so a lobby running an older jar still decodes a newer snapshot. */
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String): LeaderboardSnapshot? =
            runCatching { JSON.decodeFromString<LeaderboardSnapshot>(raw) }
                // Null here renders as "waiting for data from the game server", which
                // is indistinguishable from Redis simply being empty. Say which it is.
                .onFailure { logger.warn("Discarding unreadable leaderboard snapshot: {}", it.message) }
                .getOrNull()

        private val logger = org.slf4j.LoggerFactory.getLogger("everlands.leaderboard")
    }

    fun encode(): String = JSON.encodeToString(this)
}

/**
 * One row. [value] is whatever the board ranks on; kills/deaths/level ride along
 * so the UI can show the detail columns without a second lookup.
 */
@Serializable
data class LeaderboardRow(
    val uuid: String = "",
    val username: String = "",
    val value: Long = 0,
    val kills: Int = 0,
    val deaths: Int = 0,
    val wins: Int = 0,
    val level: Int = 0,
) {
    val kd: Double get() = killDeathRatio(kills, deaths)

    val kdText: String get() = killDeathRatioText(kills, deaths)
}

/**
 * The one K/D definition.
 *
 * Deaths of 0 would divide by zero, so an undefeated player is scored as though they
 * had one death — which for a player with no kills either is 0.0, not NaN.
 *
 * This lived in three places: [LeaderboardRow], the game server's leaderboard
 * publisher, and the loadout menu's stats panel. All three agreed, but nothing made
 * them agree, and a player comparing the menu against the board would have no way to
 * tell which was wrong if they ever drifted.
 */
fun killDeathRatio(kills: Int, deaths: Int): Double =
    if (deaths <= 0) kills.toDouble() else kills.toDouble() / deaths

/** [killDeathRatio] to two decimal places, as every surface displays it. */
fun killDeathRatioText(kills: Int, deaths: Int): String =
    String.format("%.2f", killDeathRatio(kills, deaths))

/**
 * Board identifiers.
 *
 * Per-mode kill boards use the MoshpitGame enum name lowercased, so adding a
 * mode needs no change here -- only the UI tab list.
 */
object BoardId {
    const val GLOBAL_KILLS = "global_kills"
    const val LEVEL = "level"
    const val KD = "kd"
    const val WINS = "wins"

    fun killsFor(gameName: String) = "kills_${gameName.lowercase()}"
}
