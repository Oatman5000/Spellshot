package net.everlands.moshpit.minigame.engine.strategy.spawn

import com.hypixel.hytale.math.vector.Transform
import net.everlands.moshpit.minigame.engine.model.MiniGameParticipant
import net.everlands.moshpit.minigame.engine.model.MiniGameTeam

interface SpawnStrategy {

    fun teleport(team: MiniGameTeam, participant: MiniGameParticipant)

    fun getSpawn(team: MiniGameTeam, participant: MiniGameParticipant): Transform

}
