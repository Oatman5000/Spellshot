package net.everlands.moshpit.item.airstrike

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import org.joml.Vector3d
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.model.config.Model
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.putModel
import java.time.Instant
import java.util.concurrent.TimeUnit

class MissileSpawner(
    val playerRef: PlayerRef,
    val initialPosition: Vector3d,
    val delayInMillis: Long = 3000,
    val offsetY: Double = 80.0,
    val pos1: Vector3d,
    val pos2: Vector3d,
    val amountPerSecond: Int = 10,
    val duration: Int
) {
    val id = playerRef.uuid.toString()
    val allowedToSpawn: Instant = Instant.now().plusMillis(delayInMillis)

    var seconds: Int = duration

    val modelAsset by lazy {
        ModelAsset.getAssetMap().getAsset("Missile")
    }

    val projectileConfig by lazy {
        ProjectileConfig.getAssetMap().getAsset("Projectile_Config_Missile")
    }

    val explosionSoundIndex by lazy {
        SoundEvent.getAssetMap().getIndex("FragGrenade_Explode")
    }

    fun start() {
        SPAWNERS.put(id, this)
    }

    fun stop() {
        SPAWNERS.invalidate(id)
    }

    fun spawn(ref: Ref<EntityStore>, buffer: CommandBuffer<EntityStore>) {
        val config = projectileConfig ?: return
        val model = modelAsset ?: return
        val randomXBetween = Math.random() * (pos2.x - pos1.x) + pos1.x
        val randomZBetween = Math.random() * (pos2.z - pos1.z) + pos1.z
        val position = Vector3d(randomXBetween, initialPosition.y + offsetY, randomZBetween)
        val projectile = ProjectileModule.get().spawnProjectile(ref, buffer, config, position, Vector3d(1.0, 0.0, 0.0))
        buffer.addComponent(projectile, MissileComponent.componentType, MissileComponent(id))
        buffer.putModel(projectile, ModelComponent(Model.createScaledModel(model, 2.0f)))
    }

    fun explode(buffer: CommandBuffer<EntityStore>, position: Vector3d) {
        ParticleUtil.spawnParticleEffect("Explosion_Big", position, buffer)
        SoundUtil.playSoundEvent3d(explosionSoundIndex, SoundCategory.SFX, position, buffer)
    }

    companion object {
        val SPAWNERS: Cache<String, MissileSpawner> = CacheBuilder<String, MissileSpawner>.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build()
    }
}
