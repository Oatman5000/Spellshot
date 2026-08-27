package net.spellshot.moshpit.minigame.ffa.countdown

import net.spellshot.moshpit.minigame.engine.countdown.KillRaceCountdown
import net.spellshot.moshpit.minigame.ffa.FFAMiniGame
import net.spellshot.moshpit.minigame.ffa.FFAMiniGameSession
import net.spellshot.moshpit.moshpit.MoshpitGame

/** Round clock for free-for-all. See [KillRaceCountdown] for the shared behaviour. */
class FFACountdown(session: FFAMiniGameSession, countdown: Int) :
    KillRaceCountdown<FFAMiniGameSession>(
        session,
        countdown,
        MoshpitGame.FFA,
        (session.game as FFAMiniGame).progressionManager,
        FFAMiniGame.STATE_VOTE,
        { it.ffa },
    )
