package net.spellshot.moshpit.progression

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.SpellshotUser
import net.spellshot.moshpit.moshpit.MoshpitGame

fun PlayerRef.getStatsValue(path: String): Int {
    val user = this.userOrNull
    return user?.customData?.getInt(path) ?: 0
}

fun PlayerRef.getMiniGameStatsValue(
    game: MoshpitGame,
    statsPath: String
) = getStatsValue(buildMiniGamePath(game) + statsPath)

fun SpellshotUser.getMoshpitStatsValue(path: String): Int {
    return MoshpitGame.entries.sumOf { customData.getInt(buildMiniGamePath(it) + path) ?: 0 }
}

suspend fun PlayerRef.applyStats(
    dsl: StatsApplyDsl.() -> Unit
) {
    val user = this.userOrNull ?: return
    user.applyStats(dsl)
}

suspend fun SpellshotUser.applyStats(
    dsl: StatsApplyDsl.() -> Unit
) {
    val statsApplyDsl = StatsApplyDsl().apply(dsl)
    for ((path, amount) in statsApplyDsl.values) {
        val currentValue = customData.getInt(path) ?: 0
        setCustomData(path, currentValue + amount)
    }
}

class StatsApplyDsl {
    val values = mutableMapOf<String, Int>()

    fun increment(path: String, amount: Int) {
        values[path.lowercase()] = amount
    }

    fun decrement(path: String, amount: Int) {
        values[path.lowercase()] = -amount
    }

    fun incrementMiniGame(
        game: MoshpitGame,
        statsPath: String,
        amount: Int
    ) {
        values[buildMiniGamePath(game) + statsPath.lowercase()] = amount
    }

    fun decrementMiniGame(
        game: MoshpitGame,
        statsPath: String,
        amount: Int
    ) {
        values[buildMiniGamePath(game) + statsPath.lowercase()] = -amount
    }
}

private fun buildMiniGamePath(game: MoshpitGame): String {
    return "minigame_${game.name.lowercase()}_"
}

/**
 * Records a match win for the leaderboard.
 *
 * Kills and deaths are incremented from the death handler, but nothing tracked
 * wins until the lobby leaderboard needed them. Called from each mode's win
 * path -- once per winning participant for team modes, once for the winner in
 * free-for-all modes.
 */
suspend fun SpellshotUser.recordWin(game: MoshpitGame) {
    applyStats { incrementMiniGame(game, "wins", 1) }
}
