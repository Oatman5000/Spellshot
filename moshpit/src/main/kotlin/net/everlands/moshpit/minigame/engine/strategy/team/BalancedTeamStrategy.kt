package net.everlands.moshpit.minigame.engine.strategy.team

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam

/**
 * Puts each joining player on whichever existing team is smallest.
 *
 * For modes with fixed sides — TDM, Hardpoint, Elimination — as opposed to
 * [SingleTeamPerPlayerTeamStrategy], which creates a team per player for the
 * free-for-all modes. Teams are expected to exist already; this never creates or
 * removes one, which is why a team emptying out is left in place rather than dropped.
 *
 * TDM, Hardpoint and Elimination each shipped their own copy of this, identical apart
 * from the participant type they constructed. That is now the [participantProvider],
 * matching how [SingleTeamPerPlayerTeamStrategy] already handled the same problem.
 */
class BalancedTeamStrategy(
    val session: MiniGameSession,
    val participantProvider: (playerRef: PlayerRef) -> MiniGameParticipant,
) : TeamStrategy {

    override fun distributePlayer(playerRef: PlayerRef): MiniGameTeam {
        val team = session.teams.minBy { it.participants.size }
        team.participants[playerRef.uuid] = participantProvider(playerRef)
        return team
    }

    override fun leaveTeam(playerRef: PlayerRef, team: MiniGameTeam): MiniGameParticipant =
        team.participants.remove(playerRef.uuid)
            ?: error("Player ${playerRef.uuid} is not in team ${team.id}!")
}
