package net.spellshot.moshpit.minigame.tdm

import net.spellshot.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.TDMConfig")
data class TDMConfig(
    val maxPoints: Int = 100,
) {

    @Component
    class Binder : Config<TDMConfig>(TDMConfig())
}
