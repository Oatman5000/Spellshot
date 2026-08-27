package net.spellshot.moshpit.minigame.engine.strategy.team

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.moshpit.minigame.engine.model.MiniGameParticipant
import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam

interface TeamStrategy {

    fun distributePlayer(playerRef: PlayerRef): MiniGameTeam

    fun leaveTeam(playerRef: PlayerRef, team: MiniGameTeam): MiniGameParticipant
}
