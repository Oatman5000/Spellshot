package net.spellshot.moshpit.item.interaction

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionState
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.WaitForDataFrom
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import net.spellshot.core.ext.property
import net.spellshot.moshpit.item.InteractionRegistry

class CheckAmmoInteraction(
    var checkType: String = CheckAmmoType.EMPTY.name
) : SimpleInstantInteraction() {

    override fun getWaitForDataFrom(): WaitForDataFrom {
        return WaitForDataFrom.Server
    }

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        val itemContext = context.createHeldItemContext() ?: return
        val ammoProvider = InteractionRegistry.instance.ammoProvider
        val ammo = ammoProvider.getAmmo(itemContext.itemStack) ?: 0
        if (checkType == CheckAmmoType.EMPTY.name) {
            if (ammo <= 0) {
                context.state.state = InteractionState.Failed
                return
            }
            context.state.state = InteractionState.Finished
        } else if (checkType == CheckAmmoType.FULL.name) {
            val maxAmmo = ammoProvider.getMaxAmmo(itemContext.itemStack) ?: 0
            if (ammo >= maxAmmo) {
                context.state.state = InteractionState.Failed
                return
            }
            context.state.state = InteractionState.Finished
        }

    }

    companion object {
        val CODEC: BuilderCodec<CheckAmmoInteraction?> = BuilderCodec.builder<CheckAmmoInteraction?>(
            CheckAmmoInteraction::class.java, { CheckAmmoInteraction() }, SimpleInstantInteraction.CODEC
        )
            .property(CheckAmmoInteraction::checkType, Codec.STRING)
            .build()
    }
}

enum class CheckAmmoType {
    FULL,
    EMPTY
}
