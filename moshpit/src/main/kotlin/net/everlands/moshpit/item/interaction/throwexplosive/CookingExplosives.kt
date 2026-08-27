package net.everlands.moshpit.item.interaction.throwexplosive

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A grenade whose fuse is burning in someone's hand.
 *
 * Carries everything [CookInHandSystem] needs to detonate it where the player is
 * standing, because by then the interaction that created it is long gone.
 */
data class CookingExplosive(
    val explodeAt: Instant,
    val projectileConfigId: String,
    val explosionParticleId: String,
    val explosionSoundId: String,
    val damageRadius: Double,
    val damageAmount: Double,
    val damageFalloff: Double,
    val type: ThrowExplosiveType,
)

/**
 * Who is currently holding a live grenade.
 *
 * Deliberately a plain map keyed by player UUID rather than an ECS component. The
 * fuse has to survive things that destroy the player's entity -- death, respawn,
 * changing world -- because a grenade cooking when you die should still go off. A
 * component on the player entity would vanish with it.
 *
 * One at a time per player: pulling a second pin while the first is burning is not
 * a mechanic anyone asked for, and [ThrowExplosiveInteraction] treats the second
 * click as "throw" rather than "start another".
 */
object CookingExplosives {

    private val cooking = ConcurrentHashMap<UUID, CookingExplosive>()

    fun start(uuid: UUID, explosive: CookingExplosive) {
        cooking[uuid] = explosive
    }

    /** Removes and returns the cooking grenade, or null if there isn't one. */
    fun take(uuid: UUID): CookingExplosive? = cooking.remove(uuid)

    fun peek(uuid: UUID): CookingExplosive? = cooking[uuid]

    /** Everyone currently holding one, for the tick that detonates overdue fuses. */
    fun entries(): List<Map.Entry<UUID, CookingExplosive>> = cooking.entries.toList()

    fun clear(uuid: UUID) {
        cooking.remove(uuid)
    }
}
