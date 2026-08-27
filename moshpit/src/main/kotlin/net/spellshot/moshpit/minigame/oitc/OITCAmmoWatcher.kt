package net.spellshot.moshpit.minigame.oitc

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.playerRefOrNull
import net.spellshot.core.framework.EcsService
import net.spellshot.moshpit.minigame.engine.MiniGameEngine

/**
 * Drives [OITCMiniGameSession.reconcileAmmo] -- the single owner of the gun's
 * ammo. It both pays out bullets earned by kills and confiscates the gun once
 * the chamber is empty.
 *
 * Confiscation is what kills the reload loop.
 *
 * This exists to kill the reload loop. Revolver.json routes its Primary through
 * a StatsCondition costing 1 GunAmmo, with `"Failed": "Revolver_Reload"` -- so
 * with an empty chamber every single click starts a reload. Revolver_Reload is a
 * `Simple` interaction carrying a 2 second animation, and the actual refill is
 * only its `Next` step. [OITCRules.blocksReload] stops the refill but runs far
 * too late to stop the animation, so holding the trigger on an empty revolver
 * replayed the reload forever.
 *
 * Removing the item is what actually fixes it: with no gun in the slot there is
 * nothing to reload, the routing never fires, and Revolver_Reload's own
 * `"CancelOnItemChange": true` tears down any animation already in flight.
 *
 * It also happens to be exactly the rule the mode was asked for -- miss your
 * shot and you are on the knife until you earn another bullet.
 */
@EcsService
class OITCAmmoWatcher : DelayedEntitySystem<EntityStore>(0.1f) {

    override fun getQuery(): Query<EntityStore> = Player.getComponentType()

    override fun tick(
        delta: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>
    ) {
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        // Creative runs the stock interface and is not part of a match.
        if (player.gameMode == GameMode.Creative) return
        val playerRef = player.playerRefOrNull ?: return

        val session = runCatching {
            val game = MiniGameEngine.instance.getGameByPlayerUUID(playerRef.uuid) as? OITCMiniGame ?: return
            game.getSessionByUUID(playerRef.uuid) as? OITCMiniGameSession
        }.getOrNull() ?: return

        session.reconcileAmmo(player, playerRef)
    }
}
