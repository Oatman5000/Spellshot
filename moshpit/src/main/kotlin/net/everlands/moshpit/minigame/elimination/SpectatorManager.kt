package net.everlands.moshpit.minigame.elimination

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.protocol.packets.interface_.HudComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.core.ext.playerOrNull
import net.everlands.core.functions.cameraLookAt
import net.everlands.core.functions.cameraReset
import net.everlands.moshpit.ui.applyEverlandsHud
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Follow-cam spectating for eliminated players.
 *
 * Hytale 0.5.7 has no Spectator game mode -- [com.hypixel.hytale.protocol.GameMode]
 * is only Adventure, Creative and Code -- so this is built from the same
 * `cameraLookAt` primitive the killcam runs on: a third-person chase camera
 * attached to another player's entity.
 *
 * Deliberately event-driven rather than polled. [retarget] is called whenever
 * somebody dies, which is the only moment a spectator's view can become invalid,
 * and the camera packet is re-sent only when the target actually changes. A tick
 * system would re-send the packet constantly and make the view stutter.
 */
object SpectatorManager {

    private val logger = LoggerFactory.getLogger("Spectator")

    /** spectator uuid -> uuid of the player their camera is currently attached to. */
    private val watching = ConcurrentHashMap<UUID, UUID>()

    /**
     * Puts a freshly eliminated player into spectator mode.
     *
     * Their own body stays in the world -- there is no way to despawn a player
     * entity without disconnecting them -- so the nameplate goes and the damage
     * filter ignores them. They can still walk around, but with the camera
     * attached elsewhere and an empty inventory there is nothing they can do
     * with that.
     */
    fun beginSpectating(spectator: PlayerRef, team: EliminationTeam) {
        val player = spectator.playerOrNull
        if (player != null) {
            runCatching {
                player.hudManager.hideHudComponents(
                    spectator,
                    HudComponent.Hotbar,
                    HudComponent.Health,
                    HudComponent.Stamina
                )
            }.onFailure { logger.warn("could not hide HUD for spectator ${spectator.username}: ${it.message}") }
        }
        retargetOne(spectator, team)
    }

    /**
     * Re-points every spectator on [team] at a living team-mate.
     *
     * Call after any death: the person a spectator was watching may have just
     * been eliminated themselves, and a camera attached to a dead player shows
     * nothing useful.
     */
    fun retarget(team: EliminationTeam) {
        team.runForEachParticipant { participant ->
            val p = participant as? EliminationParticipant ?: return@runForEachParticipant
            if (!p.eliminated) return@runForEachParticipant
            retargetOne(p.playerRef, team)
        }
    }

    private fun retargetOne(spectator: PlayerRef, team: EliminationTeam) {
        val current = watching[spectator.uuid]
        val candidates = team.participants.values
            .mapNotNull { it as? EliminationParticipant }
            .filter { !it.eliminated && it.playerRef.uuid != spectator.uuid }

        // Still watching someone who is alive: leave the camera alone rather than
        // re-sending the same attachment and jolting the view.
        if (current != null && candidates.any { it.playerRef.uuid == current }) return

        val next = candidates.firstOrNull()
        if (next == null) {
            // Whole team is down. Nothing to follow -- hand the camera back so
            // they are not staring at a corpse until the round ends.
            watching.remove(spectator.uuid)
            runCatching { cameraReset(spectator) }
                // A failed reset leaves the player welded to a dead team-mate's view
                // with no way out but reconnecting, so it must not pass unnoticed.
                .onFailure { logger.warn("could not release spectator camera for ${spectator.username}: ${it.message}") }
            return
        }
        watching[spectator.uuid] = next.playerRef.uuid
        runCatching { cameraLookAt(spectator, next.playerRef) }
            .onFailure { logger.warn("could not attach spectator camera for ${spectator.username}: ${it.message}") }
    }

    /**
     * Returns a player to their own body and restores the stock HUD strip.
     *
     * Every ECS touch is guarded. This is reachable from the countdown's
     * onFinish, which runs on a coroutine dispatcher rather than the world
     * thread, and reading a component from off-thread trips Store.assertThread.
     * Releasing the camera is the part that matters and it is only a packet
     * write, so it happens first and unconditionally.
     */
    fun stopSpectating(spectator: PlayerRef) {
        watching.remove(spectator.uuid)
        runCatching { cameraReset(spectator) }
            // Same again: this is the round-end path, so a silent failure here strands
            // the player in spectator view for the whole of the next round.
            .onFailure { logger.warn("could not release spectator camera for ${spectator.username}: ${it.message}") }
        runCatching {
            val player = spectator.playerOrNull ?: return@runCatching
            // Re-assert the Everlands set, NOT showHudComponents. Switching the
            // vanilla hotbar/health/stamina back on is what made the stock HUD
            // reappear -- applyEverlandsHud drops all three on purpose.
            if (player.gameMode != GameMode.Creative) {
                applyEverlandsHud(player, spectator)
            }
        }.onFailure { logger.debug("could not restore HUD for ${spectator.username}: ${it.message}") }
    }

    /** Drops everyone out of spectator mode -- used when the match ends. */
    fun stopAll(session: EliminationMiniGameSession) {
        session.runForEachParticipant { stopSpectating(it.playerRef) }
    }

    fun isSpectating(uuid: UUID): Boolean = watching.containsKey(uuid)
}
