package net.spellshot.moshpit.item

import net.spellshot.core.framework.HytaleService

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import net.spellshot.core.framework.InteractionBinders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.spellshot.moshpit.item.airstrike.RequestAirstrikeInteraction
import net.spellshot.moshpit.item.ammo.AmmoProvider
import net.spellshot.moshpit.item.interaction.CheckAmmoInContainerInteraction
import net.spellshot.moshpit.item.interaction.CheckAmmoInteraction
import net.spellshot.moshpit.item.interaction.ReloadInteraction
import net.spellshot.moshpit.item.interaction.ShootInteraction
import net.spellshot.moshpit.minigame.engine.MiniGameEngine

@Service
class InteractionRegistry(
    val plugin: JavaPlugin, val hytaleService: HytaleService, val ammoProvider: AmmoProvider,
    val engine: MiniGameEngine
) {

    init {
        instance = this
        hytaleService.onSetup {
            plugin.getCodecRegistry(Interaction.CODEC)
                .register("Shoot", ShootInteraction::class.java, ShootInteraction.CODEC)
            plugin.getCodecRegistry(Interaction.CODEC)
                .register("Reload", ReloadInteraction::class.java, ReloadInteraction.CODEC)
            plugin.getCodecRegistry(Interaction.CODEC)
                .register("CheckAmmo", CheckAmmoInteraction::class.java, CheckAmmoInteraction.CODEC)
            plugin.getCodecRegistry(Interaction.CODEC)
                .register(
                    "CheckAmmoInContainer",
                    CheckAmmoInContainerInteraction::class.java,
                    CheckAmmoInContainerInteraction.CODEC
                )
            plugin.getCodecRegistry(Interaction.CODEC)
                .register(
                    "RequestAirstrike",
                    RequestAirstrikeInteraction::class.java,
                    RequestAirstrikeInteraction.CODEC
                )
            registerInteractionBinders()
        }
    }

    /**
     * Registers every [InteractionBinder] declared as a Spring bean.
     *
     * Binders add themselves to [InteractionBinders] on construction, but nothing
     * consumed that list -- so ThrowExplosiveInteraction's codec was never handed
     * to the plugin and its assets failed to decode with
     * "No codec registered with for 'Type': ThrowExplosiveInteraction",
     * which silently stripped the throw step out of the grenade chain.
     *
     * These assets key off the class's simple name, unlike the short names above.
     */
    @Suppress("UNCHECKED_CAST")
    private fun registerInteractionBinders() {
        val registry = plugin.getCodecRegistry(Interaction.CODEC)
        val binders = InteractionBinders.all()
        if (binders.isEmpty()) {
            logger.warn("No InteractionBinder beans found - grenade codecs will not register")
        }
        for (binder in binders) {
            val name = binder.interactionClass.simpleName ?: continue
            try {
                // register() is typed to Interaction, not Any.
                val codec = (binder.codecFactory() as BuilderCodec.Builder<Interaction>).build()
                registry.register(name, binder.interactionClass.java as Class<out Interaction>, codec)
                logger.info("Registered interaction codec '$name'")
            } catch (e: Exception) {
                logger.error("Failed to register interaction codec '$name'", e)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InteractionRegistry::class.java)
        lateinit var instance: InteractionRegistry
    }
}

