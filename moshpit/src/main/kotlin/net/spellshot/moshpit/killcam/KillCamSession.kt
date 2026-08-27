package net.spellshot.moshpit.killcam

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.protocol.packets.interface_.HudComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.putNameplate
import net.spellshot.core.ext.removeNameplate
import net.spellshot.core.functions.cameraLookAt
import net.spellshot.core.functions.cameraReset
import net.spellshot.moshpit.ui.HUDComposer
import net.spellshot.moshpit.ui.applySpellshotHud
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

val killCamSessions: Cache<PlayerRef, KillCamSession> = CacheBuilder.newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .build()

data class KillCamSession(
    val player: Player,
    val playerRef: PlayerRef,
    val killerRef: PlayerRef,
    val respawnAt: Instant,
    val page: KillCamHud,
    val hudComposer: HUDComposer
) {

    init {
        killCamSessions.put(playerRef, this)
    }

    val differenceInSeconds: Long
        get() {
            val msLeft = Duration.between(Instant.now(), respawnAt).toMillis()
            val clamped = max(0L, msLeft)
            return (clamped + 999L) / 1000L
        }

    fun stop(ref: Ref<EntityStore>, buffer: CommandBuffer<EntityStore>) {
        buffer.tryRemoveComponent(ref, DeathComponent.getComponentType())
        hudComposer.unregisterHUD(page.player, page.playerRef, page.key)
        buffer.putNameplate(ref, Nameplate(playerRef.username))
        cameraReset(playerRef)
        // Re-assert the Spellshot set rather than switching the vanilla hotbar,
        // health and stamina back on.
        //
        // This used to call showHudComponents(Hotbar, Health, Stamina), which put
        // three components back that applySpellshotHud deliberately drops -- the
        // hotbar is replaced by the weapon selector, stamina is unlimited, and we
        // draw our own health. Since a killcam runs after every player kill, that
        // was the "the default hotbar sometimes comes back" report: whether it
        // stuck depended on whether GunSelectUI's respawn re-apply happened to
        // land after this.
        //
        // Creative is left alone; those players are meant to have the stock UI.
        if (player.gameMode != GameMode.Creative) {
            applySpellshotHud(player, playerRef)
        }
        killCamSessions.invalidate(playerRef)
    }

    companion object {

        fun create(
            ref: Ref<EntityStore>,
            player: Player,
            playerRef: PlayerRef,
            killerRef: PlayerRef,
            respawnDelaySeconds: Int,
            hudComposer: HUDComposer,
            buffer: CommandBuffer<EntityStore>
        ): KillCamSession {
            val respawnAt = Instant.now().plusSeconds(respawnDelaySeconds.toLong())
            cameraLookAt(playerRef, killerRef)
            buffer.removeNameplate(ref)
            val page = KillCamHud(player, playerRef, killerRef.username, hudComposer)
            hudComposer.registerHud(player, playerRef, page)
            page.updateCountdown(respawnDelaySeconds)
            player.hudManager.hideHudComponents(
                playerRef,
                HudComponent.Hotbar,
                HudComponent.Health,
                HudComponent.Stamina
            )
            return KillCamSession(player, playerRef, killerRef, respawnAt, page, hudComposer)
        }
    }
}

