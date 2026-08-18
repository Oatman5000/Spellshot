package net.everlands.moshpit.minigame.elimination

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.framework.Config
import org.springframework.stereotype.Component

/**
 * Tuning for Elimination.
 *
 * The match is a series: one life per player per ROUND, and wiping the other
 * team takes the round rather than the match. [roundsToWin] is a first-to count,
 * so 3 is best-of-five.
 */
@Serializable
@SerialName("minigame.EliminationConfig")
data class EliminationConfig(
    @Description("Rounds a team must win to take the match. 3 = best of five, 2 = best of three.")
    val roundsToWin: Int = 3,

    @Description("Seconds between a round ending and the next one starting.")
    val intermissionSeconds: Int = 5,
) {
    /** Longest the series can run, used only for the scoreboard caption. */
    val maxRounds: Int get() = (roundsToWin * 2) - 1

    @Component
    class Binder : Config<EliminationConfig>(EliminationConfig())
}
