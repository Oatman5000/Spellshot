package net.spellshot.moshpit.minigame.engine

import com.google.common.cache.CacheBuilder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** How a player was killed, as far as a game mode needs to care. */
enum class KillWeapon {
    /** Melee. The knife, or anything else that is not a firearm or an explosive. */
    KNIFE,

    /** A bullet from any firearm, including the airstrike's missiles. */
    GUN,

    /** A timed explosive -- the frag grenade. */
    FRAG_GRENADE,

    /** An explosive that detonates on impact. */
    IMPACT_GRENADE,
}

/**
 * Records what killed each player, for the moment the kill event is published.
 *
 * The engine gives us nothing usable here. Every projectile in this mod deals damage
 * with the `"Gun"` [com.hypixel.hytale.server.core.modules.entity.damage.DamageCause]
 * -- rifles, the airstrike and, until now, both grenades -- and neither
 * [MiniGameKillEvent] nor [MiniGameDeathEvent] carries a cause at all. So by the time
 * a mode sees the kill, a frag and a headshot are indistinguishable.
 *
 * Same approach as [net.spellshot.moshpit.item.airstrike.AirstrikeKills], which
 * already had to solve exactly this problem for killstreaks: mark the victim at the
 * point of damage, where the distinction still exists, and read it back a moment
 * later. Deliberately NOT solved by adding DamageCause assets -- the "Gun" cause is
 * load-bearing for One in the Chamber's lethal override, and re-pointing explosives
 * at a new cause would quietly change what OitC treats as a one-shot.
 *
 * The TTL only guards against a leak: MiniGameDeathHandler publishes in the same tick
 * as the damage, so entries are read almost immediately.
 */
object KillWeapons {

    private val victims = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build<UUID, KillWeapon>()

    /** Called at the point of damage, before the victim can die. */
    fun mark(uuid: UUID, weapon: KillWeapon) {
        victims.put(uuid, weapon)
    }

    /**
     * What killed this player, dropping the record.
     *
     * Null means nothing marked them: a fall, drowning, the void, or a melee hit from
     * something that is not our code. Callers decide what that means for their mode.
     */
    fun consume(uuid: UUID): KillWeapon? {
        val weapon = victims.getIfPresent(uuid)
        if (weapon != null) victims.invalidate(uuid)
        return weapon
    }
}
