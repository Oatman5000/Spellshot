package net.everlands.lobby

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.framework.Config
import org.springframework.stereotype.Component

/**
 * Lobby behaviour and the list of servers shown in the selector.
 *
 * [LobbyServerEntry.host] / [LobbyServerEntry.port] are shown to the player so
 * they can connect from their own server list. Nothing here can move a client
 * between servers -- see the note on [LobbyPlugin].
 */
@Serializable
@SerialName("lobby.LobbyConfig")
data class LobbyConfig(
    @Description("Item placed in the first hotbar slot; opens the server selector. Null to omit.")
    val navigatorItemId: String? = "Navigator",

    @Description("Second hotbar item, opens the leaderboard. Null to omit.")
    val leaderboardItemId: String? = "Leaderboard",

    @Description("Seconds between leaderboard refreshes from Redis.")
    val leaderboardRefreshSeconds: Long = 60,

    @Description(
        "Game modes given their own kills tab, in order. Must match the " +
            "MoshpitGame enum names on the game server -- the lobby jar does not " +
            "depend on moshpit, so it cannot read them directly."
    )
    val leaderboardModes: List<String> = listOf(
        "FFA", "TDM", "HARDPOINT", "OITC", "ELIMINATION", "GUNGAME"
    ),

    @Description("Greeting sent once the player has spawned in. Blank sends nothing.")
    val welcomeMessage: String = "",

    @Description("Shown above the server list.")
    val selectorTitle: String = "Select Server",

    @Description(
        "Sent to chat when the Discord button is clicked. Nothing can open a " +
            "browser from here -- the client exposes no link-opening action to " +
            "server-driven UI -- so the invite is printed for the player to copy. " +
            "Blank hides the button's message. Prefer EVERLANDS_DISCORD_INVITE."
    )
    val discordInvite: String = "",

    @Description("Servers offered in the selector.")
    val servers: List<LobbyServerEntry> = listOf(
        LobbyServerEntry(
            id = "moshpit",
            name = "PVP",
            description = "FFA  ·  TDM  ·  Hardpoint  ·  OitC  ·  Elimination  ·  Gun Game",
            // Blank on purpose -- see LobbyServerEntry.effectiveHost. This used to
            // name a specific public server, so anyone cloning the repo ran a lobby
            // that referred their players onto somebody else's instance.
            host = "",
            // Moshpit moved to 5524; the lobby itself now sits on 5523.
            port = 5524,
            icon = "Images/Skull.png"
        )
    ),

    @Description(
        "What happens when a player picks a server. Every mode depends on this " +
            "instance's FallbackServer pointing at the target -- nothing can name " +
            "an address to the client. See HandoffMode."
    )
    val handoffMode: HandoffMode = HandoffMode.Refer,
) {
    fun serverById(id: String): LobbyServerEntry? = servers.firstOrNull { it.id == id }

    /** Blank unless configured. See [discordInvite]. */
    val effectiveDiscordInvite: String
        get() = System.getenv("EVERLANDS_DISCORD_INVITE")?.takeIf { it.isNotBlank() } ?: discordInvite

    @Component
    class Binder : Config<LobbyConfig>(LobbyConfig())
}

/**
 * How the lobby moves a player to another instance.
 *
 * Hytale DOES support this natively -- see "Multiserver Architecture" in the
 * Hytale Server Manual. `PlayerRef.referToServer(host, port)` sends a referral
 * packet naming the target, and the client opens a new connection to it. No
 * proxy, no BungeeCord equivalent, no coordinator.
 */
@Serializable
enum class HandoffMode {
    /**
     * Native player referral -- the real handoff. This is the one to use.
     *
     * `PlayerRef.referToServer(host, port)`. An optional 4KB payload can ride
     * along for session context; if that is ever used, sign it (HMAC), because
     * the manual warns it passes through the client and can be tampered with.
     */
    Refer,

    /** Print the address and let the player use Direct Connect. The fallback plan. */
    Message,

    /**
     * Send a clean ServerDisconnect.
     *
     * Kept only as a diagnostic. Tested: the player lands at the main menu.
     * Disconnect Fallback is documented as "Coming Soon" and is not implemented
     * in this build, so nothing picks them up.
     */
    Disconnect,

    /**
     * Hard-close the QUIC connection with a Timeout error code, sending no
     * disconnect packet at all.
     *
     * Kept only as a diagnostic. Tested: the client shows "Connection timed out"
     * and offers Reconnect, which returns to THIS server -- because Disconnect
     * Fallback is not implemented yet. Use [Refer] instead.
     */
    DropConnection,
}

@Serializable
data class LobbyServerEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val host: String,
    val port: Int = 25565,
    @Description(
        "Card icon, as a texture path from the PACK ROOT (Common/UI/Custom) -- " +
            "no leading '../'. Runtime Background values resolve from the pack " +
            "root even though .ui files declare theirs relative to their own " +
            "folder; a '../' prefix here renders a red X. Not an item id: an " +
            "ItemIcon can only draw items that exist on THIS server, and the " +
            "weapons live in moshpit's asset pack."
    )
    val icon: String = "Images/Skull.png",
) {
    /**
     * The host actually used, after the environment override.
     *
     *   EVERLANDS_GAME_HOST   overrides host
     *   EVERLANDS_GAME_PORT   overrides port
     *
     * These apply to every entry, which is fine while the selector lists one
     * server. Give a second entry a literal host if that ever changes.
     */
    val effectiveHost: String get() = env("EVERLANDS_GAME_HOST") ?: host
    val effectivePort: Int get() = env("EVERLANDS_GAME_PORT")?.toIntOrNull() ?: port

    /** False when no host is configured, so the selector can say so. */
    val isConfigured: Boolean get() = effectiveHost.isNotBlank()

    val address: String get() = if (effectivePort == 25565) effectiveHost else "$effectiveHost:$effectivePort"

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
}
