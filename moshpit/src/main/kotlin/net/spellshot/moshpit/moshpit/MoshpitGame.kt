package net.spellshot.moshpit.moshpit

import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.model.MiniGame
import net.spellshot.moshpit.minigame.ffa.FFAMiniGame
import net.spellshot.moshpit.minigame.elimination.EliminationMiniGame
import net.spellshot.moshpit.minigame.gungame.GunGameMiniGame
import net.spellshot.moshpit.minigame.hardpoint.HardpointMiniGame
import net.spellshot.moshpit.minigame.oitc.OITCMiniGame
import net.spellshot.moshpit.minigame.tdm.TDMMiniGame

enum class MoshpitGame(val provider: () -> MiniGame<*>) {

    FFA(provider = { MiniGameEngine.instance.getGameByType<FFAMiniGame>() }),
    HARDPOINT(provider = { MiniGameEngine.instance.getGameByType<HardpointMiniGame>() }),
    TDM(provider = { MiniGameEngine.instance.getGameByType<TDMMiniGame>() }),

    // MoshpitConfig.games is derived from entries, so adding here is enough to
    // put the mode in the rotation and on the vote screen.
    OITC(provider = { MiniGameEngine.instance.getGameByType<OITCMiniGame>() }),
    ELIMINATION(provider = { MiniGameEngine.instance.getGameByType<EliminationMiniGame>() }),
    GUNGAME(provider = { MiniGameEngine.instance.getGameByType<GunGameMiniGame>() });

    inline fun <reified T : MiniGame<*>> getGame(): T {
        return provider.invoke() as T
    }
}
