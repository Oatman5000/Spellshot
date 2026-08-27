package net.spellshot.moshpit.moshpit.vote

import net.spellshot.core.framework.launch
import net.spellshot.core.framework.launchGlobal
import net.spellshot.core.ext.playerOrNull
import net.spellshot.moshpit.minigame.engine.countdown.MiniGameCountdown
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession
import net.spellshot.moshpit.moshpit.MoshpitManager
import org.slf4j.LoggerFactory

class MoshpitVoteCountdown<T : MiniGameSession>(val moshpitManager: MoshpitManager, session: T, countdown: Int) :
    MiniGameCountdown<T>(session, countdown) {

    companion object {
        private val logger = LoggerFactory.getLogger("MoshpitVoteCountdown")
    }

    override suspend fun onStart() {
        val world = session.arena.world
        world.launch {
            session.runForEachParticipant {
                try {
                    val player = it.playerRef.playerOrNull ?: return@runForEachParticipant
                    player.inventory.clear()
                } catch (ex: Exception) {
                    logger.error("Error while clearing player inventory during vote countdown", ex)
                }
            }
        }
    }

    override suspend fun onTick(remainingSeconds: Int) {}

    override suspend fun onFinish() {
        val games = moshpitManager.config.value.games
        if (games.isEmpty()) {
            logger.warn("VoteCountdown finished but no games are configured!")
            return
        }
        val restartAfterGames = moshpitManager.config.value.serverRestartAfterGames
        if (restartAfterGames != null && moshpitManager.totalGamesStarted >= restartAfterGames) {
            launchGlobal {
                moshpitManager.restartServer()
            }
            return
        }
        launchGlobal {
            moshpitManager.voteManager.startVote()
        }
    }

}
