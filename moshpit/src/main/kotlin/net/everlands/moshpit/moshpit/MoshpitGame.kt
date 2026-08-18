package net.everlands.moshpit.moshpit

import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.model.MiniGame
import net.everlands.moshpit.minigame.ffa.FFAMiniGame
import net.everlands.moshpit.minigame.elimination.EliminationMiniGame
import net.everlands.moshpit.minigame.gungame.GunGameMiniGame
import net.everlands.moshpit.minigame.hardpoint.HardpointMiniGame
import net.everlands.moshpit.minigame.oitc.OITCMiniGame
import net.everlands.moshpit.minigame.tdm.TDMMiniGame

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
