package net.spellshot.moshpit.minigame.engine.model

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.arena.MiniGameArena
import net.spellshot.moshpit.minigame.engine.countdown.MiniGameCountdown
import java.util.*

abstract class MiniGame<S : MiniGameSession>(val engine: MiniGameEngine) {

    val sessions: MutableList<S> = mutableListOf()

    var minPlayers = 2

    open val healKiller = true
    open val powerUps: Boolean = false

    abstract val states: Map<Int, MiniGameStateConfig>

    abstract fun createSession(arena: MiniGameArena): S

    fun isPlaying(uuid: UUID) = sessions.any { session ->
        session.teams.any { team ->
            team.participants.containsKey(uuid)
        }
    }

    fun getSessionByUUID(uuid: UUID) = sessions.find { session ->
        session.teams.any { team ->
            team.participants.containsKey(uuid)
        }
    }

    fun getSessionByPlayer(ref: PlayerRef) = getSessionByUUID(ref.uuid)
}

data class MiniGameStateConfig(
    var pvp: Boolean = false,
    var friendlyFire: Boolean = false,
    var countdownSupplier: (session: MiniGameSession) -> MiniGameCountdown<*>? = { null },
)

