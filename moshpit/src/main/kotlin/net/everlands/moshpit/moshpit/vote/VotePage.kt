package net.everlands.moshpit.moshpit.vote

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.ext.builderCodec
import net.everlands.core.ext.property
import net.everlands.moshpit.minigame.engine.arena.MiniGameArena
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.moshpit.MoshpitGameConfig
import net.everlands.moshpit.moshpit.MoshpitManager

class VotePage(
    playerRef: PlayerRef, val moshpitManager: MoshpitManager, val voteManager: MoshpitVoteManager,
    var notifier: () -> Unit
) :
    InteractiveCustomUIPage<VotePage.PageData>(playerRef, CustomPageLifetime.CantClose, PageData.CODEC) {

    override fun build(
        ref: Ref<EntityStore>,
        builder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        builder.append("Pages/Vote/Menu.ui")
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: PageData) {
        val action = data.action ?: return
        val games = moshpitManager.config.value.games
        val game = games.firstOrNull { it.game.name == action }
        if (game != null) {
            voteForGame(game.game)
            return
        }

        if (action.contains(":")) {
            val parts = action.split(":")
            if (parts.size != 2) return
            val gameName = parts[0]
            val arenaId = parts[1]
            val gameConfig = games.firstOrNull { it.game.name == gameName } ?: return
            if (gameConfig.arenas.contains(arenaId)) {
                voteForMap(arenaId)
            }
            return
        }
    }

    fun closeWindow() {
        close()
    }

    fun updateCountdown(seconds: Int) {
        val builder = UICommandBuilder()
        builder.set("#Countdown.Text", "Voting ends in ${seconds}s")
        sendUpdate(builder)
    }

    private fun voteForGame(game: MoshpitGame) {
        moshpitManager.voteManager.voteForGame(playerRef.uuid, game)
        notifier()
    }

    fun updateGames(uiBuilder: UICommandBuilder? = null) {
        val builder = uiBuilder ?: UICommandBuilder()
        for ((index, game) in moshpitManager.config.value.games.withIndex()) {
            builder.set("#Games[$index] #Votes.Text", "${voteManager.countVotesForGame(game.game)} Votes")
        }
        if (uiBuilder == null) {
            sendUpdate(builder)
        }
    }

    private fun voteForMap(arenaId: String) {
        moshpitManager.voteManager.voteForMap(playerRef.uuid, arenaId)
        notifier()
    }

    fun updateMaps(gameConfig: MoshpitGameConfig, uiBuilder: UICommandBuilder? = null) {
        val builder = uiBuilder ?: UICommandBuilder()
        val allowedArenas = findAllowedArenas(gameConfig)
        for ((index, arena) in allowedArenas.withIndex()) {
            builder.set("#Maps[$index] #Votes.Text", "${voteManager.countVotesForMap(arena.id)} Votes")
        }
        if (uiBuilder == null) {
            sendUpdate(builder)
        }
    }

    private fun findAllowedArenas(gameConfig: MoshpitGameConfig): List<MiniGameArena> {
        return moshpitManager.arenaManager.list().filter { gameConfig.arenas.contains(it.id) }
    }

    fun enableGames() {
        val builder = UICommandBuilder()
        val eventBuilder = UIEventBuilder()
        builder.set("#Maps.Visible", false)
        builder.set("#Games.Visible", true)
        builder.set("#TitleLabel.Text", "VOTE FOR NEXT GAME")
        for ((index, game) in moshpitManager.config.value.games.withIndex()) {
            builder.append("#Games", "Pages/Vote/Game.ui")
            builder.set("#Games[$index] #Name.Text", game.game.name)
            builder.set("#Games[$index] #Icon.ItemId", game.iconId)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Games[$index] #Button",
                EventData.of("Action", game.game.name),
                false
            )
        }
        sendUpdate(builder, eventBuilder, false)
    }

    fun enableMaps(gameConfig: MoshpitGameConfig) {
        val builder = UICommandBuilder()
        val eventBuilder = UIEventBuilder()
        builder.set("#Maps.Visible", true)
        builder.set("#Games.Visible", false)
        builder.set("#TitleLabel.Text", "VOTE FOR NEXT MAP")
        val arenas = findAllowedArenas(gameConfig)
        for ((index, arena) in arenas.withIndex()) {
            builder.append("#Maps", "Pages/Vote/Map.ui")
            builder.set("#Maps[$index] #Name.Text", arena.name)
            builder.set("#Maps[$index] #Map.Background", "Maps/${arena.id}.png")
            builder.set("#Maps[$index] #Author.Text", "by ${arena.author}")
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Maps[$index] #Button",
                EventData.of("Action", "${gameConfig.game.name}:${arena.id}"),
                false
            )
        }
        sendUpdate(builder, eventBuilder, false)
    }

    class PageData {
        var action: String? = null

        companion object {
            val CODEC = builderCodec(::PageData)
                .property(PageData::action, Codec.STRING)
                .build()
        }
    }

}
