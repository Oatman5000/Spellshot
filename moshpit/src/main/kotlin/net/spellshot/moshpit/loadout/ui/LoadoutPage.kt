package net.spellshot.moshpit.loadout.ui


import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.connection.PongType
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
import net.spellshot.moshpit.minigame.engine.MiniGameEngine
import net.spellshot.moshpit.minigame.engine.model.StatisticalMiniGameParticipant
import net.spellshot.moshpit.minigame.engine.ui.TeamAnnouncer
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

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

        // The edit panel starts empty and is appended by updateEditLoadout when a
        // loadout is clicked. Nothing here hides it.
        //
        // It used to be part of Menu.ui, hidden with #Edit.Visible = false in this very
        // batch and revealed on click. On 0.6.0 that disconnects the client:
        //
        //   Selected element in CustomUI command was not found. Selector: #Edit.Visible
        //
        // An element hidden in the same batch that appends its document is never
        // materialised, so no later command can address it -- and #ItemSelection and
        // #ItemSkins live inside #Edit, so they went with it. The event bindings for
        // the panel's buttons moved into updateEditLoadout for the same reason: the
        // buttons do not exist yet at this point.

        // Shown by default; updateEditLoadout empties it when a loadout is opened.
        updatePlayers(builder)

        if (loadoutService.progressionManager.canPrestigeLevelUp(user)) {
            builder.set("#PrestigeLevelUpContainer.Visible", true)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#PrestigeLevelUpContainer #Button",
                EventData.of("Action", "prestige"), false
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
            // The panel is appended here, so its button bindings have to ride along in
            // the same update. Without the event builder the icons would draw but
            // clicking Primary/Secondary/Melee/Utility would do nothing at all.
            val eventBuilder = UIEventBuilder()
            updateEditLoadout(loadout, builder, eventBuilder)
            updateSkinsContainer(false, builder)
            sendUpdate(builder, eventBuilder, false)
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

    /**
     * Draws the online player list: name, K/D this match, ping.
     *
     * Scoped to the caller's own session rather than everyone on the server, because
     * "K/D for the game" only means anything within one match -- a player in another
     * arena has stats from a game this one is not part of.
     *
     * Sorted by kills so the list reads like a scoreboard. Ties keep whatever order
     * the teams iterate in, which is stable enough for a panel that redraws each time
     * the menu opens.
     */
    private fun updatePlayers(builder: UICommandBuilder) {
        // Rebuilt from nothing each time rather than updated in place, so the card's
        // frame is created and destroyed along with its contents.
        builder.clear("#PlayersHost")

        val session = runCatching {
            val game = MiniGameEngine.instance.getGameByPlayerUUID(playerRef.uuid) ?: return@runCatching null
            game.getSessionByUUID(playerRef.uuid)
        }.getOrNull()
        // In the lobby or between matches: no card at all, rather than an empty one.
        if (session == null) return

        builder.append("#PlayersHost", "Pages/Menu/Players.ui")

        // Kept paired with their team rather than flattened, because the side is a
        // property of the team object and is unrecoverable from the participant alone.
        val players = session.teams
            .flatMap { team ->
                team.participants.values
                    .filterIsInstance<StatisticalMiniGameParticipant>()
                    .map { TeamAnnouncer.displayNameOf(team) to it }
            }
            .sortedByDescending { (_, participant) -> participant.kills }

        // Free-for-all modes give every player a single-member team, so every side
        // comes back null. Leaving the heading blank in that case is what keeps the
        // column from reading as a bug: an empty column under "TEAM" looks broken,
        // an empty column under nothing looks like spacing.
        val hasTeams = players.any { (team, _) -> team != null }

        builder.set("#PlayersTitle.Text", "PLAYERS (${players.size})")
        builder.set("#PlayersHeader #HeaderName.Text", "Player")
        builder.set("#PlayersHeader #HeaderTeam.Text", if (hasTeams) "Team" else "")
        builder.set("#PlayersHeader #HeaderKD.Text", "K / D")
        builder.set("#PlayersHeader #HeaderPing.Text", "Ping")

        for ((index, entry) in players.withIndex()) {
            val (team, participant) = entry
            builder.append("#Players", "Pages/Menu/PlayerRow.ui")
            builder.set("#Players[$index] #Name.Text", participant.playerRef.username)
            builder.set("#Players[$index] #Team.Text", team.orEmpty())
            builder.set("#Players[$index] #KD.Text", formatKd(participant.kills, participant.deaths))
            builder.set("#Players[$index] #Ping.Text", formatPing(participant.playerRef))
        }
    }

    /**
     * Kills, deaths and the ratio between them.
     *
     * Zero deaths is not a divide-by-zero to dodge quietly -- it is the score of
     * someone who has not died yet, and reporting it as their kill count is the
     * convention every shooter uses. Guarding with `coerceAtLeast(1)` gives the same
     * number without a special case.
     */
    private fun formatKd(kills: Int, deaths: Int): String =
        "$kills / $deaths  (%.2f)".format(kills.toDouble() / deaths.coerceAtLeast(1))

    /**
     * Round-trip time in milliseconds, or an em dash.
     *
     * [PongType.Raw] is the network round trip -- the number players mean by "ping".
     * The other two, Direct and Tick, fold in server tick scheduling and read higher.
     * Period 0 is the shortest averaging window the metric keeps, so this tracks a
     * connection going bad rather than smoothing it away over minutes.
     *
     * **The metric is in MICROSECONDS**, not milliseconds. PingInfo times with
     * System.nanoTime and converts to its own TIME_UNIT of MICROSECONDS before
     * recording. Printing the raw average as "ms" showed a local test client at
     * "2958 ms" -- a number wrong by a factor of a thousand, and one that looks
     * plausible enough as a bad connection that it could have shipped unnoticed.
     *
     * Wrapped because this reaches through four objects into engine internals for a
     * cosmetic number. If any link changes, the column should read "--" rather than
     * take the whole menu down with it.
     */
    private fun formatPing(ref: PlayerRef): String = runCatching {
        val micros = ref.packetHandler.getPingInfo(PongType.Raw).pingMetricSet.getAverage(0)
        if (micros.isNaN() || micros <= 0.0) return@runCatching "--"
        val millis = micros / 1000.0
        // Rounding a sub-millisecond connection to "0 ms" reads as no data rather
        // than as a very good connection, which is what it actually is.
        if (millis < 1.0) "<1 ms" else "${millis.roundToInt()} ms"
    }.getOrElse { "--" }

    /**
     * Empties a container instead of hiding it.
     *
     * A group with no title and no rows occupies nothing, so this reads the same to
     * the player as the old `Visible = false` -- but it leaves the element in the
     * client's tree, which is the whole point. See the note in [build].
     */
    private fun hideItemSelection(builder: UICommandBuilder) {
        builder.set("#ItemSelection #Title.Text", "")
        builder.clear("#ItemSelection #Items")
    }

    private fun hideItemSkins(builder: UICommandBuilder) {
        builder.set("#ItemSkins #SkinsTitle.Text", "")
        builder.clear("#ItemSkins #Skins")
    }

    /**
     * Removes the player list entirely, frame included.
     *
     * Clearing the host rather than the rows: the card carries a panel background and
     * a divider, so emptying its contents left the chrome behind as a styled empty box
     * on the weapon select screen.
     */
    private fun hidePlayers(builder: UICommandBuilder) {
        builder.clear("#PlayersHost")
    }

    private fun updateSkinsContainer(value: Boolean, providedBuilder: UICommandBuilder? = null) {
        val builder = providedBuilder ?: UICommandBuilder()
        if (value) {
            builder.set("#ItemSkins #SkinsTitle.Text", "SELECT SKIN")
        } else {
            hideItemSkins(builder)
        }
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
            LoadoutItemType.OITC -> loadout.copy(oitc = itemId)
        }
        // Commit before rendering: an async save here used to be overwritten by
        // a stale write queued during a page rebuild.
        val savedLoadouts = loadoutService.saveLoadoutAt(user, currentLoadoutIndex, nextLoadout) ?: return
        val builder = UICommandBuilder()
        val eventBuilder = UIEventBuilder()
        updateLoadoutBoxes(builder, eventBuilder, loadouts = savedLoadouts)
        updateEditLoadout(nextLoadout, builder, eventBuilder)
        sendUpdate(builder, eventBuilder, false)
    }

    /**
     * Draws the edit panel for [loadout], creating it if it is not already on screen.
     *
     * The panel is a document appended into the empty `#Edit` container rather than a
     * permanent part of Menu.ui that gets shown and hidden -- see the note in [build].
     * Re-appending from scratch each time is what makes that safe: every element below
     * is guaranteed to exist in this same batch, so no command can reference something
     * the client pruned.
     *
     * The buttons are new elements each time, so their bindings have to be re-added
     * with them. A stale binding is not an error the client reports; the button simply
     * stops responding, which is indistinguishable from a UI that has frozen.
     */
    private fun updateEditLoadout(
        loadout: Loadout,
        providedBuilder: UICommandBuilder? = null,
        providedEventBuilder: UIEventBuilder? = null,
    ) {
        val builder = providedBuilder ?: UICommandBuilder()
        val eventBuilder = providedEventBuilder ?: UIEventBuilder()

        // The player list and the edit panel are alternatives, not neighbours: opening
        // a loadout empties the list, and reopening the menu rebuilds it.
        hidePlayers(builder)

        builder.clear("#Edit")
        builder.append("#Edit", "Pages/Menu/Edit.ui")
        for (type in LoadoutItemType.entries) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, "#Edit #${type.displayName} #Button",
                EventData.of("Action", "select:${type.name}"), false
            )
        }

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

        // Deliberately not run through getItemByAllowance, unlike the four above.
        //
        // That check calls isItemLocked, which searches every row by item id and takes
        // the first hit -- it has no idea which row is being drawn. The Revolver and
        // the M40 each appear twice now, as a secondary/primary and again here, so the
        // lookup finds the other row's level (5 and 26) and reports both as locked for
        // a new player. The icon would then fall back to the starter gun while OitC
        // actually issued what they picked: the menu would show one gun and the mode
        // would hand them another.
        //
        // Nothing is bypassed by skipping it. Both entries are level 0 in the OITC row
        // by design, and chosenGun validates the saved id against gunItemIds before
        // issuing anything, so that is where this slot's authority lives.
        val oitcPath = loadout.oitc?.let { "Images/Guns/$it.png" } ?: "Images/Empty.png"

        builder.set("#Edit #EditLabel.Text", "EDITING: LOADOUT #${currentLoadoutIndex + 1}")
        builder.set("#Edit #Primary #Icon.Background", primaryPath)
        builder.set("#Edit #Secondary #Icon.Background", secondaryPath)
        builder.set("#Edit #Melee #Icon.Background", meleePath)
        builder.set("#Edit #Utility #Icon.Background", utilityPath)
        builder.set("#Edit #Oitc #Icon.Background", oitcPath)

        hideItemSelection(builder)
        hideItemSkins(builder)

        if (providedBuilder == null) {
            sendUpdate(builder, eventBuilder, false)
        }
    }

    private fun updateItemSelection(title: String, items: List<LoadoutPreviewItem>, type: LoadoutItemType) {
        val builder = UICommandBuilder()
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

