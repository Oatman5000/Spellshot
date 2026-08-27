package net.everlands.moshpit.minigame.hardpoint

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.HardpointConfig")
data class HardpointConfig(
    /**
     * Seconds a hardpoint stays live before it moves.
     *
     * Was 30, which is short enough that a team could be pushed off, rotate, and find
     * the point had already moved before they arrived -- the fight never really
     * settled anywhere. 60 is the Call of Duty Hardpoint rotation and leaves room for
     * a contest, a wipe and a retake on the same point.
     *
     * It does not change match length: scoring is [pointsPerSecond] toward
     * [maxPoints] and is unaffected by how often the point moves.
     */
    val pointSwitchInterval: Int = 60,
    val pointRange: Double = 5.0,
    val pointsPerSecond: Int = 10,
    val maxPoints: Int = 2000,
) {

    @Component
    class Binder : Config<HardpointConfig>(HardpointConfig())
}
