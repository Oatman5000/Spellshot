package net.everlands.moshpit.minigame.oitc

import net.everlands.moshpit.minigame.engine.countdown.KillRaceCountdown
import net.everlands.moshpit.moshpit.MoshpitGame

/**
 * Round clock for One in the Chamber.
 *
 * Timed rather than first-to-N: the round always ends when the clock does and the
 * highest kill count wins, which keeps OitC interchangeable with the other modes in
 * the rotation. Podium XP reuses the FFA rank table.
 */
class OITCCountdown(session: OITCMiniGameSession, countdown: Int) :
    KillRaceCountdown<OITCMiniGameSession>(
        session,
        countdown,
        MoshpitGame.OITC,
        (session.game as OITCMiniGame).progressionManager,
        OITCMiniGame.STATE_VOTE,
        { it.oitc },
    )
