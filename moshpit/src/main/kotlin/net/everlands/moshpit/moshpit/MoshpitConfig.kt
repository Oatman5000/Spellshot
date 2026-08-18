package net.everlands.moshpit.moshpit

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.MoshpitConfig")
data class MoshpitConfig(
    // Post-match lobby before the vote screen opens. Was 60s, which is a long
    // time to sit on a "GAME HAS ENDED" title while testing. Raise for release.
    val voteDuration: Int = 12,
    val maxPlayers: Int = 12,
    val serverRestartAfterGames: Int? = null,
    val serverRestartCountdown: Int? = null,

    val startVoteDelay: Int = 5,
    val gameVoteCountdown: Int = 10,
    val mapVoteCountdown: Int = 10,
    val endVoteDelay: Int = 3,

    val gameplayConfig: String = "MiniGame",
    val games: List<MoshpitGameConfig> = MoshpitGame.entries.map { MoshpitGameConfig(it) }
) {

    fun getGameConfig(game: MoshpitGame): MoshpitGameConfig {
        return games.find { it.game == game } ?: error("Game $game not found")
    }

    @Component
    class Binder : Config<MoshpitConfig>(MoshpitConfig())
}

@Serializable
data class MoshpitGameConfig(
    val game: MoshpitGame,
    val iconId: String = defaultIconFor(game),
    // Ids must match MiniGameArenasConfig. This was "moshpit", a placeholder id
    // no arena actually has, so the map vote filtered to nothing.
    val arenas: List<String> = defaultArenasFor(game),
    val duration: Int = 480,   // seconds -> 8 minute rounds
)

/**
 * Card icon on the game vote screen.
 *
 * OitC gets the revolver because that is literally the whole mode; the rest keep
 * the original helmet.
 */
private fun defaultIconFor(game: MoshpitGame): String = when (game) {
    MoshpitGame.OITC -> "Revolver"
    MoshpitGame.ELIMINATION -> "CombatKnife"
    MoshpitGame.GUNGAME -> "Famas"
    else -> "Armor_Copper_Head"
}

/** Arenas with team spawns and hardpoint markers -- safe for every mode. */
private val TEAM_READY_ARENAS = listOf("mirage", "dust2", "raid", "rust", "hijacked")

/**
 * Nuketown has plain spawns but no team spawns and no hardpoint markers, so it
 * is offered for FFA only. Handing it to TDM would leave both teams without
 * spawn points, and Hardpoint would fail outright with no capture point.
 */
private fun defaultArenasFor(game: MoshpitGame): List<String> = when (game) {
    // OitC is free-for-all on random spawns, exactly like FFA, so it can use
    // every arena including the ones with no team spawns or capture points.
    // Free-for-all modes run on random spawns, so they can also use the arenas
    // that have no team spawns or capture points.
    MoshpitGame.FFA, MoshpitGame.OITC, MoshpitGame.GUNGAME -> TEAM_READY_ARENAS + "nuketown"
    else -> TEAM_READY_ARENAS
}
