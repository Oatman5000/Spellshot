package net.spellshot.moshpit.minigame.engine.powerup

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.logger.HytaleLogger
import org.joml.Vector3d
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.OnSetup
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession
import net.spellshot.moshpit.minigame.engine.powerup.ecs.PowerUpComponent
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@Service
class PowerUpManager(val engine: MiniGameEngine) {

    private val logger = HytaleLogger.forEnclosingClass()

    private val scheduledSpawns = mutableListOf<PowerUpScheduledSpawn>()

    @OnSetup
    fun setup() {
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate({
            for (game in engine.games) {
                if (!game.powerUps) continue
                for (session in game.sessions) {
                    val arena = session.arena
                    val world = arena.world
                    val scheduledSpawns = getScheduledSpawnsByArena(arena)
                    val powerUpSpawns = arena.locations.filter { it.key.lowercase().startsWith("powerup:") }
                    world.execute {
                        val populatedPowerUps = mutableSetOf<String>()
                        world.entityStore.store.forEachChunk(
                            PowerUpComponent.componentType,
                            BiConsumer { chunk, buffer ->
                                for (i in 0 until chunk.size()) {
                                    val comp = chunk.getComponent(i, PowerUpComponent.componentType) ?: continue
                                    if (arena.id != comp.arenaId) continue
                                    val key = comp.powerUpKey ?: continue
                                    populatedPowerUps.add(key)
                                }
                            })
                        for (powerUpSpawn in powerUpSpawns) {
                            if (populatedPowerUps.contains(powerUpSpawn.key) || scheduledSpawns.any { it.key == powerUpSpawn.key }) continue
                            val parts = powerUpSpawn.key.split(":")
                            if (parts.size < 2) {
                                logger.atWarning()
                                    .log("Invalid power-up spawn key ${powerUpSpawn.key} in arena ${arena.id}")
                                continue
                            }
                            val type = PowerUpType.valueOf(parts[1])
                            scheduleSpawn(session, powerUpSpawn.key, type, Vector3d(powerUpSpawn.value.position))
                        }
                        for (spawn in this.scheduledSpawns.toList()) {
                            if (arena.id != spawn.arena.id) {
                                this.scheduledSpawns.remove(spawn)
                                continue
                            }
                            if (Instant.now().isAfter(spawn.scheduledAt)) {
                                spawn(
                                    spawn.session,
                                    spawn.key,
                                    spawn.type,
                                    spawn.position
                                )
                                this.scheduledSpawns.remove(spawn)
                            }
                        }
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun getScheduledSpawnsByArena(arena: MiniGameArena): List<PowerUpScheduledSpawn> {
        return scheduledSpawns.filter { it.arena.id == arena.id }
    }

    fun scheduleSpawn(session: MiniGameSession, key: String, type: PowerUpType, position: Vector3d) {
        scheduledSpawns.add(
            PowerUpScheduledSpawn(
                session = session,
                arena = session.arena,
                key = key,
                type = type,
                position = position,
                scheduledAt = Instant.now().plusSeconds(type.respawnTimeInSeconds)
            )
        )
    }

    fun spawn(session: MiniGameSession, key: String, type: PowerUpType, position: Vector3d) {
        val world = session.arena.world
        val holder = EntityStore.REGISTRY.newHolder()
        // Spawn at the anchor height, not at the marker.
        //
        // Markers are recorded standing on a surface, so their Y is ground level.
        // The entity model's origin is its centre, so placing it at the marker
        // buries the lower half. The +0.6 lift used to arrive a tick later, from
        // PowerUpRotationEntitySystem writing transform.position.y -- the power-up
        // was always spawning sunk and being lifted out. Making the anchor the
        // spawn position means the correct height is the starting state and does
        // not depend on a ticking system landing its write.
        val anchorY = position.y + ANCHOR_LIFT
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(
                Vector3d(position.x, anchorY, position.z),
                com.hypixel.hytale.math.vector.Rotation3f()
            )
        )
        holder.addComponent(HeadRotation.getComponentType(), HeadRotation(com.hypixel.hytale.math.vector.Rotation3f()))
        type.componentModifier.invoke(holder)
        holder.addComponent(
            PowerUpComponent.componentType,
            PowerUpComponent(
                powerUpKey = key,
                arenaId = session.arena.id,
                gameSessionId = session.id,
                powerUpType = type,
                anchorY = anchorY
            )
        )
        holder.addComponent(NetworkId.getComponentType(), NetworkId(world.entityStore.takeNextNetworkId()))

        holder.ensureComponent(UUIDComponent.getComponentType())
        holder.ensureComponent(PowerUpComponent.componentType)
        world.entityStore.store.addEntity(holder, AddReason.SPAWN)
    }

    private companion object {
        /**
         * How far above the marker the power-up floats.
         *
         * Markers sit on the ground and the model is centred on its origin, so
         * this needs to be at least half the model's height or the lower half
         * renders inside the floor. The bob in PowerUpRotationEntitySystem swings
         * +/- 0.2 around it, so the lowest point of the animation is
         * ANCHOR_LIFT - 0.2 and that is the figure that must clear the ground.
         */
        private const val ANCHOR_LIFT = 0.6
    }

}

data class PowerUpScheduledSpawn(
    val session: MiniGameSession,
    val arena: MiniGameArena,
    val key: String,
    val type: PowerUpType,
    val position: Vector3d,
    val scheduledAt: Instant
)
