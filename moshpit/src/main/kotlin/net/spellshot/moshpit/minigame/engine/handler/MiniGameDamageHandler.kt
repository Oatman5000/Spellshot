package net.spellshot.moshpit.minigame.engine.handler

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.SystemGroup
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.moshpit.minigame.elimination.EliminationMiniGameSession
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.oitc.OITCMiniGameSession
import net.spellshot.moshpit.minigame.oitc.OITCRules
import net.spellshot.moshpit.minigame.engine.KillWeapon
import net.spellshot.moshpit.minigame.engine.KillWeapons

class MiniGameDamageHandler(val engine: MiniGameEngine) : DamageEventSystem() {

    override fun getQuery(): Query<EntityStore?> {
        return Player.getComponentType()
    }

    override fun getGroup(): SystemGroup<EntityStore> {
        return DamageModule.get().filterDamageGroup
    }

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        dmg: Damage
    ) {
        val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val game = engine.getGameByPlayerUUID(playerRef.uuid) ?: return
        val session = game.getSessionByUUID(playerRef.uuid) ?: return
        val stateConfig = session.currentStateConfig
        dmg.isCancelled = !stateConfig.pvp

        // Elimination: a spectator is a ghost. Their body is still standing in
        // the world -- a player entity cannot be despawned without disconnecting
        // them -- so without this someone could shoot a dead player, and a dead
        // player could punch the living.
        if (session is EliminationMiniGameSession) {
            if (session.isEliminated(playerRef.uuid)) {
                dmg.isCancelled = true
                return
            }
            val attacker = dmg.source
            if (attacker is Damage.EntitySource) {
                val attackerRef = attacker.ref.store.getComponent(attacker.ref, PlayerRef.getComponentType())
                if (attackerRef != null && session.isEliminated(attackerRef.uuid)) {
                    dmg.isCancelled = true
                    return
                }
            }
        }

        val source = dmg.source
        if (source is Damage.EntitySource) {
            val damager = source.ref.store.getComponent(source.ref, PlayerRef.getComponentType())
            if (damager == null) {
                engine.plugin.logger.atWarning().log("Damager is null")
                return
            }
            // You are trivially "in the same team" as yourself, so this used to cancel
            // all self-damage as friendly fire -- your own grenade, your own airstrike.
            // Excluding self means blast radius applies to the thrower like anyone else.
            if (damager.uuid != playerRef.uuid && session.isInSameTeam(playerRef.uuid, damager.uuid)) {
                dmg.isCancelled = !stateConfig.friendlyFire
            }

            // Melee, recorded centrally because nothing else can: the knife runs the
            // stock DamageEntity interaction, which is engine code we do not touch.
            // Firearms and explosives stamp the "Gun" cause and mark themselves at the
            // point of damage, so anything left over from a player is a melee hit.
            //
            // Marked on every hit, not just lethal ones -- the most recent damage is
            // what killed you, so a shot followed by a knife reads as a knife kill.
            if (!dmg.isCancelled && dmg.damageCauseIndex != OITCRules.gunDamageCauseIndex) {
                KillWeapons.mark(playerRef.uuid, KillWeapon.KNIFE)
            }
        }

        // One in the Chamber: a bullet that connects always kills.
        //
        // Applied here rather than by editing the gun's HeadDamage/ChestDamage,
        // because those are shared item assets -- changing them would make the
        // revolver a one-shot in every other mode too. Gated on the damage cause
        // so only gunfire is lethal; the knife keeps its normal 25 and stays the
        // several-swings punishment for missing.
        if (!dmg.isCancelled &&
            session is OITCMiniGameSession &&
            dmg.damageCauseIndex == OITCRules.gunDamageCauseIndex
        ) {
            dmg.amount = OITCRules.LETHAL_DAMAGE
        }
    }


}
