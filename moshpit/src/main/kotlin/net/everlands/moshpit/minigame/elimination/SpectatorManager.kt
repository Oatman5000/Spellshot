package net.everlands.moshpit.minigame.elimination

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.modules.entity.component.Spectating
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrNull
import net.everlands.moshpit.ui.applyEverlandsHud
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Real spectating for eliminated players, on 0.6.0's own subsystem.
 *
 * This used to be a chase camera built from the killcam's `cameraLookAt`, because
 * 0.5.7 had nothing else: its `GameMode` enum was Adventure, Creative and Code, with
 * no spectator anywhere. The result looked like spectating but was not -- the player
 * kept a solid body in the world, could still walk around, and remained visible to
 * everyone else. It was a camera trick.
 *
 * 0.6.0 ships the real thing: a [Spectating] component, `SpectatorSystems` to drive
 * it, `HideFromNonSpectators` so the living cannot see you, and `SpectatingHud`.
 * Adding the component is now all that is required; the engine handles the body, the
 * collision, the visibility and the HUD.
 *
 * Note that [com.hypixel.hytale.protocol.GameMode] still has no Spectator constant in
 * 0.6.0 -- it is Adventure and Creative only, having actually dropped Code. Spectating
 * is a component, not a game mode, so nothing here touches `player.gameMode`.
 *
 * Still event-driven rather than polled: [retarget] is called on every death, which
 * is the only moment a spectator's target can become invalid.
 */
object SpectatorManager {

    private val logger = LoggerFactory.getLogger("Spectator")

    /** spectator uuid -> uuid of the player they are currently following. */
    private val watching = ConcurrentHashMap<UUID, UUID>()

    /**
     * Everyone currently spectating, whether or not they have a target.
     *
     * Separate from [watching] on purpose: a spectator whose whole team is down drops
     * to a free camera and has no target, but is still very much spectating. Deriving
     * [isSpectating] from [watching] alone reported those players as alive again.
     */
    private val spectators = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Puts a freshly eliminated player into spectator mode.
     *
     * The engine takes their body out of the world, hides them from the living and
     * swaps in the spectating HUD. The manual HUD hiding this used to do is gone --
     * `SpectatingHud` does it, and hiding the components by hand fought with it.
     */
    fun beginSpectating(spectator: PlayerRef, team: EliminationTeam) {
        spectators.add(spectator.uuid)
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

        // Already following someone who is alive: leave it alone rather than
        // re-attaching to the same target and jolting the view.
        if (current != null && candidates.any { it.playerRef.uuid == current }) return

        val next = candidates.firstOrNull()
        if (next == null) {
            // Whole team is down. Free-camera rather than dropping out of spectating
            // entirely -- their body should stay out of the world until the round ends.
            watching.remove(spectator.uuid)
            onWorldThread(spectator, "release the spectator target") { ref, store ->
                setSpectating(ref, store, Spectating())
            }
            return
        }

        val targetRef = next.playerRef.referenceOrNull ?: run {
            logger.warn("spectator target ${next.playerRef.username} has no entity yet; leaving as-is")
            return
        }
        watching[spectator.uuid] = next.playerRef.uuid
        onWorldThread(spectator, "attach the spectator camera") { ref, store ->
            setSpectating(ref, store, Spectating(targetRef))
        }
    }

    /**
     * Replaces the player's [Spectating] component, whether or not they have one.
     *
     * Neither `addComponent` nor `putComponent` will do this on its own -- both throw
     * "Entity already contains component type" when the component is present, which is
     * what the Spectator log was full of. And it is present more often than you would
     * expect: the engine attaches Spectating itself when a player dies, so by the time
     * Elimination reacts to the death the component is already there.
     *
     * [Spectating] takes its target in the constructor with no setter, so the only way
     * to retarget is to drop it and add a new one.
     */
    private fun setSpectating(ref: Ref<EntityStore>, store: Store<EntityStore>, spectating: Spectating) {
        store.removeComponentIfExists(ref, Spectating.getComponentType())
        store.addComponent(ref, Spectating.getComponentType(), spectating)
    }


    /**
     * Runs an ECS mutation on the world thread.
     *
     * Adding or removing a component off-thread trips `Store.assertThread`. Callers
     * here are a mixed bag -- the kill handler is already on the world thread, the
     * round-end countdown is on a coroutine dispatcher -- so every mutation is
     * marshalled rather than relying on the caller to be in the right place.
     *
     * Note that every attach uses `putComponent`, never `addComponent`. [Spectating]
     * takes its target in the constructor and exposes no setter, so retargeting means
     * replacing the component outright -- and by then the player already has one.
     * The vanilla `/spectate` command checks the archetype and calls `putComponent`
     * for the same reason.
     */
    private inline fun onWorldThread(
        spectator: PlayerRef,
        what: String,
        crossinline action: (Ref<EntityStore>, Store<EntityStore>) -> Unit,
    ) {
        val ref = spectator.referenceOrNull ?: return
        runCatching {
            ref.store.externalData.world.execute {
                runCatching { action(ref, ref.store) }
                    .onFailure { logger.warn("could not $what for ${spectator.username}: ${it.message}") }
            }
        }.onFailure { logger.warn("could not schedule $what for ${spectator.username}: ${it.message}") }
    }

    /**
     * Returns a player to their own body.
     *
     * Removing [Spectating] is what matters -- it is what puts the body back in the
     * world and makes them visible again. A silent failure here strands someone in
     * spectator view for the whole of the next round, so it is logged loudly.
     *
     * The HUD is re-asserted afterwards because `SpectatingHud` swapped it out.
     * Deliberately `applyEverlandsHud` and not `showHudComponents`: turning the
     * vanilla hotbar/health/stamina back on is what made the stock HUD reappear.
     */
    fun stopSpectating(spectator: PlayerRef) {
        watching.remove(spectator.uuid)
        spectators.remove(spectator.uuid)
        onWorldThread(spectator, "leave spectator mode") { ref, store ->
            store.removeComponentIfExists(ref, Spectating.getComponentType())
            val player = spectator.playerOrNull
            if (player != null && player.gameMode != GameMode.Creative) {
                runCatching { applyEverlandsHud(player, spectator) }
                    .onFailure { logger.debug("could not restore HUD for ${spectator.username}: ${it.message}") }
            }
        }
    }

    /** Drops everyone out of spectator mode -- used when the match ends. */
    fun stopAll(session: EliminationMiniGameSession) {
        session.runForEachParticipant { stopSpectating(it.playerRef) }
    }

    fun isSpectating(uuid: UUID): Boolean = spectators.contains(uuid)
}
