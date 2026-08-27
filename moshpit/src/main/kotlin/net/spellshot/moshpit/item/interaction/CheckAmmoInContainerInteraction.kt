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
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import net.spellshot.core.ext.countItemsInInventory
import net.spellshot.moshpit.item.ammo.hytale.UnlimitedAmmoComponent

class CheckAmmoInContainerInteraction(
    var ammoItemId: String? = null,
) : SimpleInstantInteraction() {

    override fun getWaitForDataFrom() = WaitForDataFrom.Server

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        val ammoItemId = this.ammoItemId ?: return
        val player = context.entity.store.getComponent(context.entity, Player.getComponentType()) ?: return
        val unlimitedAmmo =
            context.entity.store.getComponent(context.entity, UnlimitedAmmoComponent.componentType) != null
        if (player.countItemsInInventory(ammoItemId) > 0 || unlimitedAmmo) {
            context.state.state = InteractionState.Finished
        } else {
            context.state.state = InteractionState.Failed
        }
    }

    companion object {
        val CODEC: BuilderCodec<CheckAmmoInContainerInteraction?> =
            BuilderCodec.builder<CheckAmmoInContainerInteraction?>(
                CheckAmmoInContainerInteraction::class.java,
                { CheckAmmoInContainerInteraction() },
                SimpleInstantInteraction.CODEC
            )
                .append(
                    KeyedCodec("AmmoItemId", Codec.STRING),
                    { interaction, value -> interaction?.ammoItemId = value },
                    { i -> i?.ammoItemId })
                .addValidator(Validators.nonNull())
                .add()
                .build()
    }
}
