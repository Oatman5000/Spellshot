package net.everlands.moshpit.minigame.gungame

import net.everlands.moshpit.minigame.engine.countdown.KillRaceCountdown
import net.everlands.moshpit.moshpit.MoshpitGame

/**
 * Round clock for Gun Game.
 *
 * Purely timed: the ladder is a weapon modifier, not a race, so reaching the final gun
 * does not end anything. Highest kill count when the clock hits zero takes the match,
 * same as FFA.
 */
class GunGameCountdown(session: GunGameMiniGameSession, countdown: Int) :
    KillRaceCountdown<GunGameMiniGameSession>(
        session,
        countdown,
        MoshpitGame.GUNGAME,
        (session.game as GunGameMiniGame).progressionManager,
        GunGameMiniGame.STATE_VOTE,
        { it.gunGame },
    )
