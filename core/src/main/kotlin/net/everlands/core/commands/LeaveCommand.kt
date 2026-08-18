package net.everlands.core.commands

import net.everlands.core.framework.dsl.EverlandsCommand
import net.everlands.core.framework.dsl.buildPlayerCommand
import net.everlands.core.api.EverlandsLeave
import org.springframework.stereotype.Service

@Service
class LeaveCommand : EverlandsCommand({
    buildPlayerCommand("leave", "Leave the current server and return to the lobby") {
        command.requirePermission("everlands.user")
        command.addAliases("l", "hub", "lobby", "exit")
        handle {
            EverlandsLeave.leave(playerRef)
        }
    }
})
