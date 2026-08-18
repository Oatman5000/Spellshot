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

    private val prefix = locationPrefix.lowercase()

    /**
     * Read fresh each time rather than captured in a `val` at construction.
     *
     * The markers live on the world and load with it, so a strategy built before the
     * resource is available used to cache an empty list for the entire session -- the
     * arena stayed broken long after its cause had passed. Re-reading costs a map
     * filter per spawn, which is nothing next to a teleport.
     */
    val spawns: List<Transform>
        get() = arena.locations.filter { it.key.lowercase().startsWith(prefix) }.map { it.value }

    val queue = EvictingQueue.create<Transform>(5)

    override fun teleport(
        team: MiniGameTeam,
        participant: MiniGameParticipant
    ) {
        val targetWorld = arena.world
        val spawn = getSpawn(team, participant) ?: return
        val reference = participant.playerRef.referenceOrErr
        reference.store.teleport(participant.playerRef.referenceOrErr, targetWorld, spawn)
    }

    /**
     * Null when the arena has no markers matching [prefix].
     *
     * This used to be non-null and ended `return spawns.random()` -- inside the
     * fallback path of a `catch` whose only realistic cause was `spawns` being empty.
     * So the catch logged a tidy warning and then threw the identical
     * `NoSuchElementException` straight back out, which is how an empty arena surfaced
     * as an unhandled exception in MiniGameRespawnHandler rather than as the message
     * it had just written.
     */
    override fun getSpawn(team: MiniGameTeam, participant: MiniGameParticipant): Transform? {
        val available = spawns
        if (available.isEmpty()) {
            logger.error(
                "No '{}' markers on arena '{}' - cannot place {}. See the MiniGameArena error above for why.",
                prefix, arena.id, team.name.ifBlank { "player" }
            )
            return null
        }
        return try {
            val players = Universe.get().players
            val spawn = available.filterNot { queue.contains(it) }.maxByOrNull { spawn ->
                var x = players.minOfOrNull { it.transform.position.distanceSquared(spawn.position) } ?: 0.0
                if (x > 10) {
                    x += Math.random() * 30.0
                }
                x
            } ?: available.random()
            queue.add(spawn)
            spawn
        } catch (e: Exception) {
            logger.warn("Error picking a spawn on arena '${arena.id}': ${e.message}", e)
            available.random()
        }
    }
}
