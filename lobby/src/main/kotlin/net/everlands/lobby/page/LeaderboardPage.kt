package net.everlands.lobby.page

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
import net.everlands.core.ext.builderCodec
import net.everlands.core.ext.property
import net.everlands.core.leaderboard.BoardId
import net.everlands.core.leaderboard.LeaderboardRow
import net.everlands.lobby.leaderboard.LeaderboardService
import org.slf4j.LoggerFactory

/**
 * The leaderboard menu.
 *
 * Reads only the cached snapshot [LeaderboardService] holds, so opening it never
 * blocks on Redis. Tabs are built from the service's board list rather than
 * declared in the .ui, so adding a mode is a config change.
 *
 * Switching tabs REOPENS the page with a different [board] rather than patching
 * the open one. A UICommandBuilder can append children but not remove them, so
 * an in-place switch left the previous board's rows on screen underneath the new
 * ones. Reopening is the only way to get a clean list.
 */
class LeaderboardPage(
    private val player: Player,
    playerRef: PlayerRef,
    private val service: LeaderboardService,
    private val board: String = BoardId.GLOBAL_KILLS,
) : InteractiveCustomUIPage<LeaderboardPage.PageData>(
    playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC
) {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun build(
        ref: Ref<EntityStore>,
        builder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        builder.append("Pages/Leaderboard.ui")
        builder.set("#Updated.Text", service.updatedText())

        service.tabs.forEachIndexed { index, tab ->
            builder.append("#Tabs", "Pages/LeaderboardTab.ui")
            builder.set("#Tabs[$index] #Title.Text", tab.label)
            // Active tab is cyan, the rest muted -- same convention as the
            // weapon selector's slot numbers.
            builder.set(
                "#Tabs[$index] #Title.Style.TextColor",
                if (tab.id == board) "#00D4FF" else "#8FA3B8"
            )
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Tabs[$index] #Button",
                EventData.of("Action", tab.id),
                false
            )
        }

        val rows: List<LeaderboardRow> = service.snapshot?.board(board).orEmpty()
        rows.take(MAX_ROWS).forEachIndexed { index, row ->
            builder.append("#Entries", "Pages/LeaderboardEntry.ui")
            val e = "#Entries[$index]"
            builder.set("$e #Rank.Text", "#${index + 1}")
            builder.set("$e #Name.Text", row.username)
            builder.set("$e #Score.Text", formatValue(board, row))
            builder.set("$e #Kills #Value.Text", row.kills.toString())
            builder.set("$e #Deaths #Value.Text", row.deaths.toString())
            builder.set("$e #KD #Value.Text", row.kdText)
        }
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: PageData) {
        val action = data.action ?: return
        if (action == board) return
        if (service.tabs.none { it.id == action }) return
        // No close() first. Opening a custom page replaces whatever is open --
        // moshpit's vote flow does exactly this. Closing beforehand let the
        // client process the close AFTER the open, which left the replacement
        // drawn but with its event bindings torn down: the first tab switch
        // worked, and every one after it did nothing.
        runCatching {
            player.pageManager.openCustomPage(ref, ref.store, LeaderboardPage(player, playerRef, service, action))
        }.onFailure { log.warn("could not switch leaderboard tab: ${it.message}") }
    }

    /**
     * The headline number for the active board.
     *
     * K/D is published as a scaled integer so the snapshot can sort on a Long;
     * it is divided back out here. Level ranks on XP but reads as a level.
     */
    private fun formatValue(board: String, row: LeaderboardRow): String = when (board) {
        BoardId.KD -> String.format("%.2f", row.value / 1000.0)
        BoardId.LEVEL -> "Lv ${row.level}"
        BoardId.WINS -> "${row.value} wins"
        else -> row.value.toString()
    }

    class PageData {
        var action: String? = null

        companion object {
            val CODEC: BuilderCodec<PageData> = builderCodec(::PageData)
                .property(PageData::action, Codec.STRING)
                .build()
        }
    }

    private companion object {
        /** Rows drawn. The snapshot carries more; the panel only fits so many. */
        const val MAX_ROWS = 10
    }
}
