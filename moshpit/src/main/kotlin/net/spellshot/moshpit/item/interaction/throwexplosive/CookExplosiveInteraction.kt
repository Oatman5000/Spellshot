package net.spellshot.moshpit.item.interaction.throwexplosive

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.EnumCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import net.spellshot.core.framework.InteractionBinder
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.ext.property
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Pulls the pin. Runs the instant the throw button goes down, before the charge.
 *
 * The grenade's Primary is a `Charging` interaction: press, hold, release. The
 * engine only reaches [ThrowExplosiveInteraction] on release, from whichever `Next`
 * branch the hold time landed in -- so an interaction chain has exactly one place
 * that can observe the press, and this is it.
 *
 * That is what makes cooking possible. The fuse starts here rather than at the
 * throw, so holding the button burns it down: release late and the grenade goes off
 * sooner, hold past the fuse entirely and [CookInHandSystem] detonates it where you
 * stand. The client already draws the charge bar (`DisplayProgress: true`), which
 * doubles as the fuse indicator for free.
 *
 * **The item is deliberately not consumed here.** The grenades set
 * `CancelOnItemChange: true`, so taking it out of the hotbar now would cancel the
 * charge that is still running -- the release would never fire, and every grenade
 * would cook off in your hand instead of throwing. [ThrowExplosiveInteraction]
 * consumes it on a successful spawn instead.
 *
 * The explosion fields are duplicated from the throw step because a grenade that
 * cooks off never becomes a projectile: there is no [ThrowExplosiveComponent] to
 * read them from, so the cook record has to carry its own copy.
 *
 * [explosiveType] is the one field the grenade assets deliberately do *not* set on
 * this step, and it must stay that way for the impact grenade. Its throw step is
 * `ExplosiveType: Impact`, meaning "detonate on collision" -- copied onto the cook
 * record, an impact grenade whose fuse ran out in your hand would sit there waiting
 * for a collision that is never coming, and simply never go off. A hand detonation
 * is always immediate, so this defaults to TIMED for both grenades.
 */
class CookExplosiveInteraction(
    var explosionParticleId: String = "",
    var explosionSoundId: String = "",
    var damageRadius: Double = 8.0,
    var damageAmount: Double = 8.0,
    var damageFalloff: Double = 0.5,
    var timerInMillis: Long? = null,
    var explosiveType: ThrowExplosiveType = ThrowExplosiveType.TIMED
) : SimpleInstantInteraction() {

    override fun firstRun(
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler
    ) {
        val playerRef = context.entity.playerRefOrNull ?: return
        val fuseMillis = timerInMillis ?: run {
            logger.error("Cook step has no timerInMillis; the grenade can never detonate")
            return
        }

        // Already cooking means the charge restarted -- a second press while the
        // first fuse burns. Keep the original: re-pressing must not refresh the
        // fuse, or a grenade could be held indefinitely by tapping.
        if (CookingExplosives.peek(playerRef.uuid) != null) return

        CookingExplosives.start(
            playerRef.uuid,
            CookingExplosive(
                explodeAt = Instant.now().plusMillis(fuseMillis),
                // Read now, while the held item is still addressable. CookInHandSystem
                // runs from a tick with no interaction context and could not find it.
                itemId = context.createHeldItemContext()?.itemStack?.itemId,
                projectileConfigId = "",
                explosionParticleId = explosionParticleId,
                explosionSoundId = explosionSoundId,
                damageRadius = damageRadius,
                damageAmount = damageAmount,
                damageFalloff = damageFalloff,
                type = explosiveType
            )
        )
        logger.debug("{} pulled the pin ({}ms fuse)", playerRef.username, fuseMillis)
    }

    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(CookExplosiveInteraction::class.java)
    }

    @Service
    class Binder : InteractionBinder<CookExplosiveInteraction>(
        CookExplosiveInteraction::class, ::CookExplosiveInteraction, {
            BuilderCodec.builder(
                CookExplosiveInteraction::class.java, { CookExplosiveInteraction() }, CODEC
            )
                .property(CookExplosiveInteraction::timerInMillis, Codec.LONG)
                .property(CookExplosiveInteraction::damageRadius, Codec.DOUBLE)
                .property(CookExplosiveInteraction::explosionParticleId, Codec.STRING)
                .property(CookExplosiveInteraction::explosionSoundId, Codec.STRING)
                .property(CookExplosiveInteraction::damageAmount, Codec.DOUBLE)
                .property(CookExplosiveInteraction::damageFalloff, Codec.DOUBLE)
                .property(
                    CookExplosiveInteraction::explosiveType,
                    EnumCodec(ThrowExplosiveType::class.java)
                )
        }
    )
}
