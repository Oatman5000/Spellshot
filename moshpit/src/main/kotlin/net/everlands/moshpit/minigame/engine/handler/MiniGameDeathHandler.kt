package net.everlands.moshpit.minigame.engine.handler

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.heal
import net.everlands.core.ext.play2DSound
import net.everlands.moshpit.minigame.engine.MiniGameDeathEvent
import net.everlands.moshpit.minigame.engine.MiniGameEngine
import net.everlands.moshpit.minigame.engine.MiniGameKillEvent
import net.everlands.moshpit.killcam.KillCamSession
import net.everlands.moshpit.minigame.engine.model.StatisticalMiniGameParticipant
import org.slf4j.LoggerFactory

class MiniGameDeathHandler(val engine: MiniGameEngine) : DeathSystems.OnDeathSystem() {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val deps = setOf(
        SystemDependency(Order.BEFORE, DeathSystems.PlayerDeathScreen::class.java)
    )

    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }

    override fun getDependencies(): Set<Dependency<EntityStore?>?> {
        return deps
    }

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        comp: DeathComponent,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val playerRef = store.getComponent(ref, PlayerRef.getComponentType()) ?: error("Player not found")
        val game = engine.getGameByPlayerUUID(playerRef.uuid) ?: return
        val session = game.getSessionByUUID(playerRef.uuid) ?: return
        val pair = session.getTeamAndParticipantByUUID(playerRef.uuid) ?: return
        comp.isShowDeathMenu = false
        comp.itemsLossMode = DeathConfig.ItemsLossMode.NONE
        val participant = pair.second
        if (participant is StatisticalMiniGameParticipant) {
            participant.deaths += 1
        }
        playerRef.play2DSound(MiniGameEngine.DEATH_SOUND)
        try {
            engine.eventPublisher.publishEvent(
                MiniGameDeathEvent(
                    session,
                    pair.first,
                    pair.second,
                )
            )
        } catch (e: Exception) {
            logger.warn("Error while processing death events for ${playerRef.username}", e)
        }
        val source = comp.deathInfo?.source
        var removeDeathComponent = true
        if (comp.deathInfo != null && source is Damage.EntitySource) {
            val killerRef = source.ref
            if (killerRef.isValid) {
                val killer = store.getComponent(killerRef, PlayerRef.getComponentType())
                if (killer != null) {
                    val killerPair = session.getTeamAndParticipantByUUID(killer.uuid)
                    if (killerPair != null) {
                        if (killerPair.second is StatisticalMiniGameParticipant) {
                            val statsKiller = killerPair.second as StatisticalMiniGameParticipant
                            statsKiller.kills += 1
                        }
                        if (game.healKiller) {
                            val killerPlayer =
                                store.getComponent(killerRef, Player.getComponentType()) ?: return
                            killerPlayer.heal()
                        }
                        killerPair.second.playerRef.play2DSound(MiniGameEngine.KILL_SOUND)
                        val player = store.getComponent(ref, Player.getComponentType())
                        if (player != null) {
                            try {
                                KillCamSession.create(ref, player, playerRef, killer, 3, engine.hudComposer, buffer)
                                logger.info("Successfully started killcam for ${playerRef.username} killed by ${killer.username}")
                                removeDeathComponent = false
                            } catch (ex: Exception) {
                                logger.error(
                                    "Error starting killcam for ${playerRef.username} killed by ${killer.username}",
                                    ex
                                )
                            }
                        } else {
                            logger.warn("Error while processing death events for ${playerRef.username}: Player component not found")
                        }
                        try {
                            engine.eventPublisher.publishEvent(
                                MiniGameKillEvent(
                                    session,
                                    pair.first,
                                    pair.second,
                                    killerPair.first,
                                    killerPair.second,
                                )
                            )
                        } catch (e: Exception) {
                            logger.warn("Error while processing kill events for ${killer.username}", e)
                        }
                    }
                }
            }
        }
        if (removeDeathComponent) {
            buffer.tryRemoveComponent(ref, DeathComponent.getComponentType())
            logger.info("Removed death event for ${playerRef.username}")
        }
    }
}
