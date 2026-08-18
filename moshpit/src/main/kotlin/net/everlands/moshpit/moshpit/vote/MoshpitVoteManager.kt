package net.everlands.moshpit.moshpit.vote

import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.universe.Universe
import net.everlands.core.framework.onWorld
import net.everlands.core.ext.world
import kotlinx.coroutines.delay
import net.everlands.core.functions.broadcastFormat
import net.everlands.core.ext.play2DSound
import net.everlands.core.ext.playerOrErr
import net.everlands.core.ext.playerOrNull
import net.everlands.core.ext.referenceOrNull
import net.everlands.moshpit.moshpit.MoshpitGame
import net.everlands.moshpit.moshpit.MoshpitManager
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class MoshpitVoteManager(val manager: MoshpitManager) {

    private val logger = LoggerFactory.getLogger(MoshpitVoteManager::class.java)

    private val gameVotes = mutableMapOf<UUID, MoshpitGame>()
    private val mapVotes = mutableMapOf<UUID, String>()

    suspend fun startVote() {
        gameVotes.clear()
        mapVotes.clear()
        val config = manager.config.value
        delay(config.startVoteDelay * 1000L)
        val players = Universe.get().players
        val pages = CopyOnWriteArrayList<VotePage>()
        for (playerRef in players) {
            try {
                // Must await: enableGames() below iterates `pages`, so every page
                // has to exist before we fall out of this loop.
                playerRef.world?.onWorld {
                    try {
                        val ref = playerRef.referenceOrNull ?: return@onWorld
                        val player = playerRef.playerOrNull ?: return@onWorld
                        val page = VotePage(playerRef, manager, this@MoshpitVoteManager) {
                            for (page in pages) {
                                try {
                                    page.updateGames()
                                } catch (e: Exception) {
                                    logger.warn("Error notifying vote page for player: ${e.message}")
                                }
                            }
                        }
                        pages.add(page)
                        // return@onWorld, not continue -- this is inside a lambda, and
                        // Kotlin does not allow a non-local continue there. Skipped
                        // rather than asserted: a player between worlds has no world,
                        // and `!!` threw a NullPointerException that the catch below
                        // reported only as "null".
                        val playerWorld = player.world ?: return@onWorld
                        player.pageManager.openCustomPage(ref, playerWorld.entityStore.store, page)
                    } catch (e: Exception) {
                        logger.warn("Error opening vote page context for player ${playerRef.username}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error opening vote page for player ${playerRef.username}: ${e.message}")
            }
        }
        for (page in pages) {
            page.enableGames()
        }
        countDown(config.gameVoteCountdown, pages)
        var gameWinner = config.games.maxByOrNull { countVotesForGame(it.game) }?.game
        if (gameWinner == null) {
            gameWinner = config.games.random().game
        }
        val gameConfig = config.games.first { it.game == gameWinner }
        for (page in pages) {
            page.enableMaps(gameConfig)
            page.notifier = {
                for (otherPage in pages) {
                    try {
                        otherPage.updateMaps(gameConfig)
                    } catch (e: Exception) {
                        logger.warn("Error notifying vote page for player: ${e.message}")
                    }
                }
            }
        }
        countDown(config.mapVoteCountdown, pages)
        val mapWinner = mapVotes.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        var arena = mapWinner?.let { winner -> manager.arenaManager.list().firstOrNull { it.id == winner } }
        if (arena == null) {
            // randomOrNull + fallback: an allow-list that matches no real arena
            // used to throw NoSuchElementException here and kill the vote.
            val all = manager.arenaManager.list()
            arena = all.filter { gameConfig.arenas.contains(it.id) }.randomOrNull()
                ?: all.randomOrNull()
        }
        for (player in players) {
            try {
                player.world?.onWorld {
                    try {
                        val player = player.playerOrErr
                        val page = player.pageManager.customPage
                        if (page is VotePage) {
                            page.closeWindow()
                        }
                    } catch (e: Exception) {
                        logger.warn("Error during vote page closing context for player ${player.username}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error closing vote page for player ${player.username}: ${e.message}")
            }
        }
        broadcastFormat("\n<bc>Moshpit Vote Results:")
        broadcastFormat("<7>Game: <c>${gameWinner.name}")
        if (arena != null) {
            broadcastFormat("<7>Map: <c>${arena.name}\n")
        }
        val soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Generic_Crafting_Failed")
        for (player in players) {
            try {
                player.play2DSound(soundIndex, 5f)
            } catch (e: Exception) {
                logger.warn("Error playing vote end sound for player ${player.username}: ${e.message}")
            }
        }
        delay(config.endVoteDelay * 1000L)
        manager.switchGame(gameWinner, arena)
    }

    private suspend fun countDown(countdown: Int, pages: List<VotePage>) {
        for (secondsLeft in countdown downTo 0) {
            for (page in pages) {
                try {
                    page.updateCountdown(secondsLeft)
                } catch (e: Exception) {
                    logger.warn("Error updating vote countdown for player: ${e.message}")
                }
            }
            delay(1000L)
        }
    }

    fun voteForGame(playerId: UUID, game: MoshpitGame) {
        gameVotes[playerId] = game
    }

    fun voteForMap(playerId: UUID, arenaId: String) {
        mapVotes[playerId] = arenaId
    }

    fun countVotesForMap(arenaId: String): Int {
        return mapVotes.values.count { it == arenaId }
    }

    fun countVotesForGame(game: MoshpitGame): Int {
        return gameVotes.values.count { it == game }
    }
}
