package net.everlands.moshpit.item.interaction

import net.everlands.core.framework.BlockBoxRaycaster

import org.joml.Vector3d

import net.everlands.core.framework.Raycasts

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.protocol.WaitForDataFrom
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.util.TargetUtil
import net.everlands.core.ext.estimateWorldTickLatency
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.transform
import net.everlands.moshpit.item.InteractionRegistry

class ShootInteraction(
    var bulletsPerShot: Int? = null,
    var bulletSpread: Double? = null,
    var maxDistance: Double? = null,
    var headDamage: Float? = null,
    var chestDamage: Float? = null,
    var legDamage: Float? = null,
) : SimpleInstantInteraction() {

    override fun getWaitForDataFrom(): WaitForDataFrom {
        return WaitForDataFrom.None
    }

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        val itemContext = context.createHeldItemContext() ?: return
        val look = TargetUtil.getLook(context.entity, context.entity.store)
        val world = context.entity.store.externalData.world
        val maxDistance = maxDistance ?: 50.0

        val ammoProvider = InteractionRegistry.instance.ammoProvider
        val ammo = ammoProvider.getAmmo(itemContext.itemStack) ?: (ammoProvider.getMaxAmmo(itemContext.itemStack) ?: 0)
        if (ammo <= 0) {
            return
        }
        ammoProvider.setAmmo(itemContext.container, itemContext.slot, itemContext.itemStack, ammo - 1)

        val playerRef = context.entity.playerRefOrNull ?: return
        val tickRewind = playerRef.estimateWorldTickLatency()
        var headshotHit = false
        for (i in 0 until (bulletsPerShot ?: 1)) {
            val dir = Vector3d(look.direction)
                .add(
                    (Math.random() - 0.5) * (bulletSpread ?: 0.0),
                    (Math.random() - 0.5) * (bulletSpread ?: 0.0),
                    (Math.random() - 0.5) * (bulletSpread ?: 0.0)
                )

            val normalizedDir = org.joml.Vector3d(dir).normalize()
            val back = 1.0
            val origin = Vector3d(look.position)
                .add(-normalizedDir.x * back, -normalizedDir.y * back, -normalizedDir.z * back)
            val (lastPos, initialHitPos) = BlockBoxRaycaster.raycast(world, origin, dir, maxDistance)
            val (_, hitPos, entity) = Raycasts.lineCheckEntities(
                world = world,
                start = origin,
                end = lastPos,
                entityPredicate = { it != context.entity },
                tickRewind = tickRewind
            )
            if (entity != null) {
                try {
                    val transform = entity.store.transform(entity)
                    val statMap = entity.store.getComponent(entity, EntityStatMap.getComponentType()) ?: continue
                    val movementState = entity.store.getComponent(entity, MovementStatesComponent.getComponentType()) ?: continue
                    val isCrouching = movementState.movementStates.crouching
                    val headDistance = if (isCrouching) 0.98 else 1.38
                    val chestDistance = if (isCrouching) 0.31 else 0.8
                    // Skipped rather than asserted. The raycast can report a hit entity
                    // with no hit position, and `!!` here meant a NullPointerException
                    // mid-shot; the two lines above already skip on a missing component.
                    val hit = hitPos ?: continue
                    val distance = hit.distance(transform.position)
                    val damage = when {
                        distance >= headDistance -> headDamage ?: 3f
                        distance >= chestDistance -> chestDamage ?: 2f
                        else -> legDamage ?: 1f
                    }
                    headshotHit = damage >= (headDamage ?: 3f)
                    val nextHealth = statMap.get(DefaultEntityStatTypes.getHealth())?.get()?.minus(damage) ?: continue
                    if (nextHealth <= 0f) {
                        world.execute {
                            val dmg = Damage(Damage.EntitySource(context.entity), gunDamageCauseIndex, damage)
                            DamageSystems.executeDamage(entity, entity.store, dmg)
                        }
                        break
                    }
                    val dmg = Damage(Damage.EntitySource(context.entity), gunDamageCauseIndex, damage)
                    DamageSystems.executeDamage(entity, entity.store, dmg)
                } catch (ex: Exception) {
                    // Per pellet, per shot. Keep it to one line -- a full trace here
                    // during a shotgun burst buries everything else in the console.
                    logger.warn("Damage application failed: {}", ex.message)
                }
            }
            if (entity != null && hitPos != null) {
                ParticleUtil.spawnParticleEffect("Gun_Impact", hitPos, context.entity.store)
            } else if (initialHitPos != null) {
                ParticleUtil.spawnParticleEffect("Gun_Impact", initialHitPos, context.entity.store)
            }
        }
        if (headshotHit) {
            val player = context.entity.playerRefOrNull ?: return
            SoundUtil.playSoundEvent2dToPlayer(player, headshotSoundIndex, SoundCategory.SFX)
        }
    }


    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ShootInteraction::class.java)

        val headshotSoundIndex by lazy(LazyThreadSafetyMode.NONE) {
            SoundEvent.getAssetMap().getIndex("Bullet_Headshot")
        }
        val gunDamageCauseIndex by lazy(LazyThreadSafetyMode.NONE) {
            DamageCause.getAssetMap().getIndex("Gun")
        }
        val CODEC: BuilderCodec<ShootInteraction?> = BuilderCodec.builder<ShootInteraction?>(
            ShootInteraction::class.java, { ShootInteraction() }, SimpleInstantInteraction.CODEC
        )
            .append(
                KeyedCodec("BulletsPerShot", Codec.INTEGER),
                { interaction, value -> interaction?.bulletsPerShot = value },
                { i -> i?.bulletsPerShot }).add()

            .append(
                KeyedCodec("BulletSpread", Codec.DOUBLE),
                { interaction, value -> interaction?.bulletSpread = value },
                { i -> i?.bulletSpread }).add()

            .append(
                KeyedCodec("MaxDistance", Codec.DOUBLE),
                { interaction, value -> interaction?.maxDistance = value },
                { i -> i?.maxDistance }).add()

            .append(
                KeyedCodec("HeadDamage", Codec.FLOAT),
                { interaction, value -> interaction?.headDamage = value },
                { i -> i?.headDamage }).add()

            .append(
                KeyedCodec("ChestDamage", Codec.FLOAT),
                { interaction, value -> interaction?.chestDamage = value },
                { i -> i?.chestDamage }).add()

            .append(
                KeyedCodec("LegDamage", Codec.FLOAT),
                { interaction, value -> interaction?.legDamage = value },
                { i -> i?.legDamage }).add()
            .build()
    }
}

