package net.spellshot.moshpit.progression

import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.dsl.SpellshotCommand
import net.spellshot.core.framework.dsl.buildCommandCollection
import net.spellshot.core.ext.sendRawMessage
import net.spellshot.core.ext.requireGamePermission
import org.springframework.stereotype.Service

@Service
class XPCommand(val progressionManager: ProgressionManager) : SpellshotCommand({
    buildCommandCollection("xp", "Give progression xp") {
        command.requireGamePermission("xp.admin")
        playerCommand("info", "Show your progression xp info") {
            val target = required("target", "The player to give xp to", ArgTypes.PLAYER_REF)
            handle {
                val target = target()
                val targetUser = target.userOrNull ?: return@handle
                playerRef.sendRawMessage("${target.username} has ${targetUser.xp} / ${targetUser.xpForNextLevel} XP towards level ${targetUser.level + 1}.")
            }
        }
        playerCommand("give", "Give progression xp to a player") {
            val target = required("target", "The player to give xp to", ArgTypes.PLAYER_REF)
            val amount = required("amount", "The amount of xp to give", ArgTypes.INTEGER)
            handle {
                val target = target()
                val amount = amount()
                val targetUser = target.userOrNull ?: return@handle
                targetUser.runLockGuarded {
                    progressionManager.addXP(
                        targetUser,
                        amount,
                    )
                }
                playerRef.sendRawMessage("Gave ${target.username} $amount XP.")
                playerRef.sendRawMessage("${targetUser.xp} / ${targetUser.xpForNextLevel} XP towards level ${targetUser.level + 1}.")
            }
        }
        playerCommand("take", "Take progression xp from a player") {
            val target = required("target", "The player to give xp to", ArgTypes.PLAYER_REF)
            val amount = required("amount", "The amount of xp to take", ArgTypes.INTEGER)
            handle {
                val target = target()
                val amount = amount()
                val targetUser = target.userOrNull ?: return@handle
                targetUser.runLockGuarded {
                    progressionManager.removeXP(
                        targetUser,
                        amount,
                    )
                }
                playerRef.sendRawMessage("Removed ${target.username} $amount XP.")
                playerRef.sendRawMessage("${targetUser.xp} / ${targetUser.xpForNextLevel} XP towards level ${targetUser.level + 1}.")
            }
        }

    }
})

