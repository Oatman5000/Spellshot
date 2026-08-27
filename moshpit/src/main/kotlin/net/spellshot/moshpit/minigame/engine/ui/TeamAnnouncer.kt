package net.spellshot.moshpit.minigame.engine.ui

import net.spellshot.core.ext.referenceOrNull
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.moshpit.minigame.elimination.EliminationTeam
import net.spellshot.moshpit.minigame.engine.model.MiniGameParticipant
import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam
import net.spellshot.moshpit.minigame.hardpoint.HardpointTeam
import net.spellshot.moshpit.minigame.tdm.TDMTeam
import org.slf4j.LoggerFactory

/**
 * Tells a player which side they are on, on screen, when they join a team mode.
 *
 * The team was previously only discoverable from your armour colour and the HUD
 * scoreboard -- neither of which is obvious in the first seconds of a match, which is
 * exactly when you need to know who to shoot.
 *
 * Only fires for the team modes. FFA, OitC and Gun Game run
 * [net.spellshot.moshpit.minigame.engine.strategy.team.SingleTeamPerPlayerTeamStrategy],
 * where every player is their own single-member team, so "you are on team Oatman5000"
 * would be noise.
 */
object TeamAnnouncer {

    private val logger = LoggerFactory.getLogger(TeamAnnouncer::class.java)

    /**
     * The side's display name for the modes that have real sides, or null.
     *
     * The three team classes each carry their own `def` enum with the same fields and
     * no common supertype, so this matches on the concrete type. Returning null for
     * anything else is what keeps FFA quiet -- it is the check, not an oversight.
     *
     * The defs also carry a hex `color`, deliberately unused here: [MessageParser]
     * STRIPS every `<...>` tag rather than rendering it, and `sendEventTitle` builds a
     * `Message.raw`, so there is no colour to be had on either surface. The colours are
     * written onto the HUD instead, and the team's armour is the in-world signal.
     */
    private fun identity(team: MiniGameTeam): String? = when (team) {
        is TDMTeam -> team.def.displayName
        is HardpointTeam -> team.def.displayName
        is EliminationTeam -> team.def.displayName
        else -> null
    }

    /**
     * Flashes the team banner for [participant].
     *
     * Deferred by one tick via `world.execute`. At the moment a participant is added
     * the client is still being set up -- the HUD is registered immediately after this
     * point -- and a title sent into that window is simply dropped. Every other
     * per-join action in this codebase defers for the same reason.
     */
    fun announce(team: MiniGameTeam, participant: MiniGameParticipant) {
        val name = identity(team) ?: return
        val playerRef = participant.playerRef
        val ref = playerRef.referenceOrNull ?: return

        runCatching {
            ref.store.externalData.world.execute {
                runCatching {
                    playerRef.sendEventTitle(
                        "$name TEAM",
                        "Your side for this match",
                        true,
                        duration = 3.5f,
                        fadeInDuration = 0.3f,
                    )
                    // Chat too, so it is still there after the title fades and after a
                    // mid-match join where the flash is easy to miss.
                    playerRef.sendRawMessage("You are on the $name team.")
                }.onFailure {
                    logger.warn("Could not announce the team to {}: {}", playerRef.username, it.message)
                }
            }
        }.onFailure {
            logger.warn("Could not schedule the team announcement for {}: {}", playerRef.username, it.message)
        }
    }
}
