package net.everlands.moshpit.minigame.engine.countdown

import net.everlands.core.ext.sendEventTitle
import net.everlands.core.ext.sendMessageFormat
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.launchGlobal
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.ext.setTimeLeft
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import net.everlands.moshpit.minigame.engine.model.StatisticalMiniGameParticipant
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.progression.ExperienceConfig
import net.everlands.moshpit.progression.ExperienceFFAConfig
import net.everlands.moshpit.progression.ProgressionManager
import net.everlands.moshpit.progression.recordWin

/**
 * Round clock for the timed, kill-ranked modes: FFA, One in the Chamber and Gun Game.
 *
 * All three end the same way — the clock runs out, whoever has the most kills wins,
 * and the top three take podium XP. Nothing about the mode changes that: OitC's one
 * bullet and Gun Game's weapon ladder are scoring modifiers, not different endings.
 *
 * They used to be three separate classes. `GunGameCountdown` and `OITCCountdown` were
 * byte-identical apart from their doc comments, and `FFACountdown` differed only in
 * which enum and XP table it named — so a fix to one (the win-tracking ordering note
 * below, for instance) had to be remembered three times.
 *
 * Ranking is computed from [MiniGameSession.teams] rather than each session's own
 * `findWinner()`/`topList`, which are themselves near-identical and differ only by the
 * participant subtype they cast to. Everything needed here — username and kills — is
 * on [StatisticalMiniGameParticipant], so no cast is required. These modes put one
 * player per team, so flattening the teams gives the same list those methods build.
 *
 * @param voteState the state to move to when the clock ends. Every mode uses 1, but it
 *   is passed rather than assumed so a mode with a different state machine still works.
 * @param xpTable picks this mode's podium table out of the shared experience config.
 */
open class KillRaceCountdown<S : MiniGameSession>(
    session: S,
    countdown: Int,
    private val moshpitGame: MoshpitGame,
    private val progressionManager: ProgressionManager,
    private val voteState: Int,
    private val xpTable: (ExperienceConfig) -> ExperienceFFAConfig,
) : MiniGameCountdown<S>(session, countdown) {

    override suspend fun onTick(remainingSeconds: Int) {
        // session.countdown, not the bare `countdown` -- that name resolves to this
        // class's own constructor parameter, the round length in seconds.
        session.runForEachParticipant {
            session.engine.hudComposer.update(it.playerRef, MiniGameEngine.DEFAULT_HUD_KEY) {
                setTimeLeft(session.countdown?.remainingSeconds ?: -1)
            }
        }
    }

    override suspend fun onFinish() {
        session.state = voteState

        val ranked = session.teams
            .flatMap { it.participants.values }
            .filterIsInstance<StatisticalMiniGameParticipant>()
            .sortedByDescending { it.kills }

        val winner = ranked.firstOrNull() ?: return
        val topThree = ranked.take(3)

        session.runForEachParticipant {
            it.playerRef.sendEventTitle(winner.playerRef.username, "WINNER")
            it.playerRef.sendMessageFormat(
                "\n<c><b>Game Over! <r>The winner is <c>${winner.playerRef.username}<r> with <a>${winner.kills} kills!"
            )
            it.playerRef.sendMessageFormat("<7>Top 3 Players:")
            for ((index, participant) in topThree.withIndex()) {
                it.playerRef.sendMessageFormat(
                    "  <c>#${index + 1}: <c>${participant.playerRef.username}<r> with <a>${participant.kills} kills"
                )
            }
        }

        val xpPerRank = xpTable(progressionManager.experienceConfig.value).xpPerRank
        topThree.forEachIndexed { index, participant ->
            val user = participant.playerRef.userOrNull ?: return@forEachIndexed
            launchGlobal {
                user.runLockGuarded {
                    // Recorded BEFORE the xp lookup: that lookup elvis-returns when a
                    // rank has no XP configured, which would silently skip the win.
                    if (index == 0) user.recordWin(moshpitGame)
                    val xp = xpPerRank[index + 1] ?: return@runLockGuarded
                    progressionManager.addXP(user, xp)
                }
            }
        }
    }
}
