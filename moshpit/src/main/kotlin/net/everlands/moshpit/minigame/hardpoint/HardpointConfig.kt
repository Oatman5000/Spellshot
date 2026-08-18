package net.everlands.moshpit.minigame.hardpoint

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.HardpointConfig")
data class HardpointConfig(
    val pointSwitchInterval: Int = 30,
    val pointRange: Double = 5.0,
    val pointsPerSecond: Int = 10,
    val maxPoints: Int = 2000,
) {

    @Component
    class Binder : Config<HardpointConfig>(HardpointConfig())
}
