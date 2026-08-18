package net.everlands.moshpit.minigame.elimination

import net.everlands.moshpit.minigame.engine.countdown.MiniGameCountdown
import org.slf4j.LoggerFactory

/**
 * Round clock for Elimination.
 *
 * Most matches will not reach zero -- a wipe ends the round early through
 * [EliminationMiniGameSession.win]. When it does expire, the result falls to
 * [EliminationMiniGameSession.findWinnerOnTimeout]: most players alive, then
 * most eliminations, then a draw.
 */
class EliminationCountdown(
    val eliminationSession: EliminationMiniGameSession,
    countdown: Int,
) : MiniGameCountdown<EliminationMiniGameSession>(eliminationSession, countdown) {

    override suspend fun onTick(remainingSeconds: Int) {
        // Same two-second grace as TDM: the HUD layer is not registered on the
        // very first ticks and updating it then logs a warning per player.
        if (remainingSeconds > countdown - 2) return
        try {
            eliminationSession.update()
        } catch (ex: Exception) {
            logger.error("Failed to update elimination session", ex)
        }
    }

    override suspend fun onFinish() {
        eliminationSession.win(eliminationSession.findWinnerOnTimeout())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EliminationCountdown::class.java)
    }
}
