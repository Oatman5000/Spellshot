package net.spellshot.moshpit.ui

import com.hypixel.hytale.protocol.GameMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Last known game mode per player, refreshed by [GunSelectEntitySystem].
 *
 * Exists so code running off the world thread can branch on game mode without
 * touching the ECS store -- reading a component from a network thread trips
 * Store.assertThread. The inbound packet filter in LoadoutInventoryRebind is
 * the case that needs it.
 */
object PlayerModes {

    private val modes = ConcurrentHashMap<UUID, GameMode>()

    fun set(uuid: UUID, mode: GameMode) {
        modes[uuid] = mode
    }

    fun forget(uuid: UUID) {
        modes.remove(uuid)
    }

    fun isCreative(uuid: UUID): Boolean = modes[uuid] == GameMode.Creative
}
