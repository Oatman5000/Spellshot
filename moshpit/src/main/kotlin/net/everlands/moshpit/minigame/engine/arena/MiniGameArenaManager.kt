package net.everlands.moshpit.minigame.engine.arena

import net.everlands.core.framework.Raycasts

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.shape.Box
import org.joml.Vector3d
import org.joml.Vector3f
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.OnSetup
import net.everlands.core.functions.schedule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MiniGameArenaManager(val plugin: JavaPlugin, val config: MiniGameArenasConfig.Binder) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @OnSetup
    fun setup() {
        try {
            MiniGameArenaResource.resourceType = plugin.chunkStoreRegistry.registerResource(
                MiniGameArenaResource::class.java,
                "MiniGameArenaResource",
                MiniGameArenaResource.CODEC
            )
        } catch (e: Exception) {
            logger.warn("Failed to register MiniGameArenaResource: ${e.message}")
        }
    }

    fun visualize(playerRef: PlayerRef, world: World, entry: MiniGameArenaEntry, despawnTimer: Long = 30000) {
        val spawnColor = ColorParseUtil.hexStringToRGBInt("#CBCBCB")
        val teamColor = ColorParseUtil.hexStringToRGBInt("#000080")
        val powerUpColor = ColorParseUtil.hexStringToRGBInt("#F88379")
        val redColor = ColorParseUtil.hexStringToRGBInt("#FF0000")
        val entities = mutableListOf<Ref<EntityStore>>()
        for ((key, transform) in entry.locations) {
            val color = if (key.contains("spawn")) {
                spawnColor
            } else if (key.contains("team")) {
                teamColor
            } else if (key.contains("powerup")) {
                powerUpColor
            } else {
                redColor
            }
            Raycasts.debugBoundingBox(
                playerRef, Box.horizontallyCentered(1.0, 1.0, 1.0), transform.position,
                Vector3d(), color, durationMs = despawnTimer.toInt()
            )
            val titleHolder = EntityStore.REGISTRY.newHolder()
            val projComp = ProjectileComponent("Projectile")
            titleHolder.putComponent(ProjectileComponent.getComponentType(), projComp)
            titleHolder.putComponent(
                TransformComponent.getComponentType(), TransformComponent(
                    Vector3d(transform.position).add(0.0, 1.1, 0.0),
                    com.hypixel.hytale.math.vector.Rotation3f()
                )
            )
            titleHolder.ensureComponent(UUIDComponent.getComponentType())
            if (projComp.projectile == null) {
                projComp.initialize()
                if (projComp.projectile == null) {
                    continue
                }
            }
            titleHolder.addComponent(
                NetworkId.getComponentType(),
                NetworkId(world.entityStore.store.getExternalData().takeNextNetworkId())
            )
            titleHolder.addComponent(Nameplate.getComponentType(), Nameplate(key))
            titleHolder.addComponent(
                DespawnComponent.getComponentType(),
                DespawnComponent(Instant.now().plusSeconds(30))
            )

            val titleEntity = world.entityStore.store.addEntity(titleHolder, AddReason.SPAWN)
            if (titleEntity != null) {
                entities.add(titleEntity)
            }
        }
        schedule(despawnTimer) {
            world.execute {
                for (entityRef in entities) {
                    world.entityStore.store.removeEntity(entityRef, RemoveReason.REMOVE)
                }
            }
        }
    }

    fun list(): List<MiniGameArena> {
        return config.value.arenas
    }

}


