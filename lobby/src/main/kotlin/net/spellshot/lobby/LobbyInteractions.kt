package net.spellshot.lobby

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import net.spellshot.core.ext.builderCodec
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.framework.HytaleService
import net.spellshot.lobby.leaderboard.LeaderboardService
import net.spellshot.lobby.page.LeaderboardPage
import net.spellshot.lobby.page.ServerSelectorPage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Opens the server selector when the Navigator is used.
 *
 * Navigator.json (salvaged from the original lobby pack) binds both Primary and
 * Secondary to `"Type": "ServerSelectorInteraction"`, so that name has to exist
 * in the interaction codec registry or the item fails to decode and the
 * navigator silently does nothing.
 */
class ServerSelectorInteraction : SimpleInstantInteraction() {

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        val ref = context.entity
        val playerRef = ref.playerRefOrNull ?: return
        val player = ref.store.getComponent(ref, Player.getComponentType()) ?: return
        val config = LobbyInteractions.configBinder ?: return
        runCatching {
            player.pageManager.openCustomPage(ref, ref.store, ServerSelectorPage(playerRef, config))
        }.onFailure { LobbyInteractions.logger.warn("could not open server selector: ${it.message}") }
    }

    companion object {
        val CODEC: BuilderCodec<ServerSelectorInteraction> =
            builderCodec(::ServerSelectorInteraction).build()
    }
}

/**
 * Opens the leaderboard when the Leaderboard item is used.
 *
 * Leaderboard.json binds both Primary and Secondary to
 * `"Type": "LeaderboardInteraction"`, so the name has to be in the codec
 * registry or the item fails to decode entirely.
 */
class LeaderboardInteraction : SimpleInstantInteraction() {

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        val ref = context.entity
        val playerRef = ref.playerRefOrNull ?: return
        val player = ref.store.getComponent(ref, Player.getComponentType()) ?: return
        val service = LobbyInteractions.leaderboardService ?: return
        runCatching {
            player.pageManager.openCustomPage(ref, ref.store, LeaderboardPage(player, playerRef, service))
        }.onFailure { LobbyInteractions.logger.warn("could not open leaderboard: ${it.message}") }
    }

    companion object {
        val CODEC: BuilderCodec<LeaderboardInteraction> =
            builderCodec(::LeaderboardInteraction).build()
    }
}

/**
 * Registers the lobby's interaction codecs.
 *
 * Mirrors moshpit's InteractionRegistry: codecs must be registered during
 * @OnSetup, before the asset packs are decoded, or the item json referencing
 * them fails with "No codec registered with for 'Type'".
 */
@Service
class LobbyInteractions(
    val plugin: JavaPlugin,
    val hytaleService: HytaleService,
    val config: LobbyConfig.Binder,
    val leaderboard: LeaderboardService,
) {
    init {
        configBinder = config
        leaderboardService = leaderboard
        hytaleService.onSetup {
            runCatching {
                plugin.getCodecRegistry(Interaction.CODEC).register(
                    "ServerSelectorInteraction",
                    ServerSelectorInteraction::class.java,
                    ServerSelectorInteraction.CODEC
                )
                logger.info("Registered interaction codec 'ServerSelectorInteraction'")
            }.onFailure { logger.error("Failed to register ServerSelectorInteraction", it) }
            runCatching {
                plugin.getCodecRegistry(Interaction.CODEC).register(
                    "LeaderboardInteraction",
                    LeaderboardInteraction::class.java,
                    LeaderboardInteraction.CODEC
                )
                logger.info("Registered interaction codec 'LeaderboardInteraction'")
            }.onFailure { logger.error("Failed to register LeaderboardInteraction", it) }
        }
    }

    companion object {
        internal val logger = LoggerFactory.getLogger(LobbyInteractions::class.java)

        /** The interactions are built by the codec, not by Spring, so they read these here. */
        internal var configBinder: LobbyConfig.Binder? = null
        internal var leaderboardService: LeaderboardService? = null
    }
}
