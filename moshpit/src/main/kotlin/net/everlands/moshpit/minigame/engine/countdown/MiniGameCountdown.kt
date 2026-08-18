package net.everlands.moshpit.minigame.engine.countdown

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.everlands.core.Countdown
import net.everlands.core.framework.coroutineDispatcher
import net.everlands.moshpit.minigame.engine.model.MiniGameSession

abstract class MiniGameCountdown<S : MiniGameSession>(val session: S, val countdown: Int) :
    Countdown(countdown) {

    // Resolved lazily: the arena's world is not available at construction time.
    override val scope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + session.arena.world.coroutineDispatcher)
    }
}