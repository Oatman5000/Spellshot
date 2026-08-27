package net.spellshot.moshpit.loadout

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.i18n.I18nModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import kotlinx.serialization.json.Json
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.SpellshotUser
import net.spellshot.core.ext.referenceOrNull
import net.spellshot.moshpit.item.ammo.AmmoProvider
import net.spellshot.moshpit.progression.ProgressionManager
import net.spellshot.moshpit.progression.level
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LoadoutService(
    val starterLoadout: StarterLoadoutConfig.Binder, val ammoProvider: AmmoProvider,
    val progressionManager: ProgressionManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getAvailableItems(user: SpellshotUser, type: LoadoutItemType): List<LoadoutPreviewItem> {
        val config = progressionManager.config.value
        val items = mutableListOf<LoadoutPreviewItem>()
        val currentLevel = user.level
        config.items[type]?.forEach { itemProgression ->
            val name = getItemNameById(itemProgression.itemId)
            if (name == null) {
                logger.warn("Item name not found for id: ${itemProgression.itemId}")
                return@forEach
            }
            val isLocked = currentLevel < itemProgression.requiredLevel
            items.add(LoadoutPreviewItem(itemProgression.itemId, name, isLocked, itemProgression.requiredLevel))
        }
        return items
    }

    fun getSkins(user: SpellshotUser, playerRef: PlayerRef, itemId: String): List<LoadoutPreviewSkin> {
        val skins = progressionManager.getSkins(user, playerRef, itemId).map { skin ->
            LoadoutPreviewSkin(skin.itemId, skin.name)
        }
        if (skins.isEmpty()) return skins
        return buildList {
            val default = LoadoutPreviewSkin(itemId, "Default")
            add(default)
            addAll(skins)
        }
    }


    fun isItemLocked(user: SpellshotUser, itemId: String): Boolean {
        if (progressionManager.isSkin(itemId)) return false
        val config = progressionManager.config.value
        val itemProgression = config.items.values.flatten().firstOrNull { it.itemId == itemId } ?: return true
        val userLevel = user.level
        return userLevel < itemProgression.requiredLevel
    }

    /**
     * Display name for the loadout menu.
     *
     * This used to be `Message.translation(key).ansiMessage`, which rendered the
     * raw key ("spellshot.items.AR14.name") for every Spellshot weapon while
     * vanilla names resolved fine. The reason is visible in the boot log: the
     * base game's strings arrive as "bundled default translations", which is the
     * table getAnsiMessage() resolves against, but a mod's .lang file is only
     * ever registered against a locale ("Loaded 14 entries for 'en-US' from
     * ...Spellshot.Game"). Our keys were never in the table being searched.
     *
     * Asking the locale table directly is the fix. The final fallback returns the
     * item id so a missing entry shows "Remington870" rather than a translation
     * key -- ugly, but readable.
     */
    private fun getItemNameById(itemId: String): String? {
        val item = Item.getAssetMap().getAsset(itemId) ?: return null
        val key = item.translationKey ?: return itemId

        // getMessages returns a raw Map, so the value comes back as Any? -- cast
        // explicitly rather than relying on inference.
        val fromLocale = runCatching {
            I18nModule.get().getMessages(DEFAULT_LOCALE)?.get(key) as? String
        }.getOrNull()
        if (!fromLocale.isNullOrBlank()) return fromLocale

        val fromDefaults = runCatching { Message.translation(key).ansiMessage }.getOrNull()
        // An unresolved lookup hands back the key itself, which always contains
        // dots; a real display name will not.
        if (!fromDefaults.isNullOrBlank() && fromDefaults != key && !fromDefaults.contains('.')) {
            return fromDefaults
        }

        logger.warn("No translation for '$key' (item $itemId), falling back to the item id")
        return itemId
    }

    fun giveLoadout(player: Player, playerRef: PlayerRef) {
        val user = playerRef.userOrNull
        if (user == null) {
            logger.warn("user ${playerRef.username} not found")
            return
        }
        // Resolve everything BEFORE touching the inventory.
        //
        // This used to clear first and resolve after, so anything that threw in
        // between -- a saved loadout that decodes to an empty list, an item id
        // that no longer exists -- wiped the hotbar and left it empty, with no
        // error logged anywhere. That is the "items sometimes disappear" case.
        val loadout = runCatching { getSelectedLoadout(user) }.getOrElse {
            logger.warn("Could not read loadout for ${playerRef.username}, using default: ${it.message}")
            createDefaultLoadout()
        }
        val items = buildList {
            loadout.primary?.let { id -> getItemByAllowance(user, playerRef, id) { c -> c.primaryGun } }?.let(::add)
            loadout.secondary?.let { id -> getItemByAllowance(user, playerRef, id) { c -> c.secondaryGun } }?.let(::add)
            loadout.melee?.let { id -> getItemByAllowance(user, playerRef, id) { c -> c.melee } }?.let(::add)
            loadout.utility?.let { id -> getItemByAllowance(user, playerRef, id) { c -> c.utility } }?.let(::add)
        }

        player.inventory.clear()
        // Resolved once, with a message. `hotbar!!` gave a bare NullPointerException
        // per item, from inside a runCatching that only reported "null".
        val hotbar = player.inventory.hotbar
        if (hotbar == null) {
            logger.error("No hotbar container for ${playerRef.username}; loadout not given")
            return
        }
        for (id in items) {
            runCatching {
                hotbar.addItemStack(interceptGunItemStack(ItemStack(id)))
            }.onFailure {
                // One bad item must not cost the player the rest of their kit.
                logger.warn("Could not give $id to ${playerRef.username}: ${it.message}")
            }
        }
        giveStarterLoadout(player)
        val ref = player.referenceOrNull ?: return
        val heldItem = player.inventory.itemInHand ?: return
        val maxAmmo = ammoProvider.getMaxAmmo(heldItem) ?: return
        ammoProvider.setClientAmmo(ref, maxAmmo)
    }

    fun getItemByAllowance(
        user: SpellshotUser, playerRef: PlayerRef, itemId: String,
        selector: (loadout: StarterLoadoutConfig) -> String?
    ): String? {
        if (!isAllowedToUseItem(user, playerRef, itemId)) {
            return selector(starterLoadout.value)
        }
        return itemId
    }

    private fun isAllowedToUseItem(user: SpellshotUser, playerRef: PlayerRef, itemId: String): Boolean {
        if (isItemLocked(user, itemId)) return false
        val skinParentId = progressionManager.getSkinParentItemId(user, itemId)
        if (skinParentId != null) {
            if (isItemLocked(user, skinParentId)) {
                return false
            }
        }
        return true
    }

    /**
     * Stored as a JSON string rather than the live object.
     *
     * UserStore only writes primitives to disk -- it deliberately skips object
     * graphs, since they reference runtime state. Keeping a PlayerLoadouts
     * instance in customData therefore meant loadouts survived a relog but were
     * lost on every restart. Encoding here makes them a String, which persists
     * for free alongside xp, level and prestige.
     */
    fun saveLoadouts(user: SpellshotUser, playerLoadouts: PlayerLoadouts) {
        val encoded = runCatching { loadoutJson.encodeToString(PlayerLoadouts.serializer(), playerLoadouts) }
            .getOrElse {
                logger.warn("Could not encode loadouts for ${user.username}: ${it.message}")
                return
            }
        user.setCustomData("loadouts", encoded)
    }

    /**
     * Atomic read-modify-write of the stored loadouts, under the user's lock.
     *
     * Every mutation must go through here. The previous code snapshotted the
     * list, mutated a copy, then persisted it from a fire-and-forget coroutine
     * -- so a late write from a page rebuild could overwrite a selection the
     * player had already made.
     */
    private fun <T> mutateLoadouts(
        user: SpellshotUser, block: (PlayerLoadouts) -> Pair<PlayerLoadouts, T>
    ): T = user.runLockGuarded {
        val current = getPlayerLoadouts(user)
            ?: PlayerLoadouts(selected = 0, loadouts = mutableListOf(createDefaultLoadout()))
        val (updated, result) = block(current)
        saveLoadouts(user, updated)
        result
    }

    /** Grows the list as needed so [index] is always addressable. */
    private fun grownTo(current: PlayerLoadouts, index: Int): MutableList<Loadout> {
        val updated = current.loadouts.toMutableList()
        while (updated.size <= index) updated.add(createDefaultLoadout())
        return updated
    }

    fun saveLoadoutAt(user: SpellshotUser, index: Int, loadout: Loadout): PlayerLoadouts? {
        if (index < 0) return null
        return mutateLoadouts(user) { current ->
            val updated = grownTo(current, index)
            updated[index] = loadout
            val next = current.copy(loadouts = updated)
            next to next
        }
    }

    private fun createDefaultLoadout(): Loadout {
        val config = starterLoadout.value
        return Loadout(
            primary = config.primaryGun,
            secondary = config.secondaryGun,
            melee = config.melee,
            utility = config.utility,
            oitc = config.oitcGun
        )
    }

    fun getOrCreateLoadout(user: SpellshotUser, index: Int, playerLoadouts: PlayerLoadouts): Loadout {
        if (index < 0) return createDefaultLoadout()
        if (index < playerLoadouts.loadouts.size) {
            return getLoadout(user, index, playerLoadouts) ?: error("Loadout not found")
        }
        // Re-read inside the lock rather than persisting the caller's snapshot.
        return mutateLoadouts(user) { current ->
            val updated = grownTo(current, index)
            current.copy(loadouts = updated) to updated[index]
        }
    }

    fun getSelectedLoadout(user: SpellshotUser): Loadout {
        // firstOrNull, not first: PlayerLoadouts defaults to an empty list, so a
        // saved value that decodes without any loadouts threw NoSuchElementException
        // here -- after giveLoadout had already cleared the inventory.
        getPlayerLoadouts(user)
            ?: return mutateLoadouts(user) { current ->
                current to (current.loadouts.firstOrNull() ?: createDefaultLoadout())
            }
        return mutateLoadouts(user) { current ->
            current to (current.loadouts.getOrNull(current.selected) ?: createDefaultLoadout())
        }
    }

    fun selectLoadout(user: SpellshotUser, index: Int) {
        mutateLoadouts(user) { current -> current.copy(selected = index) to Unit }
        // Drives the "open your inventory" hint: once they have picked once, the
        // prompt has done its job. Stored in customData so UserStore persists it
        // and the hint does not reappear on the next restart.
        user.setCustomData(LOADOUT_CHOSEN, 1)
    }

    companion object {
        /** Lenient so a saved loadout written by an older build still decodes. */
        private val loadoutJson = Json { ignoreUnknownKeys = true }

        /** customData key set when a player has selected a loadout at least once. */
        const val LOADOUT_CHOSEN = "loadoutchosen"

        /**
         * Locale the loadout menu resolves item names in.
         *
         * The mod only ships en-US (Server/Languages/en-US/spellshot.lang); add
         * more locale folders and this can become per-player.
         */
        const val DEFAULT_LOCALE = "en-US"

        fun hasChosenLoadout(user: SpellshotUser): Boolean =
            (user.customData.getInt(LOADOUT_CHOSEN) ?: 0) > 0
    }

    fun getLoadout(user: SpellshotUser, index: Int, loadouts: PlayerLoadouts? = null): Loadout? {
        val playerLoadouts = loadouts ?: getPlayerLoadouts(user) ?: return null
        return playerLoadouts.loadouts.getOrNull(index)
    }

    fun getPlayerLoadouts(user: SpellshotUser): PlayerLoadouts? {
        return when (val raw = user.customData["loadouts"]) {
            is String -> runCatching { loadoutJson.decodeFromString(PlayerLoadouts.serializer(), raw) }
                .getOrElse {
                    // Corrupt or from an incompatible older format: fall back to
                    // defaults rather than wedging the player out of the game.
                    logger.warn("Could not decode loadouts for ${user.username}: ${it.message}")
                    null
                }
            // Tolerated so a session that started before this change still works.
            is PlayerLoadouts -> raw
            else -> null
        }
    }

    private fun giveStarterLoadout(player: Player) {
        val loadout = starterLoadout.value
        // Each container resolved once and skipped if absent, rather than three !!
        // that would each throw a bare NullPointerException mid-kit.
        loadout.armor.forEach { (id, qty) ->
            if (qty > 0) player.inventory.armor?.addItemStack(interceptGunItemStack(ItemStack(id, qty)))
        }
        loadout.hotbar.forEach { (id, qty) ->
            if (qty > 0) player.inventory.hotbar?.addItemStack(interceptGunItemStack(ItemStack(id, qty)))
        }
        loadout.storage.forEach { (id, qty) ->
            if (qty > 0) player.inventory.storage?.addItemStack(interceptGunItemStack(ItemStack(id, qty)))
        }
    }

    private fun interceptGunItemStack(itemStack: ItemStack): ItemStack {
        val maxAmmo = ammoProvider.getMaxAmmo(itemStack)
        if (maxAmmo != null) {
            return ammoProvider.buildItemStackWithAmmo(itemStack, maxAmmo)
        }
        return itemStack
    }
}

data class LoadoutPreviewItem(
    val id: String,
    val displayName: String,
    val isLocked: Boolean,
    val requiredLevel: Int,
    val requiredPrestige: Int = 0,
)

data class LoadoutPreviewSkin(
    val id: String,
    val displayName: String,
)


