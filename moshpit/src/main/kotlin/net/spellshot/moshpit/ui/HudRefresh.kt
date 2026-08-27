package net.spellshot.moshpit.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks when a player's composed HUD was rebuilt.
 *
 * The HUD systems are all "push on change": they compare the value they last
 * sent and return early if nothing moved. That is the right thing for network
 * traffic, but it breaks whenever [HUDComposer.refresh] runs, because the client
 * re-reads every .ui from source and every value a system wrote is reset to its
 * default -- empty weapon icons, unlit killstreak pips.
 *
 * The system's cached value still matches the player's real state, so it sends
 * nothing and the HUD stays blank until the value happens to change again. That
 * is what made the weapon slots look empty after a respawn while the guns were
 * still in the inventory.
 *
 * Refresh bumps a per-player counter; systems store the generation alongside
 * their cached value and re-push when it moves. A counter rather than a
 * consumed flag, because several systems independently observe the same refresh.
 */
object HudRefresh {

    private val generations = ConcurrentHashMap<String, Int>()

    /** Called by [HUDComposer.refresh] for every HUD rebuild. */
    fun bump(username: String?) {
        if (username == null) return
        generations.merge(username, 1) { a, b -> a + b }
    }

    fun generation(username: String): Int = generations[username] ?: 0

    fun forget(username: String?) {
        if (username != null) generations.remove(username)
    }
}
