package net.spellshot.moshpit.minigame.tdm

import net.spellshot.moshpit.minigame.engine.countdown.MiniGameCountdown
import org.slf4j.LoggerFactory

class TDMCountdown(val game: TDMMiniGame, val tdmSession: TDMMiniGameSession, countdown: Int) :
    MiniGameCountdown<TDMMiniGameSession>(tdmSession, countdown) {

    override suspend fun onTick(remainingSeconds: Int) {
        if (remainingSeconds <= countdown - 2) {
            try {
                tdmSession.update()
            } catch (ex: Exception) {
                logger.error("Failed to update tdm session", ex)
            }
        }

    }

    override suspend fun onFinish() {
        tdmSession.win()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TDMCountdown::class.java)
    }

}
