package net.everlands.moshpit.ui

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.protocol.packets.interface_.HudComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import kotlinx.coroutines.delay
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.world
import net.everlands.core.framework.EcsService
import net.everlands.core.framework.OnStart
import net.everlands.core.framework.launchGlobal
import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.onWorld
import net.everlands.moshpit.loadout.LoadoutService
import net.everlands.moshpit.moshpit.MoshpitManager
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.MiniGameRespawnEvent
import org.springframework.context.event.EventListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

const val GUN_SELECT_HUD_KEY = "GunSelect"
const val GUN_SELECT_SLOTS = 4

/** Hotbar index the killstreak reward lands in, shown as the smaller 5th slot. */
const val KILLSTREAK_SLOT = 4

/** Only this item gets the 5th slot; see KillStreakConfig. */
const val KILLSTREAK_ITEM_ID = "RequestAirstrike"

/** "Open your inventory" prompt; dropped once the player has picked a loadout. */
const val LOADOUT_HINT_HUD_KEY = "LoadoutHint"

private val hudLogger: Logger = LoggerFactory.getLogger("EverlandsHud")

/**
 * Applies the Everlands HUD set.
 *
 * Declares what stays rather than what goes: setVisibleHudComponents replaces
 * the whole set, so anything omitted is hidden. That covers the hotbar
 * (replaced by the weapon selector), health and stamina (stamina is unlimited),
 * the vanilla ammo indicator (we draw our own), and the survival/builder
 * clutter -- utility and block selectors, builder legend, mana, oxygen, sleep,
 * status icons.
 */
internal fun applyEverlandsHud(player: Player, playerRef: PlayerRef) {
    try {
        player.hudManager.setVisibleHudComponents(
            playerRef,
            HudComponent.Reticle,        // crosshair
            HudComponent.Chat,
            HudComponent.Notifications,
            HudComponent.EventTitle,     // "TDM / NOW PLAYING", "DRAW"
            HudComponent.KillFeed,
            HudComponent.PlayerList,     // tab scoreboard
            HudComponent.Compass
        )
        // Diagnostic: log what the server believes is visible after the push, so
        // a stray element can be traced to either a component we failed to drop
        // or something the client draws outside this system entirely.
        hudLogger.info(
            "HUD set for ${playerRef.username}: {}",
            player.hudManager.visibleHudComponents.joinToString(", ")
        )
    } catch (e: Exception) {
        hudLogger.warn("Could not set Everlands HUD for ${playerRef.username}: ${e.message}")
    }
}

/**
 * Restores stock Hytale UI: default component set back on, custom layers torn
 * down. Used while a player is in Creative so building tools behave normally.
 */
internal fun restoreVanillaHud(player: Player, playerRef: PlayerRef) {
    try {
        player.hudManager.resetHud(playerRef)
    } catch (e: Exception) {
        hudLogger.warn("Could not restore vanilla HUD for ${playerRef.username}: ${e.message}")
    }
}

/**
 * Hides the vanilla hotbar and registers the weapon selector in its place.
 * [GunSelectEntitySystem] keeps the selector in sync and handles game-mode swaps.
 */
@Service
class GunSelectUI(val plugin: JavaPlugin, val composer: HUDComposer) {

    /**
     * Re-applies the Everlands HUD after a respawn.
     *
     * Dying hands the client a fresh HUD, and it rebuilds from its own defaults
     * rather than the set the server last pushed -- so the vanilla hotbar, health
     * and stamina bars all come back. This is the same client behaviour we
     * already work around after PlayerReady, so it uses the same fix: re-assert
     * the set once immediately and again after the client has settled.
     */
    @EventListener
    fun onRespawn(event: MiniGameRespawnEvent) {
        val playerRef = event.participant.playerRef
        val world = playerRef.world ?: return
        world.execute {
            val player = playerRef.playerOrNull ?: return@execute
            if (player.gameMode == GameMode.Creative) return@execute
            applyEverlandsHud(player, playerRef)
            composer.refresh(player, playerRef)
        }
        launchGlobal {
            for (delayMs in longArrayOf(500L, 1500L)) {
                delay(delayMs)
                playerRef.world?.onWorld {
                    val p = playerRef.playerOrNull ?: return@onWorld
                    if (p.gameMode == GameMode.Creative) return@onWorld
                    applyEverlandsHud(p, playerRef)
                    composer.refresh(p, playerRef)
                }
            }
        }
    }

