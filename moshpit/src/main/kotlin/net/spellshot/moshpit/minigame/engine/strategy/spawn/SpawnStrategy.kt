package net.spellshot.moshpit.minigame.engine.strategy.spawn

import com.hypixel.hytale.math.vector.Transform
import net.spellshot.moshpit.minigame.engine.model.MiniGameParticipant
import net.spellshot.moshpit.minigame.engine.model.MiniGameTeam

interface SpawnStrategy {

    fun teleport(team: MiniGameTeam, participant: MiniGameParticipant)

    /**
     * Where to put this player, or null if the arena has no marker that fits.
     *
     * Nullable rather than throwing. Both implementations used to signal "no markers"
     * with an exception -- `NoSuchElementException` from `random()` on an empty list,
     * and an `error()` call -- which escaped into the ECS respawn callback and left
     * the player wherever they died. Callers now skip the teleport and log, which is
     * equally broken for that player but does not take the respawn handler with it.
     */
    fun getSpawn(team: MiniGameTeam, participant: MiniGameParticipant): Transform?

}
