package net.spellshot.moshpit.item.airstrike

import com.google.common.cache.CacheBuilder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Records players killed by an airstrike, so killstreaks can ignore those kills.
 *
 * The missile deals damage as `Damage(EntitySource(owner), DamageCause "Gun")` --
 * the same cause a rifle uses -- and MiniGameKillEvent carries no cause at all,
 * so by the time the kill is published there is nothing left to distinguish an
 * airstrike from a headshot. Marking the victim at the point of damage is the
 * only place the distinction still exists.
 *
 * MiniGameDeathHandler publishes the death and kill events in the same tick as
 * the damage, so the entry is read almost immediately; the TTL only exists so a
 * victim who somehow never generates a kill event cannot leak.
 */
object AirstrikeKills {

    private val victims = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build<UUID, Long>()

    /** Called by [MissileEntitySystem] just before it applies lethal damage. */
    fun mark(uuid: UUID) {
        victims.put(uuid, System.currentTimeMillis())
    }

    /**
     * True if this player's death came from an airstrike. Single-shot: the entry
     * is dropped on read, so a later genuine kill on the same victim still counts.
     */
    fun consume(uuid: UUID): Boolean {
        val hit = victims.getIfPresent(uuid) != null
        if (hit) victims.invalidate(uuid)
        return hit
    }
}
