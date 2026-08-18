package net.everlands.moshpit.ui

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.framework.EcsService
import net.everlands.moshpit.killstreak.KillstreakManager

const val KILL_STREAK_HUD_KEY = "KillStreak"

/** Must match the number of #PipN elements declared in Hud/KillStreak.ui. */
const val KILL_STREAK_PIPS = 10

/**
 * Drives the killstreak counter (Hud/KillStreak.ui).
 *
 * There is no streak-changed event, so the value is polled -- the same approach
 * AmmoEntitySystem and GunSelectEntitySystem already use -- and UI is only
 * pushed when the number actually moves. A quarter-second tick is plenty for a
 * counter that changes at most once per kill.
 */
@EcsService
class KillStreakEntitySystem(
    val killstreak: KillstreakManager
) : DelayedEntitySystem<EntityStore>(0.25f) {

    private val shown = mutableMapOf<String, Int>()

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
        // Creative runs the stock Hytale interface; nothing of ours is on screen.
        if (player.gameMode == GameMode.Creative) return
        val playerRef = player.playerRefOrNull ?: return
        val name = playerRef.username ?: return

        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY)
        if (hud !is ComposedMainHUD || !hud.isHudRegistered(KILL_STREAK_HUD_KEY)) {
            // Drop the cached value so the counter is re-pushed once the layer is
            // back, rather than being skipped as "unchanged" against a fresh .ui.
            shown.remove(name)
            return
        }

        val streak = killstreak.currentStreak(playerRef.uuid)
        // Same as the weapon selector: a HUD rebuild unlights every pip without
        // the streak value changing, so the generation has to be part of the test.
        val gen = HudRefresh.generation(name)
        if (shown[name] == streak && shownGen[name] == gen) return
        shown[name] = streak
        shownGen[name] = gen

        // Pips only -- the numeric readout was removed from the .ui, so writing
        // #Streak / #StreakLabel here would target selectors that no longer exist.
        val builder = UICommandBuilder()
        for (i in 0 until KILL_STREAK_PIPS) {
            builder.set("#Pip$i.Visible", i < streak)
        }
        hud.update(false, builder)
    }
}