    @OnStart
    fun start() {
        plugin.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            event.playerRef.store.externalData.world.execute {
                val playerRef = event.player.playerRefOrNull ?: return@execute

                composer.registerHud(event.player, playerRef, GUN_SELECT_HUD_KEY) {
                    handle { builder -> builder.append("Hud/GunSelect.ui") }
                }
                composer.registerHud(event.player, playerRef, KILL_STREAK_HUD_KEY) {
                    handle { builder -> builder.append("Hud/KillStreak.ui") }
                }
                // Only for players who have never chosen a loadout. The flag is
                // in customData, so UserStore persists it across restarts.
                val user = playerRef.userOrNull
                if (user == null || !LoadoutService.hasChosenLoadout(user)) {
                    composer.registerHud(event.player, playerRef, LOADOUT_HINT_HUD_KEY) {
                        handle { builder -> builder.append("Hud/LoadoutHint.ui") }
                    }
                }
                if (event.player.gameMode != GameMode.Creative) {
                    applyEverlandsHud(event.player, playerRef)
                }

                // The client builds its own HUD after PlayerReady and can overwrite
                // the set we just pushed, so re-apply once it has settled.
                launchGlobal {
                    for (delayMs in longArrayOf(1000L, 3000L)) {
                        delay(delayMs)
                        playerRef.world?.onWorld {
                            val p = playerRef.playerOrNull ?: return@onWorld
                            if (p.gameMode != GameMode.Creative) applyEverlandsHud(p, playerRef)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drives the selector highlight, and swaps the whole HUD when a player's game
 * mode changes.
 *
 * In Creative the stock Hytale interface is restored -- the build tools, hotbar
 * and selectors are the point of that mode, so overriding them is unhelpful.
 * Leaving Creative re-applies the Everlands set and re-pushes the composed
 * layer, which brings back the selector, ammo and minigame HUDs; the composer
 * still holds their registrations, so nothing needs re-registering.
 *
 * There is no "held slot changed" event in 0.5.7, so the active slot is read
 * each tick -- the same approach AmmoEntitySystem already uses -- and UI is
 * only pushed when it actually moves.
 */
@EcsService
class GunSelectEntitySystem(
    val composer: HUDComposer,
    val moshpit: MoshpitManager
) : DelayedEntitySystem<EntityStore>(0.05f) {

    private val shownSlot = mutableMapOf<String, Int>()
    private val shownMode = mutableMapOf<String, GameMode>()

    /** HUD generation last pushed to, so a rebuild forces a re-send. See [HudRefresh]. */
    private val shownGen = mutableMapOf<String, Int>()

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        val playerRef = player.playerRefOrNull ?: return
        val name = playerRef.username ?: return

        val mode = player.gameMode
        // published for off-thread readers (see PlayerModes)
        PlayerModes.set(playerRef.uuid, mode)
        if (shownMode[name] != mode) {
            val first = shownMode[name] == null
            shownMode[name] = mode
            if (mode == GameMode.Creative) {
                restoreVanillaHud(player, playerRef)
                leaveSession(playerRef)
            } else {
                applyEverlandsHud(player, playerRef)
                composer.refresh(player, playerRef)
                // Rejoin on the way back, but not on the first observation --
                // MoshpitManager already adds Adventure players on PlayerReady.
                if (!first) joinSession(playerRef)
            }
            shownSlot.remove(name)
        }
        if (mode == GameMode.Creative) return

        // Retire the loadout hint as soon as they have picked. Polled rather than
        // pushed so LoadoutService does not need a reference to the HUD composer;
        // the registration check keeps this to one unregister, not one per tick.
        if (composer.getHUD(playerRef, LOADOUT_HINT_HUD_KEY) != null) {
            val user = playerRef.userOrNull
            if (user != null && LoadoutService.hasChosenLoadout(user)) {
                afterTick(playerRef) { composer.unregisterHUD(player, playerRef, LOADOUT_HINT_HUD_KEY) }
            }
        }

        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY)
        if (hud !is ComposedMainHUD || !hud.isHudRegistered(GUN_SELECT_HUD_KEY)) return

        val inventory = player.inventory ?: return
        val active = inventory.activeHotbarSlot.toInt()
        // Re-push when the slot moves OR when the HUD was rebuilt underneath us --
        // a rebuild resets the icons to empty without the slot ever changing.
        val gen = HudRefresh.generation(name)
        if (shownSlot[name] == active && shownGen[name] == gen) return
        shownSlot[name] = active
        shownGen[name] = gen

        val builder = UICommandBuilder()
        val hotbar = inventory.hotbar
        for (i in 0 until GUN_SELECT_SLOTS) {
            builder.set("#Active$i.Visible", i == active)
            builder.set("#Key$i.Style.TextColor", if (i == active) "#00D4FF" else "#8FA3B8")
            // slot indices are shorts on ItemContainer
            val stack = runCatching { hotbar?.getItemStack(i.toShort()) }.getOrNull()
            builder.set("#Icon$i.ItemId", stack?.itemId ?: "")
        }

        // Killstreak slot. Only rendered while the reward is actually held, so an
        // empty fifth frame never sits on screen -- unlike slots 0..3, which are
        // the player's class and always present.
        val extra = runCatching { hotbar?.getItemStack(KILLSTREAK_SLOT.toShort()) }.getOrNull()
        val hasExtra = extra != null && extra.itemId == KILLSTREAK_ITEM_ID
        builder.set("#Slot4.Visible", hasExtra)
        builder.set("#Icon4.ItemId", if (hasExtra) extra.itemId else "")
        builder.set("#Active4.Visible", hasExtra && active == KILLSTREAK_SLOT)
        builder.set(
            "#Key4.Style.TextColor",
            if (active == KILLSTREAK_SLOT) "#00D4FF" else "#8FA3B8"
        )
        hud.update(false, builder)
    }

    /**
     * Drops a player out of the running match when they switch to Creative, so
     * no respawn or map-rotation teleport can reach them while they build.
     */
    private fun leaveSession(playerRef: PlayerRef) {
        afterTick(playerRef) {
            MiniGameEngine.instance
                .getSessionByPlayerUUID<MiniGameSession>(playerRef.uuid)
                ?.removeParticipant(playerRef)
        }
    }

    /** Puts them back in when they return to Adventure. */
    private fun joinSession(playerRef: PlayerRef) {
        afterTick(playerRef) {
            if (!moshpit.isSessionActive) return@afterTick
            if (moshpit.currentSession.isParticipantInSession(playerRef.uuid)) return@afterTick
            moshpit.currentSession.addParticipant(playerRef)
        }
    }

    /**
     * Defers store-mutating work to the world's task queue.
     *
     * add/removeParticipant teleport the player and rewrite their inventory,
     * which the ECS refuses mid-tick: "Store is currently processing! Ensure you
     * aren't calling a store method from a system." Queueing it runs the same
     * work on the same thread, just after this tick has finished.
     */
    private fun afterTick(playerRef: PlayerRef, block: () -> Unit) {
        try {
            val world = playerRef.world ?: return
            world.execute {
                try {
                    block()
                } catch (e: Exception) {
                    hudLogger.warn("Session change failed for ${playerRef.username}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            hudLogger.warn("Could not queue session change for ${playerRef.username}: ${e.message}")
        }
    }
}
