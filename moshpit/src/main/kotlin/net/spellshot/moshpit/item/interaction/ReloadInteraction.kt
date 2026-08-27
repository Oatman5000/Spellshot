package net.spellshot.moshpit.item.interaction

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.validation.Validators
import com.hypixel.hytale.protocol.InteractionState
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.WaitForDataFrom
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import net.spellshot.core.ext.countItemsInInventory
import net.spellshot.core.ext.takeItemsFromInventory
import net.spellshot.moshpit.item.InteractionRegistry
import net.spellshot.moshpit.item.ammo.hytale.UnlimitedAmmoComponent
import net.spellshot.moshpit.minigame.oitc.OITCRules

class ReloadInteraction(
    var ammo: Int? = null,
    var ammoItemId: String? = null,
    var ammoOperation: String? = null
) : SimpleInstantInteraction() {

    override fun getWaitForDataFrom() = WaitForDataFrom.Server

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        // One in the Chamber has no reload. Bullets are earned by killing, so
        // letting this run would refill the revolver to its magazine size and
        // dissolve the entire mode. Checked before anything else so no ammo is
        // consumed and no client state is touched.
        if (OITCRules.blocksReload(context.entity)) {
            context.state.state = InteractionState.Failed
            return
        }
        val ammoItemId = this.ammoItemId ?: return
        val maxAmmo = this.ammo ?: 30
        val itemContext = context.createHeldItemContext() ?: return
        val player = context.entity.store.getComponent(context.entity, Player.getComponentType()) ?: return
        val unlimitedAmmo =
            context.entity.store.getComponent(context.entity, UnlimitedAmmoComponent.componentType) != null
        val ammoProvider = InteractionRegistry.instance.ammoProvider

        val currentAmmo = ammoProvider.getAmmo(itemContext.itemStack) ?: 0
        val missingAmmo = (maxAmmo - currentAmmo).coerceAtLeast(0)
        if (missingAmmo == 0) return

        val ammoInContainer = player.countItemsInInventory(ammoItemId)
        if (ammoInContainer == 0 && !unlimitedAmmo) {
            context.state.state = InteractionState.Failed
            return
        }

        // Adds +1 ammo instead of reloading to full
        if (ammoOperation != null && ammoOperation == "ADDITIVE") {
            // Magazine already full â†’ nothing to do
            if (currentAmmo >= maxAmmo) {
                return
            }
            // No ammo available and not unlimited
            if (!unlimitedAmmo && ammoInContainer <= 0) {
                context.state.state = InteractionState.Failed
                return
            }
            val takeAmmo = 1
            if (!unlimitedAmmo) {
                player.takeItemsFromInventory(ammoItemId, takeAmmo)
            }
            val newAmmo = (currentAmmo + takeAmmo).coerceAtMost(maxAmmo)
            ammoProvider.setAmmo(
                itemContext.container,
                itemContext.slot,
                itemContext.itemStack,
                newAmmo
            )
            ammoProvider.setClientAmmo(context.entity, newAmmo)
            return
        }


        val takeAmmo = ammoInContainer.coerceAtMost(missingAmmo)
        if (!unlimitedAmmo) {
            player.takeItemsFromInventory(ammoItemId, takeAmmo)
        }
        val newAmmo = if (unlimitedAmmo) maxAmmo else currentAmmo + takeAmmo
        ammoProvider.setAmmo(itemContext.container, itemContext.slot, itemContext.itemStack, newAmmo)
        ammoProvider.setClientAmmo(context.entity, newAmmo)
    }


    companion object {

        val ammoAsset: Int by lazy(LazyThreadSafetyMode.NONE) {
            EntityStatType.getAssetMap().getIndex("GunAmmo")
        }

        val CODEC: BuilderCodec<ReloadInteraction?> = BuilderCodec.builder<ReloadInteraction?>(
            ReloadInteraction::class.java, { ReloadInteraction() }, SimpleInstantInteraction.CODEC
        ).append(
            KeyedCodec("Ammo", Codec.INTEGER),
            { interaction, value -> interaction?.ammo = value },
            { i -> i?.ammo }).add()

            .append(
                KeyedCodec("AmmoItemId", Codec.STRING),
                { interaction, value -> interaction?.ammoItemId = value },
                { i -> i?.ammoItemId })
            .addValidator(Validators.nonNull())
            .add()
            .append(
                KeyedCodec("AmmoOperation", Codec.STRING),
                { interaction, value -> interaction?.ammoOperation = value },
                { i -> i?.ammoOperation }).add()
            .build()
    }
}
