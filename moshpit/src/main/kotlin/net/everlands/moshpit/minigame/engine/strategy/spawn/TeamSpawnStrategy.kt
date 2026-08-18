package net.everlands.moshpit.minigame.engine.strategy.spawn

import com.hypixel.hytale.math.vector.Transform
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.teleport
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam

class TeamSpawnStrategy(val arena: MiniGameArena) :
    SpawnStrategy {

    private val spawns: Map<String, List<Transform>> =
        arena.locations
            .filterKeys { it.startsWith("team:") }
            .mapNotNull { (key, value) ->
                key.split(":").getOrNull(1)?.let { teamName -> teamName to value }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, transforms) -> transforms.toList() }
            .toMap()

    override fun teleport(
        team: MiniGameTeam,
        participant: MiniGameParticipant
    ) {
        val targetWorld = arena.world
        val spawn = getSpawn(team, participant)
        val reference = participant.playerRef.referenceOrErr
        reference.store.teleport(participant.playerRef.referenceOrErr, targetWorld, spawn)
    }

    override fun getSpawn(team: MiniGameTeam, participant: MiniGameParticipant): Transform {
        return spawns[team.id]?.random()
            ?: error("Team ${team.id} not found in arena ${arena.id} spawns! Or no spawns defined")
    }
}
