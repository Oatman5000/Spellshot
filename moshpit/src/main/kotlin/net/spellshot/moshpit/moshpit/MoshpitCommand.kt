package net.spellshot.moshpit.moshpit

import net.spellshot.core.framework.dsl.SpellshotCommand
import net.spellshot.core.framework.dsl.buildCommandCollection
import net.spellshot.core.framework.launchGlobal
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.core.ext.playerOrErr
import net.spellshot.core.ext.playerRef
import net.spellshot.core.ext.requireGamePermission
import net.spellshot.moshpit.ui.HUDComposer
import org.springframework.stereotype.Service

@Service
class MoshpitCommand(val manager: MoshpitManager, val composer: HUDComposer) : SpellshotCommand({
    buildCommandCollection("moshpit", "Moshpit command") {
        command.requireGamePermission("moshpit")
        playerCommand("games", "List available moshpit games") {
            handle {
                val ref = context.playerRef
                context.sendRawMessage("Available Moshpit Games:")
                for (game in MoshpitGame.entries) {
                    ref.sendRawMessage("${game.name}:")
                    for (session in game.provider().sessions) {
                        ref.sendRawMessage(" - Session ID: ${session.id}, State: ${session.state} , Participants: ${session.participantCount}")
                    }
                }
            }
        }
        command("random", "Force restart the current mini-game") {
            handle {
                val game = manager.config.value.games.random().game
                manager.switchGame(game)
                context.sendRawMessage("Switched to random game: ${game.name}")
            }
        }
        playerCommand("reloadhud", "Reload hud") {
            handle {
                val playerRef = context.playerRef
                val player = playerRef.playerOrErr
                composer.refresh(player, playerRef)
                context.sendRawMessage("Reloaded hud")
            }
        }
        playerCommand("stopserver", "Stop current mini-game session") {
            handle {
                launchGlobal {
                    manager.restartServer()
                }
            }
        }
        playerCommand("forcevote", "Force start a vote for the next mini-game") {
            handle {
                if(!manager.isSessionActive) {
                    context.sendRawMessage("No active session to force vote for")
                    return@handle
                }
                manager.currentSession.state = 1
                context.sendRawMessage("Forced vote for the next mini-game")
            }
        }
        playerCommand("leave", "Reload hud") {
            handle {
                val playerRef = context.playerRef
                if (!manager.isSessionActive) {
                    context.sendRawMessage("No active session to leave")
                    return@handle
                }
                manager.currentSession.removeParticipant(playerRef)
                context.sendRawMessage("You have left the current mini-game session")
            }
        }
    }
})
