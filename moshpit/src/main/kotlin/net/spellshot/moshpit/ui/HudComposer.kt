package net.spellshot.moshpit.ui

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HUDComposer {

    companion object {
        /**
         * Update 5 removed [CustomUIHud.DEFAULT_KEY] and the single-argument
         * `CustomUIHud(PlayerRef)` constructor, so each layer now owns a stable key.
         * Verified against Server-0.5.7: the surviving constructors are
         * `CustomUIHud(PlayerRef, String)` and `CustomUIHud(PlayerRef, String, int)`,
         * and HudManager keys its layers in a `Map<String, CustomUIHud>` â€” so adding
         * under the same key replaces, which is what the old `setCustomHud` did.
         */
        const val MAIN_HUD_KEY = "spellshot.moshpit.main"
    }

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    val huds = mutableMapOf<PlayerRef, MutableMap<String, Hud>>()

    fun registerHud(player: Player, playerRef: PlayerRef, key: String, hud: Hud.() -> Unit): Hud {
        val hud = Hud(player, playerRef).apply(hud)
        return registerHud(player, playerRef, key, hud)
    }

    fun registerHud(player: Player, playerRef: PlayerRef, key: String, hud: Hud): Hud {
        huds.computeIfAbsent(playerRef) { mutableMapOf() }[key] = hud
        refresh(player, playerRef)
        return hud
    }

    fun registerHud(player: Player, playerRef: PlayerRef, hud: AbstractHud): Hud {
        return registerHud(player, playerRef, hud.key, hud)
    }

    fun update(playerRef: PlayerRef, key: String, builder: UICommandBuilder.() -> Unit) {
        val hud = getHUD(playerRef, key) ?: return
        try {
            hud.update(builder)
        } catch (e: Exception) {
            logger.warn("Error while updating HUD for player ${playerRef.username}: ${e.message}")
        }

    }

    fun unregisterHUD(player: Player, playerRef: PlayerRef, key: String) {
        huds[playerRef]?.remove(key)
        refresh(player, playerRef)
    }

    fun getHUD(playerRef: PlayerRef, key: String): Hud? {
        return huds[playerRef]?.get(key)
    }

    fun refresh(player: Player, playerRef: PlayerRef) {
        try {
            // Update 5: setCustomHud(PlayerRef, CustomUIHud) removed, use the keyed API.
            player.hudManager.addCustomHud(playerRef, ComposedMainHUD(playerRef, this))
            // Rebuilding re-runs every .ui from source, so anything a system has
            // written since (weapon icons, killstreak pips) is back at its default.
            // Systems that only push on change would never re-send it -- that is
            // why the weapon slots went blank after a respawn while the guns were
            // still in the inventory. Bumping the generation tells them to re-push.
            HudRefresh.bump(playerRef.username)
        } catch (e: Exception) {
            logger.warn("Error while refreshing HUD for player ${playerRef.username}: ${e.message}")
        }
    }

    fun remove(player: Player, playerRef: PlayerRef) {
        huds.remove(playerRef)
        HudRefresh.forget(playerRef.username)
        // Verified against Server-0.5.7: HudManager exposes
        // removeCustomHud(PlayerRef, String), so the layer is dropped by key
        // rather than masked by pushing an empty layer over it.
        player.hudManager.removeCustomHud(playerRef, MAIN_HUD_KEY)
    }
}

class ComposedMainHUD(playerRef: PlayerRef, private val composer: HUDComposer) :
    CustomUIHud(playerRef, HUDComposer.MAIN_HUD_KEY) {

    override fun build(builder: UICommandBuilder) {
        val huds = composer.huds[playerRef] ?: return
        for ((_, hud) in huds) {
            try {
                hud.handleFn(builder)
            } catch (e: Exception) {
                composer.logger.warn("Error while building HUD for player ${playerRef.username}: ${e.message}")
            }
        }
    }

    fun isHudRegistered(key: String): Boolean {
        val huds = composer.huds[playerRef] ?: return false
        return huds.containsKey(key)
    }

    /*
     * Deliberately no onRemove() override.
     *
     * HudManager.addCustomHud calls onRemove() on the *previously registered*
     * layer before showing the replacement -- not just when the player leaves.
     * Clearing composer.huds here therefore wiped every registered layer as soon
     * as a second HUD was added, so build() found an empty map and rendered
     * nothing (missing Hardpoint and ammo HUDs).
     *
     * Per-player cleanup belongs to PlayerDisconnectListener -> HUDComposer.remove().
     */
}


open class Hud(val player: Player, val playerRef: PlayerRef) {

    var handleFn: ((builder: UICommandBuilder) -> Unit) = {}

    fun handle(handle: (builder: UICommandBuilder) -> Unit) {
        handleFn = handle
    }

    fun update(builder: UICommandBuilder.() -> Unit) {
        // Update 5: the no-arg HudManager#getCustomHud() getter was removed, the
        // keyed lookup replaces it.
        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY) ?: return
        if (hud !is ComposedMainHUD) return
        val uiBuilder = UICommandBuilder().apply(builder)
        hud.update(false, uiBuilder)
    }
}

abstract class AbstractHud(player: Player, playerRef: PlayerRef, val key: String) : Hud(player, playerRef) {

    init {
        handleFn = { builder ->
            handle(builder)
        }
    }

    abstract fun handle(builder: UICommandBuilder)
}
