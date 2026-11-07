package net.spellshot.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.ext.transformOrNull
import net.spellshot.core.functions.executeRadial
import net.spellshot.moshpit.minigame.engine.KillWeapon
import net.spellshot.moshpit.minigame.engine.KillWeapons
import org.joml.Vector3d
import org.slf4j.LoggerFactory

/**
 * The blast itself, shared by a thrown grenade and one that cooks off in your hand.
 *
 * Extracted so the two cannot drift. A grenade that goes off at your feet because
 * you held it too long should do exactly what one that lands at your feet does.
 */
object Explosions {

    private val logger = LoggerFactory.getLogger(Explosions::class.java)

    /** Returned by getIndexOrDefault when an asset id is not registered. Real indices are >= 0. */
    private const val MISSING_ASSET_INDEX = -1

    private val damageCause by lazy { DamageCause.getAssetMap().getIndex("Gun") }

    /**
     * Applies particles, sound and radial damage at [position].
     *
     * Everything needed for damage is read before any of it is applied. The previous
     * version removed the entity and played the particle first, then bailed on
     * `?: return` if the sound index, damage amount or falloff were missing -- giving
     * a grenade that visibly exploded and hurt nobody.
     *
     * [source] null means the thrower is no longer around: damage still lands, it is
     * simply credited to the environment rather than to a player. Self-damage is
     * included, and is filtered downstream by
     * [net.spellshot.moshpit.minigame.engine.handler.MiniGameDamageHandler].
     */
    fun detonate(
        buffer: CommandBuffer<EntityStore>,
        position: Vector3d,
        source: Ref<EntityStore>?,
        radius: Double,
        damageAmount: Double?,
        damageFalloff: Double?,
        explosionParticleId: String?,
        explosionSoundId: String?,
        explosiveType: ThrowExplosiveType? = null,
    ) {
        explosionParticleId?.let { ParticleUtil.spawnParticleEffect(it, position, buffer) }

        explosionSoundId?.let { soundId ->
            // getIndexOrDefault, not getIndex.
            //
            // getIndex returns a primitive int, so the `== null` check this used to do
            // was dead code -- the compiler reported it as always false. An unknown
            // sound id would have sailed past the guard and been handed to the sound
            // system as whatever getIndex returns for a miss. getIndexOrDefault lets us
            // name the sentinel instead of guessing at one.
            val soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(soundId, MISSING_ASSET_INDEX)
            if (soundIndex == MISSING_ASSET_INDEX) {
                logger.warn("Explosion sound '{}' is not a known SoundEvent", soundId)
            } else {
                SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, buffer)
            }
        }

        if (damageAmount == null || damageFalloff == null) {
            logger.warn(
                "Explosive detonated with no damage configured (amount={}, falloff={})",
                damageAmount, damageFalloff
            )
            return
        }

        val damageSource = if (source != null) {
            Damage.EntitySource(source)
        } else {
            Damage.EnvironmentSource("Explosion")
        }

        // Every projectile in this mod damages with the "Gun" cause, so a mode reading
        // the kill afterwards cannot tell a frag from a rifle round. Marking the victim
        // here is the only point at which the difference still exists.
        val killWeapon = when (explosiveType) {
            ThrowExplosiveType.IMPACT -> KillWeapon.IMPACT_GRENADE
            ThrowExplosiveType.TIMED -> KillWeapon.FRAG_GRENADE
            null -> null
        }

        executeRadial(buffer, position, radius, { it.playerRefOrNull != null }) {
            val targetTransform = it.transformOrNull ?: return@executeRadial
            val distance = position.distance(targetTransform.position)
            if (distance >= radius) return@executeRadial
            val finalDamage = (damageAmount - damageFalloff * distance).coerceAtLeast(0.0)
            if (finalDamage <= 0.0) return@executeRadial
            if (killWeapon != null) {
                it.playerRefOrNull?.let { victim -> KillWeapons.mark(victim.uuid, killWeapon) }
            }
            DamageSystems.executeDamage(
                it, buffer, Damage(damageSource, damageCause, finalDamage.toFloat())
            )
        }
    }
}
