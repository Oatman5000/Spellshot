package net.everlands.moshpit.minigame.hardpoint

import net.everlands.moshpit.minigame.engine.countdown.MiniGameCountdown
import org.slf4j.LoggerFactory
import java.time.Instant

class HardpointCountdown(val game: HardpointMiniGame, val hpSession: HardpointMiniGameSession, countdown: Int) :
    MiniGameCountdown<HardpointMiniGameSession>(hpSession, countdown) {

    override suspend fun onTick(remainingSeconds: Int) {
        if (remainingSeconds <= countdown - 2) {
            try {
                if (Instant.now().isAfter(hpSession.nextPointChange)) {
                    session.switchPoint()
                }
            } catch (e: Exception) {
                // For some reason, this sometimes throws an exception.
                logger.warn("Failed to switch point", e)
            }
            try {
                hpSession.update()
            } catch (ex: Exception) {
                logger.error("Failed to update hardpoint session", ex)
            }
        }

    }

    override suspend fun onFinish() {
        hpSession.win()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HardpointCountdown::class.java)
    }

}
