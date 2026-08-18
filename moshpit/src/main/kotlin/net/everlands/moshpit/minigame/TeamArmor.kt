package net.everlands.moshpit.minigame

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.PlayerRef
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("everlands.armour")

/**
 * Equips the team-coloured armour set. Shared by TDM and Hardpoint, which had
 * identical copies of this.
 *
 * The armour container enforces per-slot filters, so addItemStack can silently
 * decline a piece rather than throwing. The log line reports what actually
 * landed in the container, which is the only way to tell an equip failure from
 * a rendering failure from outside the game.
 */
fun equipTeamArmor(player: Player, playerRef: PlayerRef, armorPrefix: String) {
    val armor = player.inventory.armor
    if (armor == null) {
        log.warn("No armour container for {}", playerRef.username)
        return
    }
    armor.clear()

    // Hands are deliberately omitted. The gauntlet renders over the first-person
    // arm and left players with no visible hand -- it only showed up once the
    // armour actually started decoding, since before that nothing equipped at all.
    // Head, chest and legs carry the team colour perfectly well from third person,
    // and gauntlets are the least visible piece on another player anyway.
    val ids = arrayOf(
        "${armorPrefix}_Armor_Copper_Head",
        "${armorPrefix}_Armor_Copper_Chest",
        "${armorPrefix}_Armor_Copper_Legs"
    )
    for (id in ids) {
        try {
            armor.addItemStack(ItemStack(id))
        } catch (e: Exception) {
            log.warn("Could not add {} for {}: {}", id, playerRef.username, e.message)
        }
    }

    val capacity = armor.capacity.toInt()
    val contents = (0 until capacity).joinToString(", ") { slot ->
        runCatching { armor.getItemStack(slot.toShort())?.itemId }.getOrNull() ?: "-"
    }
    log.info(
        "team armour {} for {}: capacity={} contents=[{}]",
        armorPrefix, playerRef.username, capacity, contents
    )
}
