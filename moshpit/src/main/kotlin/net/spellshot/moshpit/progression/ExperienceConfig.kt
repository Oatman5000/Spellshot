package net.spellshot.moshpit.progression

import net.spellshot.core.framework.Config
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.ExperienceConfig")
data class ExperienceConfig(
    @Description("The amount of XP awarded for each kill")
    val xpPerKill: Int = 200,
    val hardpoint: ExperienceHardpointConfig = ExperienceHardpointConfig(),
    val tdm: ExperienceTDMConfig = ExperienceTDMConfig(),
    val ffa: ExperienceFFAConfig = ExperienceFFAConfig(),
    // Same podium shape as FFA -- OitC is also a timed free-for-all ranked by
    // kills -- so it reuses the type rather than duplicating it.
    val oitc: ExperienceFFAConfig = ExperienceFFAConfig(),
    val elimination: ExperienceEliminationConfig = ExperienceEliminationConfig(),
    // Timed free-for-all ranked by kills, same podium shape as FFA.
    val gunGame: ExperienceFFAConfig = ExperienceFFAConfig(),
) {
    @Component
    class Binder : Config<ExperienceConfig>(ExperienceConfig())
}

@Serializable
data class ExperienceHardpointConfig(
    @Description("The amount of XP awarded for each second you capture an objective")
    val objectiveXpPerSecond: Int = 50,
    @Description("The amount of XP for winning a hardpoint match")
    val teamWin: Int = 1000,
)

@Serializable
data class ExperienceTDMConfig(
    @Description("The amount of XP for winning a tdm match")
    val teamWin: Int = 3000,
)

@Serializable
data class ExperienceEliminationConfig(
    @Description("The amount of XP for winning an elimination match. Higher than TDM: there are no second chances, so a win is worth more.")
    val teamWin: Int = 4000,
)

@Serializable
data class ExperienceFFAConfig(
    @Description("The amount of XP awarded for rank in FFA matches")
    val xpPerRank: Map<Int, Int> = mapOf(
        1 to 1500,
        2 to 1000,
        3 to 750,
    ),
)
