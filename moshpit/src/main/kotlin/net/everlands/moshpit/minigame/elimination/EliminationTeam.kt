package net.everlands.moshpit.minigame.elimination

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam
import net.everlands.moshpit.minigame.engine.model.StatisticalMiniGameParticipant
import net.everlands.moshpit.minigame.tdm.TDMTeamDef

/**
 * Elimination reuses [TDMTeamDef] rather than declaring its own teams.
 *
 * Not laziness -- the enum constant names ODA/SENTINEL are the spawn-marker keys
 * baked into every arena ("team:ODA:spawn:N"), which TeamSpawnStrategy resolves
 * through team.id. A fresh enum with different constant names would leave both
 * teams with nowhere to spawn on every map.
 */
class EliminationTeam(val def: TDMTeamDef) : MiniGameTeam(def.name, def.displayName) {

    /** Rounds taken in the current series. First to EliminationConfig.roundsToWin takes the match. */
    var roundWins: Int = 0

    /** Players on this team who have not been eliminated yet. */
    val aliveCount: Int
        get() = participants.values.count { it is EliminationParticipant && !it.eliminated }

    /** Total eliminations this team has scored, used only to break a timeout tie. */
    val kills: Int
        get() = participants.values.sumOf { (it as? EliminationParticipant)?.kills ?: 0 }

    val isWiped: Boolean
        get() = participants.isNotEmpty() && aliveCount == 0
}

class EliminationParticipant(
    playerRef: PlayerRef,
) : StatisticalMiniGameParticipant(playerRef.uuid, playerRef) {

    /**
     * Set the moment this player dies and cleared only when the next ROUND
     * starts -- one life means one life per round. Everything downstream keys off
     * it: the respawn subscriber turns them into a spectator instead of reviving
     * them, the damage filter stops them being shot, and the win check counts
     * them out.
     */
    var eliminated: Boolean = false
}
// EliminationTeamStrategy used to live here. It was identical to TDM's and Hardpoint's
// apart from the participant type, so all three are now BalancedTeamStrategy.
