package net.spellshot.core.api

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.ext.sendRawMessage
import org.slf4j.LoggerFactory

/**
 * Sends players back to the lobby server.
 *
 * Backs `/leave` and its aliases (`/l`, `/hub`, `/lobby`, `/exit`).
 *
 * This was stubbed out during the standalone port with a note to "restore the
 * referral logic if a network is ever reintroduced". Hytale supports it
 * natively: `PlayerRef.referToServer(host, port)` sends a referral packet naming
 * the target and the client opens a new connection to it -- no proxy and no
 * coordinator. See "Multiserver Architecture" in the Hytale Server Manual.
 *
 * [target] is null until something sets it, in which case leaving just tells the
 * player there is nowhere to go. That keeps a single-instance setup working.
 */
object SpellshotLeave {

    private val logger = LoggerFactory.getLogger("SpellshotLeave")

    data class Target(val host: String, val port: Int) {
        val address: String get() = if (port == 25565) host else "$host:$port"
    }

    /** Where `/lobby` sends people. Set at startup; null means standalone. */
    @Volatile
    @JvmStatic
    var target: Target? = null

    fun leave(playerRef: PlayerRef) {
        val destination = target
        if (destination == null) {
            logger.info("leave requested by {} - no lobby configured", playerRef.username)
            playerRef.sendRawMessage("<7>This server is standalone - there is nowhere to return to.")
            return
        }
        playerRef.sendRawMessage("<7>Returning you to the lobby...")
        runCatching { playerRef.referToServer(destination.host, destination.port) }
            .onFailure {
                logger.warn("referToServer(${destination.address}) failed for ${playerRef.username}: ${it.message}")
                playerRef.sendRawMessage("<7>Could not send you back. Connect to <c>${destination.address}<7> directly.")
            }
    }

    fun leaveAll(players: Collection<PlayerRef>) = players.forEach { leave(it) }

    // There used to be a `leaveAll(vararg players: PlayerRef)` overload here as well.
    // It is deleted rather than kept, because `leaveAll()` with no arguments compiled
    // happily, resolved to it, and silently evacuated nobody -- which is exactly what
    // the server-restart path had been doing. The Collection overload cannot be called
    // by accident with nothing in it.
}
