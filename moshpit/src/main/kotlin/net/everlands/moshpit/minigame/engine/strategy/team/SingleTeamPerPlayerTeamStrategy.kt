package net.everlands.moshpit.minigame.engine.strategy.team

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam

class SingleTeamPerPlayerTeamStrategy(
    val session: MiniGameSession,
    val teamProvider: (playerRef: PlayerRef) -> MiniGameTeam = { MiniGameTeam(it.uuid.toString(), it.username) },
    val participantProvider: (playerRef: PlayerRef) -> MiniGameParticipant = { MiniGameParticipant(it.uuid, it) }
) : TeamStrategy {
    override fun distributePlayer(
        playerRef: PlayerRef
    ): MiniGameTeam {
        val team = teamProvider.invoke(playerRef)
        team.participants[playerRef.uuid] = participantProvider.invoke(playerRef)
        session.teams.add(team)
        return team
    }

    override fun leaveTeam(
        playerRef: PlayerRef,
        team: MiniGameTeam
    ): MiniGameParticipant {
        val participant =
            team.participants.remove(playerRef.uuid) ?: error("Player ${playerRef.uuid} is not in team ${team.id}!")
        if (team.participants.isEmpty()) {
            session.teams.remove(team)
        }
        return participant
    }
}
