package net.everlands.moshpit.killstreak

import net.everlands.core.framework.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.KillStreakConfig")
data class KillStreakConfig(
    // Keyed by streak count -- the reward fires on the kill that reaches it.
    // {streak} is substituted in message and title text.
    val streaks: Map<Int, KillStreakConfigEntry> = mapOf(
        5 to KillStreakConfigEntry(
            itemId = "RequestAirstrike",
            quantity = 1,
            soundId = "SFX_RequestingAirstrike",
            message = "<c>{streak} kill streak! <7>Airstrike received - check your utility slot.",
            titlePrimaryMessage = "AIRSTRIKE READY",
            titleSecondaryMessage = "{streak} KILL STREAK",
            hudLabel = "Airstrike",
        ),
        10 to KillStreakConfigEntry(
            itemId = "RequestAirstrike",
            quantity = 2,
            soundId = "SFX_Monster_Kill",
            message = "<c>{streak} kill streak! <7>Two more airstrikes received.",
            titlePrimaryMessage = "UNSTOPPABLE",
            titleSecondaryMessage = "{streak} KILL STREAK",
            hudLabel = "Unstoppable",
        ),
    )
) {
    @Component
    class Binder : Config<KillStreakConfig>(KillStreakConfig())
}

@Serializable
data class KillStreakConfigEntry(
    val itemId: String? = null,
    val quantity: Int? = null,
    val soundId: String? = null,
    val soundVolumeMultiplier: Float? = null,
    val soundPitchMultiplier: Float? = null,
    val message: String? = null,
    val titlePrimaryMessage: String? = null,
    val titleSecondaryMessage: String? = null,
    /** Short name shown on the killstreak HUD as "Next: <hudLabel>". */
    val hudLabel: String? = null,
)
