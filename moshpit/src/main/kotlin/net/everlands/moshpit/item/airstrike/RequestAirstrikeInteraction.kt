package net.everlands.moshpit.item.airstrike

import org.joml.Vector3d

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.protocol.WaitForDataFrom
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import net.everlands.core.ext.builderCodec
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.ext.property
import net.everlands.core.ext.transformOrNull
import net.everlands.core.ext.referenceOrNull
import net.everlands.core.ext.sendRawMessage
import net.everlands.moshpit.item.InteractionRegistry
import net.everlands.moshpit.minigame.engine.model.MiniGameSession
import org.slf4j.LoggerFactory

class RequestAirstrikeInteraction(
    var yOffset: Double = 50.0,
    var delay: Long = 3000,
    var duration: Int = 30,
    var amountPerSecond: Int = 5,
    var damage: Double = 220.0,
) : SimpleInstantInteraction() {

    override fun getWaitForDataFrom() = WaitForDataFrom.None

    override fun firstRun(type: InteractionType, context: InteractionContext, cooldownHandler: CooldownHandler) {
        try {
            val playerRef = context.entity.playerRefOrNull ?: run {
                logger.warn("Airstrike used by a non-player entity; ignoring")
                return
            }
            val transform = context.entity.transformOrNull ?: run {
                logger.warn("Airstrike: no transform on {}", playerRef.uuid)
                return
            }
            val buffer = context.commandBuffer ?: run {
                logger.warn("Airstrike: no command buffer in this interaction context")
                return
            }

            val engine = InteractionRegistry.instance.engine
            // The arena, and therefore every marker the strike needs, comes from the
            // caller's session. Outside a match -- spectating, or flying around in
            // creative to test -- there is nothing to read, so the item does nothing.
            // That used to be a bare `?: return` with no output at all, which is
            // indistinguishable from the item being broken.
            val session = engine.getSessionByPlayerUUID<MiniGameSession>(playerRef.uuid) ?: run {
                logger.warn(
                    "Airstrike: {} is not in a minigame session, so there is no arena to " +
                        "strike. The item only works inside a match.",
                    playerRef.uuid
                )
                runCatching {
                    playerRef.sendRawMessage("Airstrike only works during a match - it needs an arena to target.")
                }
                return
            }
            val arena = session.arena

            val planeStart = arena.locations["plane"]
            if (planeStart == null) {
                logger.warn("Could not find plane start location for airstrike for arena ${arena.id}")
                return
            }
            val airstrikePos1 = arena.locations["airstrike:pos1"]
            if (airstrikePos1 == null) {
                logger.warn("Could not find airstrike pos1 location for airstrike for arena ${arena.id}")
                return
            }
            val airstrikePos2 = arena.locations["airstrike:pos2"]
            if (airstrikePos2 == null) {
                logger.warn("Could not find airstrike pos2 location for airstrike for arena ${arena.id}")
                return
            }
            PlaneSpawner.spawn(context.entity, transform.position, planeStart.position, buffer, planeStart.direction)
            val spawner = MissileSpawner(
                playerRef = playerRef,
                initialPosition = Vector3d(transform.position),
                offsetY = yOffset,
                delayInMillis = delay,
                duration = duration,
                amountPerSecond = amountPerSecond,
                pos1 = airstrikePos1.position,
                pos2 = airstrikePos2.position,
            )
            spawner.start()

            val soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Enemy_Airstrike")
            for (player in Universe.get().players) {
                val ref = player.referenceOrNull ?: continue
                if (context.entity == ref) continue
                SoundUtil.playSoundEvent2d(ref, soundIndex, SoundCategory.SFX, buffer)
            }
        } catch (e: Exception) {
            logger.error("Error while requesting airstrike", e)
        }

    }


    companion object {
        private val logger = LoggerFactory.getLogger(RequestAirstrikeInteraction::class.java)
        val CODEC = builderCodec(::RequestAirstrikeInteraction)
            .property(RequestAirstrikeInteraction::yOffset, Codec.DOUBLE)
            .property(RequestAirstrikeInteraction::duration, Codec.INTEGER)
            .property(RequestAirstrikeInteraction::amountPerSecond, Codec.INTEGER)
            .property(RequestAirstrikeInteraction::damage, Codec.DOUBLE)
            .property(RequestAirstrikeInteraction::delay, Codec.LONG)
            .build()
    }
}
