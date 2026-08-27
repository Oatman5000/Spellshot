package net.spellshot.moshpit.progression

import net.spellshot.core.framework.Users.userOrNull
import net.spellshot.core.framework.ChatMessageResolver
import net.spellshot.core.framework.MessageContext
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
