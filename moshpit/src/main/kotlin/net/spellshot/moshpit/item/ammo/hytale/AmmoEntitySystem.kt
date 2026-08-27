package net.spellshot.moshpit.item.ammo.hytale

import net.spellshot.core.ext.playerRefOrNull

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.EcsService
import net.spellshot.moshpit.item.ammo.AmmoProvider
import net.spellshot.moshpit.ui.ComposedMainHUD
import net.spellshot.moshpit.ui.HUDComposer
import java.util.concurrent.TimeUnit

@EcsService
class AmmoEntitySystem(val ammoProvider: AmmoProvider) : DelayedEntitySystem<EntityStore>(0.01f) {

    private val currentHeldItemSlot: Cache<String, Int> = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build()

    private val selector = "#Ammo.Text"

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        val ref = player.reference ?: return
        val item = player.inventory.itemInHand
        val builder = UICommandBuilder()
        val lastItemSlot = currentHeldItemSlot.getIfPresent(player.playerRefOrNull?.username) ?: -1
        val hasChanged = lastItemSlot != player.inventory.activeHotbarSlot.toInt()
        currentHeldItemSlot.put(player.playerRefOrNull?.username, player.inventory.activeHotbarSlot.toInt())
        if (item == null) {
            builder.updateUI(player, "")
            builder.setAmmoVisible(false)
        } else {
            val maxAmmo = ammoProvider.getMaxAmmo(item)
            if (maxAmmo == null) {
                // Melee, grenades, syringe -- no magazine, so hide the whole
                // block rather than leaving an empty plate on screen.
                builder.updateUI(player, "")
                builder.setAmmoVisible(false)
                pushAmmoUI(player, builder)
                return
            }
            builder.setAmmoVisible(true)
            val currentAmmo = ammoProvider.getAmmo(item) ?: 0
            if (hasChanged) {
                ammoProvider.setClientAmmo(ref, currentAmmo)
            }
            builder.updateUI(player, "$currentAmmo/$maxAmmo")
        }
        // Update 5: the no-arg HudManager#getCustomHud() getter was removed in
        // favour of the keyed lookup.
        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY)
        if (hud is ComposedMainHUD) {
            if (hud.isHudRegistered("Ammo")) {
                hud.update(false, builder)
            }
        }
    }

    private fun UICommandBuilder.updateUI(player: Player, text: String) {
        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY)
        if (hud is ComposedMainHUD) {
            set(selector, text)
        }
    }

    /** Toggles the plate and its caption together (see Hud/Ammo.ui). */
    private fun UICommandBuilder.setAmmoVisible(visible: Boolean) {
        set("#AmmoPlate.Visible", visible)
        set("#AmmoCaption.Visible", visible)
    }

    /** Shared with the early return above so the hide actually reaches the client. */
    private fun pushAmmoUI(player: Player, builder: UICommandBuilder) {
        val hud = player.hudManager.getCustomHud(HUDComposer.MAIN_HUD_KEY)
        if (hud is ComposedMainHUD && hud.isHudRegistered("Ammo")) {
            hud.update(false, builder)
        }
    }

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

}
