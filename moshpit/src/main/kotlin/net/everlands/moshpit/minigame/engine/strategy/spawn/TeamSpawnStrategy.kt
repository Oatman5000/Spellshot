package net.everlands.moshpit.minigame.engine.strategy.spawn

import com.hypixel.hytale.math.vector.Transform
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.teleport
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam
import org.slf4j.LoggerFactory

class TeamSpawnStrategy(val arena: MiniGameArena) :
    SpawnStrategy {

    /**
     * `team:<NAME>:spawn:<n>` markers, grouped by team name.
     *
     * Computed per call rather than captured at construction, for the reason given on
     * [RandomSpawnStrategy.spawns]: markers load with the world, and a strategy built
     * before they are available used to stay empty for the whole session.
     */
    private val spawns: Map<String, List<Transform>>
        get() = arena.locations
            .filterKeys { it.startsWith("team:") }
            .mapNotNull { (key, value) ->
                key.split(":").getOrNull(1)?.let { teamName -> teamName to value }
            }
            .groupBy({ it.first }, { it.second })

    override fun teleport(
        team: MiniGameTeam,
        participant: MiniGameParticipant
    ) {
        val targetWorld = arena.world
        val spawn = getSpawn(team, participant) ?: return
        val reference = participant.playerRef.referenceOrErr
        reference.store.teleport(participant.playerRef.referenceOrErr, targetWorld, spawn)
    }

    override fun getSpawn(team: MiniGameTeam, participant: MiniGameParticipant): Transform? {
        val byTeam = spawns
        val forThisTeam = byTeam[team.id]
        if (forThisTeam.isNullOrEmpty()) {
            logger.error(
                "No 'team:{}:spawn:' markers on arena '{}' - cannot place this player. " +
                    "Teams with markers there: {}. See the MiniGameArena error above if that list is empty.",
                team.id, arena.id, byTeam.keys
            )
            return null
        }
        return forThisTeam.random()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TeamSpawnStrategy::class.java)
    }
}
