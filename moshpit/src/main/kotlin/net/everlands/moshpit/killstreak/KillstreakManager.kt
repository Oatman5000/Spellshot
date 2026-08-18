package net.everlands.moshpit.killstreak

import net.everlands.core.ext.playerRefOrNull

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.util.EventTitleUtil
import net.everlands.core.ext.sendMessage
import net.everlands.core.ext.play2DSound
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrNull
import net.everlands.moshpit.item.airstrike.AirstrikeKills
import net.everlands.moshpit.minigame.engine.MiniGameDeathEvent
import net.everlands.moshpit.minigame.engine.MiniGameKillEvent
import net.everlands.moshpit.minigame.engine.MiniGameStopSessionEvent
import net.everlands.moshpit.minigame.oitc.OITCRules
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class KillstreakManager(val plugin: JavaPlugin, val killStreakConfig: KillStreakConfig.Binder) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val killStreak: LoadingCache<UUID, KillStreakEntry> = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<UUID, KillStreakEntry>(CacheLoader.from { _ -> KillStreakEntry() })

    @EventListener
    fun onKillEvent(event: MiniGameKillEvent) {
        killStreak.get(event.entityParticipant.uuid).amount = 0
        // Airstrike kills do not build toward the next airstrike, otherwise one
        // reward feeds the next and a single streak can chain indefinitely. The
        // victim's streak is still broken above.
        if (AirstrikeKills.consume(event.entityParticipant.uuid)) return
        // No killstreak rewards in One in the Chamber. The reward for a kill
        // there is a bullet, and an airstrike would hand out free kills that
        // bank more bullets -- a runaway loop, and it breaks the one-gun rule.
        if (OITCRules.isPlaying(event.killerParticipant.uuid)) return
        killStreak.get(event.killerParticipant.uuid).amount++
        reactToKillStreak(event.killerParticipant.playerRef)
    }

    /**
     * Any death ends the streak.
     *
     * onKillEvent only clears the victim when another player got the kill, so on
     * its own it would miss falling, the airstrike, self-damage and /kill. This
     * covers every death, which is the rule we want.
     */
    @EventListener
    fun onDeathEvent(event: MiniGameDeathEvent) {
        killStreak.get(event.participant.uuid).amount = 0
    }

    @EventListener
    fun onStopSession(event: MiniGameStopSessionEvent) {
        killStreak.invalidateAll()
    }

    /**
     * Current streak for a player, for the HUD to read.
     *
     * Uses getIfPresent so polling the HUD never populates the cache for players
     * who have not killed anyone yet.
     */
    fun currentStreak(uuid: UUID): Int = killStreak.getIfPresent(uuid)?.amount ?: 0

    /** The next streak reward at or above [streak], or null once past the last one. */
    fun nextMilestone(streak: Int): Int? =
        killStreakConfig.value.streaks.keys.filter { it > streak }.minOrNull()

    /** Highest configured streak reward; the HUD's progress bar tops out here. */
    fun topMilestone(): Int = killStreakConfig.value.streaks.keys.maxOrNull() ?: 0

    fun labelFor(milestone: Int): String? =
        killStreakConfig.value.streaks[milestone]?.hudLabel

    fun reactToKillStreak(playerRef: PlayerRef): KillStreakEntry {
        val entry = killStreak.get(playerRef.uuid)
        val streakConfig = killStreakConfig.value.streaks[entry.amount] ?: return entry
        val player = playerRef.playerOrNull ?: return entry
        val itemId = streakConfig.itemId
        val quantity = streakConfig.quantity
        if (itemId != null && quantity != null) {
            player.inventory.hotbar!!.addItemStack(ItemStack(itemId, quantity))
        }
        logger.info("Awarded killstreak reward ${streakConfig.itemId} x${streakConfig.quantity} to ${player.playerRefOrNull?.username} for ${entry.amount} kills")
        val streakMessage = streakConfig.message
        if (streakMessage != null) {
            player.world?.sendMessage(
                streakMessage.processMessage(player, entry.amount)
            )
        }
        val soundId = streakConfig.soundId
        if (soundId != null) {
            playerRef.play2DSound(
                soundId,
                streakConfig.soundVolumeMultiplier ?: 1f,
                streakConfig.soundPitchMultiplier ?: 1f
            )
        }
        val titlePrimaryMessage = streakConfig.titlePrimaryMessage
        val titleSecondaryMessage = streakConfig.titleSecondaryMessage
        if (titlePrimaryMessage != null && titleSecondaryMessage != null) {
            val reference = player.referenceOrNull ?: return entry
            EventTitleUtil.showEventTitleToWorld(
                Message.raw(titlePrimaryMessage.processMessage(player, entry.amount)),
                Message.raw(titleSecondaryMessage.processMessage(player, entry.amount)),
                true, null, 4f,
                1f, 1f, reference.store
            )
        }
        return entry
    }

    private fun String.processMessage(player: Player, amount: Int): String {
        return this.replace("{player}", player.playerRefOrNull?.username ?: "unknown")
            .replace("{streak}", amount.toString())
    }

    data class KillStreakEntry(
        var amount: Int = 0,
    )
}
