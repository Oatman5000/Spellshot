package net.spellshot.core.commands

import net.spellshot.core.framework.dsl.SpellshotCommand
import net.spellshot.core.framework.dsl.buildPlayerCommand
import net.spellshot.core.api.SpellshotLeave
import org.springframework.stereotype.Service

@Service
class LeaveCommand : SpellshotCommand({
    buildPlayerCommand("leave", "Leave the current server and return to the lobby") {
        command.requirePermission("spellshot.user")
        command.addAliases("l", "hub", "lobby", "exit")
        handle {
            SpellshotLeave.leave(playerRef)
        }
    }
})
