package net.everlands.moshpit.minigame.engine.powerup.ecs

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.ComponentDescriptor
import net.everlands.moshpit.minigame.engine.powerup.PowerUpType

class PowerUpComponent(
    var gameSessionId: String? = null,
    var arenaId: String? = null,
    var powerUpKey: String? = null,
    var powerUpType: PowerUpType? = null,
    var anchorY: Double? = null,
) : Component<EntityStore> {

    override fun clone() = PowerUpComponent(
        gameSessionId = gameSessionId,
        arenaId = arenaId,
        powerUpKey = powerUpKey,
        powerUpType = powerUpType,
        anchorY = anchorY,
    )

    companion object : ComponentDescriptor<PowerUpComponent>(
        PowerUpComponent::class.java,
        ::PowerUpComponent,
        {
            BuilderCodec.builder(PowerUpComponent::class.java, ::PowerUpComponent)
                .append(
                    KeyedCodec("GameSessionId", Codec.STRING),
                    { comp, id -> comp.gameSessionId = id },
                    { comp -> comp.gameSessionId }
                ).add()
                .append(
                    KeyedCodec("ArenaId", Codec.STRING),
                    { comp, value -> comp.arenaId = value },
                    { comp -> comp.arenaId }
                ).add()
                .append(
                    KeyedCodec("PowerUpKey", Codec.STRING),
                    { comp, key -> comp.powerUpKey = key },
                    { comp -> comp.powerUpKey }
                ).add()
                .append(
                    KeyedCodec("PowerUpType", Codec.STRING),
                    { comp, type -> comp.powerUpType = PowerUpType.valueOf(type) },
                    { comp -> comp.powerUpType?.name }
                ).add()
                .append(
                    KeyedCodec("AnchorY", Codec.DOUBLE),
                    { comp, anchorY -> comp.anchorY = anchorY },
                    { comp -> comp.anchorY }
                ).add()
        }
    )
}

