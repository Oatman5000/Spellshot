package net.spellshot.moshpit.minigame.engine.countdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.spellshot.core.Countdown
import net.spellshot.core.framework.coroutineDispatcher
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession

abstract class MiniGameCountdown<S : MiniGameSession>(val session: S, val countdown: Int) :
    Countdown(countdown) {

    // Resolved lazily: the arena's world is not available at construction time.
    override val scope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + session.arena.world.coroutineDispatcher)
    }
}