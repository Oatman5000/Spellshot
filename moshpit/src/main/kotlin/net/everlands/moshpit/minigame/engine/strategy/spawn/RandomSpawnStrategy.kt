package net.everlands.moshpit.minigame.engine.strategy.spawn

import com.google.common.collect.EvictingQueue
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.universe.Universe
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.teleport
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam
import org.slf4j.LoggerFactory

class RandomSpawnStrategy(val arena: MiniGameArena, locationPrefix: String = "spawn:") : SpawnStrategy {

    companion object {
        private val logger = LoggerFactory.getLogger(RandomSpawnStrategy::class.java)
    }

    val spawns = arena.locations.filter { it.key.lowercase().startsWith(locationPrefix) }.map { it.value }
    val queue = EvictingQueue.create<Transform>(5)

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
        try {
            val players = Universe.get().players
            val spawn = spawns.filterNot { queue.contains(it) }.maxByOrNull { spawn ->
                var x = players.minOfOrNull { it.transform.position.distanceSquared(spawn.position) } ?: 0.0
                if (x > 10) {
                    x += Math.random() * 30.0
                }
                x
            } ?: spawns.random()
            queue.add(spawn)
            return spawn
        } catch (e: Exception) {
            logger.warn("Error while getting spawn for team ${team.name}: ${e.message}")
        }
        return spawns.random()
    }
}
