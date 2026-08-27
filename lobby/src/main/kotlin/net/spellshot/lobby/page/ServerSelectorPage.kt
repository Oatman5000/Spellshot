package net.spellshot.lobby.page

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.connection.DisconnectType
import com.hypixel.hytale.protocol.packets.connection.QuicApplicationErrorCode
import com.hypixel.hytale.protocol.packets.connection.ServerDisconnect
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.builderCodec
import net.spellshot.core.ext.property
import com.hypixel.hytale.server.core.Message
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.core.framework.MessageParser
import net.spellshot.lobby.HandoffMode
import net.spellshot.lobby.LobbyConfig

/**
 * The server picker, built on the salvaged Pages/ServerSelector.ui.
 *
 * Selecting a server hands the player straight over via
 * `PlayerRef.referToServer(host, port)` -- Hytale's native player referral, see
 * "Multiserver Architecture" in the Hytale Server Manual. The other
 * [HandoffMode] values are diagnostics kept from working that out.
 */
class ServerSelectorPage(
    playerRef: PlayerRef,
    private val config: LobbyConfig.Binder,
) : InteractiveCustomUIPage<ServerSelectorPage.PageData>(
    // CanDismiss, not CantClose: the lobby menu is informational, so Escape
    // should close it. (The vote screen uses CantClose because you must vote.)
    playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun build(
        ref: Ref<EntityStore>,
        builder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        val cfg = config.value
        builder.append("Pages/ServerSelector.ui")
        builder.set("#Title #TitleLabel.Text", cfg.selectorTitle)

        cfg.servers.forEachIndexed { index, server ->
            builder.append("#ElementList", "Pages/ServerSelectorEntry.ui")
            builder.set("#ElementList[$index] #Name.Text", server.name)
            builder.set("#ElementList[$index] #Description.Text", server.description)
            // No address on the card: referToServer takes the player straight
            // there, so there is nothing for them to read or type.
            //
            // Background, not ItemId: the icon is a texture, because an ItemIcon
            // can only draw items registered on THIS server and the weapons live
            // in moshpit's pack.
            builder.set("#ElementList[$index] #Icon.Background", server.icon)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ElementList[$index] #Button",
                EventData.of("Action", server.id),
                false
            )
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DiscordButton",
            EventData.of("Action", DISCORD_ACTION),
            false
        )
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: PageData) {
        val action = data.action ?: return
        val cfg = config.value

        if (action == DISCORD_ACTION) {
            // Printed rather than opened: the client exposes no link-opening
            // action to server-driven UI, so the player copies it from chat.
            val invite = cfg.effectiveDiscordInvite
            if (invite.isBlank()) {
                playerRef.sendRawMessage("<7>No Discord invite is configured on this server.")
            } else {
                playerRef.sendRawMessage("<7>Join our Discord: <c>$invite")
            }
            close()
            return
        }

        val server = cfg.serverById(action) ?: return

        if (!server.isConfigured) {
            // Only reachable if someone deliberately blanks the host. Kept because a
            // referral to an empty address fails with a far less obvious message.
            playerRef.sendRawMessage("<7><c>${server.name}<7> has no address configured.")
            playerRef.sendRawMessage("<7>Set its host in <c>LobbyConfig<7> or <c>SPELLSHOT_GAME_HOST<7>.")
            logger.warn("Server entry '${server.id}' has no host configured")
            close()
            return
        }

        close()

        when (cfg.handoffMode) {
            // Native player referral. The server sends a referral packet naming
            // the target and the client opens a new connection to it -- no proxy
            // and no coordinator. See "Multiserver Architecture" in the Hytale
            // Server Manual.
            HandoffMode.Refer -> {
                playerRef.sendRawMessage("<7>Sending you to <c>${server.name}<7>...")
                runCatching { playerRef.referToServer(server.effectiveHost, server.effectivePort) }
                    .onFailure {
                        logger.warn("referToServer(${server.address}) failed: ${it.message}")
                        playerRef.sendRawMessage("<7>Could not send you over. Connect to <c>${server.address}<7> directly.")
                    }
            }

            HandoffMode.Message -> {
                playerRef.sendRawMessage("<c>${server.name}<7> -- connect to <c>${server.address}")
                playerRef.sendRawMessage("<7>Add it under <c>Direct Connect<7> in your server list.")
            }

            HandoffMode.Disconnect -> runCatching {
                playerRef.packetHandler.writeNoCache(
                    ServerDisconnect(
                        MessageParser.parse("Connect to ${server.address} to join ${server.name}.").formattedMessage,
                        DisconnectType.Disconnect
                    )
                )
            }.onFailure { logger.warn("clean disconnect failed: ${it.message}") }

            HandoffMode.DropConnection -> runCatching {
                // No disconnect packet -- drop the transport underneath the client
                // with a Timeout code, so it looks like the server vanished rather
                // than kicked. That is the condition FallbackServer is meant for.
                // 0.6.0 takes a FormattedMessage here rather than a raw String.
                playerRef.packetHandler.channel.closeApplicationConnection(
                    QuicApplicationErrorCode.Timeout,
                    Message.raw("handoff to ${server.address}").formattedMessage
                )
            }.onFailure { logger.warn("connection drop failed: ${it.message}") }
        }
    }

    private companion object {
        /** Action id for the Discord button; not a server id. */
        const val DISCORD_ACTION = "__discord"
    }

    class PageData {
        var action: String? = null

        companion object {
            val CODEC: BuilderCodec<PageData> = builderCodec(::PageData)
                .property(PageData::action, Codec.STRING)
                .build()
        }
    }
}
