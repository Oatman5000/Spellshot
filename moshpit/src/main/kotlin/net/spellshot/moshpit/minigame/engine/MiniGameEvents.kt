package net.spellshot.moshpit.minigame.engine

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.moshpit.minigame.engine.model.MiniGameParticipant
import net.spellshot.moshpit.minigame.engine.model.MiniGameSession
import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam

interface MiniGameEvent {
    val session: MiniGameSession
}

data class MiniGameStopSessionEvent(
    override val session: MiniGameSession,
) : MiniGameEvent

data class MiniGameKillEvent(
    override val session: MiniGameSession,
    val entityTeam: MiniGameTeam,
    val entityParticipant: MiniGameParticipant,
    val killerTeam: MiniGameTeam,
    val killerParticipant: MiniGameParticipant,
) : MiniGameEvent

data class MiniGameKillCamFinishEvent(
    override val session: MiniGameSession,
    val buffer: CommandBuffer<EntityStore>,
    val entityTeam: MiniGameTeam,
    val entityParticipant: MiniGameParticipant,
    val killerTeam: MiniGameTeam,
    val killerParticipant: MiniGameParticipant,
) : MiniGameEvent

data class MiniGameDeathEvent(
    override val session: MiniGameSession,
    val team: MiniGameTeam,
    val participant: MiniGameParticipant,
) : MiniGameEvent

data class MiniGameRespawnEvent(
    override val session: MiniGameSession,
    val team: MiniGameTeam,
    val participant: MiniGameParticipant,
    val buffer: CommandBuffer<EntityStore>,
) : MiniGameEvent

data class MiniGameAddParticipantEvent(
    override val session: MiniGameSession,
    val team: MiniGameTeam,
    val participant: MiniGameParticipant,
) : MiniGameEvent
