package net.spellshot.moshpit.loadout.ui


import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.SpellshotUser
import net.spellshot.core.framework.context
import net.spellshot.core.ext.builderCodec
import net.spellshot.core.ext.property
import net.spellshot.core.framework.launchGlobal
import net.spellshot.core.functions.broadcastFormat
import net.spellshot.core.ext.play2DSound
import net.spellshot.core.ext.sendEventTitle
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.moshpit.loadout.*
import org.slf4j.LoggerFactory

class LoadoutPage(val player: Player, val ref: PlayerRef, val user: SpellshotUser, val loadoutService: LoadoutService) :
    InteractiveCustomUIPage<LoadoutPage.PageData>(
        ref, CustomPageLifetime.CanDismiss,
        PageData.CODEC
    ) {

    val stats = StatsContainer(this)

    var currentLoadoutIndex = 0
    val currentLoadout: Loadout?
        get() =
            loadoutService.getLoadout(user, currentLoadoutIndex)

    override fun build(
        ref: Ref<EntityStore>,
        builder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        logger.info("Building loadout page for ${playerRef.username}")
        builder.append("Pages/Menu/Menu.ui")
        stats.updateStats(user, builder)
        updateLoadoutBoxes(builder, eventBuilder)

        builder.set("#Edit.Visible", false)
        builder.set("#ItemSelection.Visible", false)
        builder.set("#ItemSkins.Visible", false)


        if (loadoutService.progressionManager.canPrestigeLevelUp(user)) {
            builder.set("#PrestigeLevelUpContainer.Visible", true)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#PrestigeLevelUpContainer #Button",
                EventData.of("Action", "prestige"), false
            )
        }
        for (type in LoadoutItemType.entries) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#Edit #${type.displayName} #Button",
                EventData.of("Action", "select:${type.name}"), false
            )
        }
    }

    fun updateLoadoutBoxes(
        providedBuilder: UICommandBuilder? = null, providedEventBuilder: UIEventBuilder? = null,
        loadouts: PlayerLoadouts? = null
    ) {
        val builder = providedBuilder ?: UICommandBuilder()
        val eventBuilder = providedEventBuilder ?: UIEventBuilder()
        builder.clear("#Loadouts")
        val playerLoadouts = loadouts ?: loadoutService.getPlayerLoadouts(user) ?: return
        for (i in 0 until 3) {
            val loadout = when (i == 0 || user.hasPermission("loadout.${i + 1}")) {
                true -> loadoutService.getOrCreateLoadout(user, i, playerLoadouts)
                else -> null
            }
            if (loadout == null) {
                builder.append("#Loadouts", "Pages/Menu/LoadoutEntryLocked.ui")
            } else {
                builder.append("#Loadouts", "Pages/Menu/LoadoutEntry.ui")
                if (playerLoadouts.selected == i) {
                    // Cyan-filled variant of the hextech frame, plus an
                    // "EQUIPPED" badge. Previously this pointed at the old
                    // pre-hextech highlight texture, so the selected entry
                    // looked out of place rather than selected.
                    builder.set(
                        "#Loadouts[${i}] #Button.Style.Default.Background",
                        "Images/Hextech/SlotSelected.png"
                    )
                    builder.set("#Loadouts[${i}] #Button.Style.Default.Background.Border", 24)
                    builder.set("#Loadouts[${i}] #SelectedBadge.Visible", true)
                    builder.set("#Loadouts[${i}] #Title.Style.TextColor", "#00D4FF")
                }
                builder.set("#Loadouts[${i}] #Title.Text", "Loadout #${i + 1}")
                if (loadout.primary == null) {
                    builder.set("#Loadouts[${i}] #Primary.Background", "Images/Empty.png")
                } else {
                    val path =
                        loadoutService.getItemByAllowance(user, playerRef, loadout.primary, { it.primaryGun })?.let {
                            "Images/Guns/${it}.png"
                        } ?: "Images/Empty.png"
                    builder.set("#Loadouts[${i}] #Primary.Background", path)
                }
                if (loadout.secondary == null) {
                    builder.set("#Loadouts[${i}] #Secondary.Background", "Images/Empty.png")
                } else {
                    val path =
                        loadoutService.getItemByAllowance(user, playerRef, loadout.secondary, { it.secondaryGun })
                            ?.let {
                                "Images/Guns/${it}.png"
                            } ?: "Images/Empty.png"
                    builder.set("#Loadouts[${i}] #Secondary.Background", path)
                }
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.RightClicking, "#Loadouts[${i}] #Button",
                    EventData.of("Action", "edit:$i"), false
                )
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating, "#Loadouts[${i}] #Button",
                    EventData.of("Action", "loadout:$i"), false
                )
            }
        }

        if (providedBuilder == null && providedEventBuilder == null) {
            sendUpdate(builder, eventBuilder, false)
        }
    }

    fun update(builder: UICommandBuilder) {
        sendUpdate(builder)
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: PageData) {
        val action = data.action ?: return
        if (action.startsWith("loadout:")) {
            val index = action.removePrefix("loadout:").toIntOrNull() ?: return
            close()
            playerRef.play2DSound("SFX_Select")
            loadoutService.selectLoadout(user, index)
            // The selection takes effect on the next respawn -- deliberately.
            //
            // This used to call respawnWithLoadout to apply it immediately, but
            // that re-runs the spawn strategy, which teleports the player to a
            // random spawn point in the middle of a live match. giveLoadout
            // already reads the selected loadout on every respawn, so simply
            // recording the choice is enough.
            playerRef.sendRawMessage("<c>Loadout ${index + 1} selected.<7> It will be applied when you next spawn.")
            return
        }
        if (action.startsWith("prestige") && loadoutService.progressionManager.canPrestigeLevelUp(user)) {
            close()
            launchGlobal {
                user.runLockGuarded {
                    val prestige = loadoutService.progressionManager.addPrestige(user)
                    store.externalData.world.context {
                        playerRef.play2DSound("SFX_Prestige_Level_Up")
                        playerRef.sendEventTitle("PRESTIGE $prestige", "YOU HAVE REACHED")
                        broadcastFormat("\n<cb>${playerRef.username}<r> has reached prestige <ab>$prestige<r>!\n")
                    }
                }
            }
            return
        }
        if (action.startsWith("edit:")) {
            playerRef.play2DSound("SFX_Click")
            val index = action.removePrefix("edit:").toIntOrNull() ?: return
            val user = playerRef.userOrNull ?: return
            val loadout = loadoutService.getLoadout(user, index) ?: return
            currentLoadoutIndex = index
            val builder = UICommandBuilder()
            updateEditLoadout(loadout, builder)
            updateSkinsContainer(false, builder)
            sendUpdate(builder)
            return
        }
        if (action.startsWith("select:")) {
            val selectionType = action.removePrefix("select:").let {
                LoadoutItemType.valueOf(it.uppercase())
            }
            val items = loadoutService.getAvailableItems(user, selectionType)
            updateItemSelection(selectionType.titleText, items, selectionType)
            return
        }
        if (action.startsWith("item:")) {
            val parts = action.removePrefix("item:").split(":")
            if (parts.size != 2) return
            val type = LoadoutItemType.valueOf(parts[0].uppercase())
            val itemId = parts[1]
            if (loadoutService.progressionManager.isSkin(itemId)) {
                selectItem(type, itemId)
                return
            }
            if (loadoutService.isItemLocked(user, itemId)) {
                return
            }
            val skins = loadoutService.getSkins(user, playerRef, itemId)
            if (skins.isEmpty()) {
                selectItem(type, itemId)
                updateSkinsContainer(false)
                return
            }
            updateSkins(type, itemId, skins)
            return
        }
        if (action.startsWith("skin:")) {
            val parts = action.removePrefix("skin:").split(":")
            if (parts.size != 2) return
            val type = LoadoutItemType.valueOf(parts[0].uppercase())
            val skinId = parts[1]
            selectItem(type, skinId)
            updateSkinsContainer(false)
            return
        }
    }

    private fun updateSkinsContainer(value: Boolean, providedBuilder: UICommandBuilder? = null) {
        val builder = providedBuilder ?: UICommandBuilder()
        builder.set("#ItemSkins.Visible", value)
        if (providedBuilder == null) {
            sendUpdate(builder)
        }
    }

    private fun updateSkins(
        type: LoadoutItemType, itemId: String,
        skins: List<LoadoutPreviewSkin>,
        providedBuilder: UICommandBuilder? = null,
        providedEventBuilder: UIEventBuilder? = null
    ) {
        val builder = providedBuilder ?: UICommandBuilder()
        val eventBuilder = providedEventBuilder ?: UIEventBuilder()
        builder.clear("#ItemSkins #Skins")
        updateSkinsContainer(true, builder)
        if (skins.isEmpty()) return
        for ((index, skin) in skins.withIndex()) {
            builder.append("#ItemSkins #Skins", "Pages/Menu/LoadoutItemSelectionEntry.ui")
            builder.set("#ItemSkins #Skins[$index] #Name.Text", skin.displayName)
            for (loadoutItemType in LoadoutItemType.entries) {
                builder.set(
                    "#ItemSkins #Skins[$index] #${loadoutItemType.displayName}Icon.Visible",
                    loadoutItemType == type
                )
                if (loadoutItemType == type) {
                    builder.set(
                        "#ItemSkins #Skins[$index] #${type.displayName}Icon.Background",
                        type.imagePath + skin.id + ".png"
                    )
                }
            }
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#ItemSkins #Skins[$index] #Button",
                EventData.of("Action", "skin:${type.name}:${skin.id}"), false
            )
        }
        if (providedBuilder == null && providedEventBuilder == null) {
            sendUpdate(builder, eventBuilder, false)
        }

    }

    private fun selectItem(type: LoadoutItemType, itemId: String) {
        val loadout = currentLoadout ?: return
        val nextLoadout = when (type) {
            LoadoutItemType.PRIMARY -> loadout.copy(primary = itemId)
            LoadoutItemType.SECONDARY -> loadout.copy(secondary = itemId)
            LoadoutItemType.MELEE -> loadout.copy(melee = itemId)
            LoadoutItemType.UTILITY -> loadout.copy(utility = itemId)
        }
        // Commit before rendering: an async save here used to be overwritten by
        // a stale write queued during a page rebuild.
        val savedLoadouts = loadoutService.saveLoadoutAt(user, currentLoadoutIndex, nextLoadout) ?: return
        val builder = UICommandBuilder()
        val eventBuilder = UIEventBuilder()
        updateLoadoutBoxes(builder, eventBuilder, loadouts = savedLoadouts)
        updateEditLoadout(nextLoadout, builder)
        sendUpdate(builder, eventBuilder, false)
    }

    private fun updateEditLoadout(loadout: Loadout, providedBuilder: UICommandBuilder? = null) {
        val builder = providedBuilder ?: UICommandBuilder()

        val primaryPath = loadout.primary?.let {
            "Images/Guns/${loadoutService.getItemByAllowance(user, playerRef, it, { sel -> sel.primaryGun })}.png"
        } ?: "Images/Empty.png"

        val secondaryPath = loadout.secondary?.let {
            "Images/Guns/${loadoutService.getItemByAllowance(user, playerRef, it, { sel -> sel.secondaryGun })}.png"
        } ?: "Images/Empty.png"

        val meleePath = loadout.melee?.let {
            "Images/Melee/${loadoutService.getItemByAllowance(user, playerRef, it, { sel -> sel.melee })}.png"
        } ?: "Images/Empty.png"

        val utilityPath = loadout.utility?.let {
            "Images/Utility/${loadoutService.getItemByAllowance(user, playerRef, it, { sel -> sel.utility })}.png"
        } ?: "Images/Empty.png"

        builder.set("#Edit.Visible", true)
        builder.set("#Edit #EditLabel.Text", "EDITING: LOADOUT #${currentLoadoutIndex + 1}")
        builder.set("#Edit #Primary #Icon.Background", primaryPath)
        builder.set("#Edit #Secondary #Icon.Background", secondaryPath)
        builder.set("#Edit #Melee #Icon.Background", meleePath)
        builder.set("#Edit #Utility #Icon.Background", utilityPath)

        builder.clear("#ItemSelection #Items")
        builder.clear("#ItemSkins #Skins")
        builder.set("#ItemSelection.Visible", false)
        builder.set("#ItemSkins.Visible", false)

        if (providedBuilder == null) {
            sendUpdate(builder)
        }
    }

    private fun updateItemSelection(title: String, items: List<LoadoutPreviewItem>, type: LoadoutItemType) {
        val builder = UICommandBuilder()
        builder.set("#ItemSelection.Visible", true)
        builder.set("#ItemSelection #Title.Text", title)
        builder.clear("#ItemSelection #Items")
        updateSkinsContainer(false, builder)
        val eventBuilder = UIEventBuilder()
        for ((index, item) in items.withIndex()) {
            val lockedDocumentSuffix = if (item.isLocked) "Locked" else ""
            builder.append("#ItemSelection #Items", "Pages/Menu/LoadoutItemSelectionEntry$lockedDocumentSuffix.ui")
            builder.set("#ItemSelection #Items[$index] #Name.Text", item.displayName)
            if (item.isLocked) {
                builder.set("#ItemSelection #Items[$index] #Requirement.Text", "LVL ${item.requiredLevel}")
            }
            for (loadoutItemType in LoadoutItemType.entries) {
                builder.set(
                    "#ItemSelection #Items[$index] #${loadoutItemType.displayName}Icon.Visible",
                    loadoutItemType == type
                )
                if (loadoutItemType == type) {
                    builder.set(
                        "#ItemSelection #Items[$index] #${loadoutItemType.displayName}Icon.Background",
                        type.imagePath + item.id + ".png"
                    )
                }
            }
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#ItemSelection #Items[$index] #Button",
                EventData.of("Action", "item:${type.name}:${item.id}"), false
            )
        }
        sendUpdate(builder, eventBuilder, false)
    }

    class PageData {
        var action: String? = null

        companion object {
            val CODEC: BuilderCodec<PageData> = builderCodec(::PageData)
                .property(PageData::action, Codec.STRING)
                .build()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoadoutPage::class.java)
    }
}

