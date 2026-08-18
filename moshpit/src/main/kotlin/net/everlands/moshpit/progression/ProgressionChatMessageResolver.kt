package net.everlands.moshpit.progression

import net.everlands.core.framework.Users.userOrNull
import net.everlands.core.framework.ChatMessageResolver
import net.everlands.core.framework.MessageContext
import org.springframework.stereotype.Service

@Service
class ProgressionChatMessageResolver : ChatMessageResolver {
    override fun condition(
        condition: String, context: MessageContext
    ): Boolean? {
        return null
    }

    override fun messageVariable(
        variable: String, context: MessageContext
    ): String? {
        val player = context.player ?: return null
        val user = player.userOrNull ?: return null
        return when (variable) {
            "level" -> user.level.toString()
            "prestige" -> user.prestige.toString()
            else -> null
        }
    }


}
