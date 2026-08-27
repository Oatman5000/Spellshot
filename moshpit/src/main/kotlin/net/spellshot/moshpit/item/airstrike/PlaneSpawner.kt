package net.spellshot.moshpit.item.airstrike

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
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.putModel
import net.spellshot.core.ext.referenceOrNull

object PlaneSpawner {

    val jetOverHeadSoundAssetIndex by lazy {
        SoundEvent.getAssetMap().getIndex("SFX_JetOverHead")
    }

    val planeProjectileConfig by lazy {
        ProjectileConfig.getAssetMap().getAsset("Plane")
    }

    val modelAsset by lazy {
        ModelAsset.getAssetMap().getAsset("Jet")
    }

    fun spawn(
        ref: Ref<EntityStore>, deployPosition: Vector3d,
        spawnPosition: Vector3d,
        buffer: CommandBuffer<EntityStore>,
        direction: Vector3d
    ) {
        val projectileConfig = planeProjectileConfig ?: return
        val modelAsset = modelAsset ?: return

        val projectile = ProjectileModule.get().spawnProjectile(
            ref, buffer, projectileConfig, spawnPosition,
            direction
        )
        buffer.putModel(projectile, ModelComponent(Model.createScaledModel(modelAsset, 10.0f)))

        for (player in Universe.get().players) {
            val ref = player.referenceOrNull ?: continue
            SoundUtil.playSoundEvent2d(ref, jetOverHeadSoundAssetIndex, SoundCategory.SFX, buffer)
        }
    }
}
