package net.spellshot.core.framework

import com.hypixel.hytale.server.core.universe.PlayerRef
import org.springframework.stereotype.Service

class MessageContext(
    val player: PlayerRef? = null,
    val variables: Map<String, String> = emptyMap()
)

interface ChatMessageResolver {
    fun condition(condition: String, context: MessageContext): Boolean?
    fun messageVariable(variable: String, context: MessageContext): String?
}

@Service
class ChatModule {
    private val resolvers = mutableListOf<ChatMessageResolver>()
    private val pattern = Regex("\\{([A-Za-z0-9_.]+)}")
    fun register(resolver: ChatMessageResolver) { resolvers.add(resolver) }

    fun sendConfigurableMessage(
        playerRef: PlayerRef,
        context: MessageContext,
        message: ConfigurableMessage
    ) {
        var text = format(message.template, context)
        for ((k, v) in context.variables) text = text.replace("{$k}", v)
        playerRef.sendMessage(MessageParser.parse(text))
    }
    fun format(template: String, context: MessageContext): String =
        pattern.replace(template) { m ->
            resolvers.firstNotNullOfOrNull { it.messageVariable(m.groupValues[1], context) } ?: m.value
        }
}

@kotlinx.serialization.Serializable
data class ConfigurableMessage(val template: String = "") {
    fun format(vararg params: Pair<String, Any?>): String {
        var out = template
        for ((k, v) in params) out = out.replace("{$k}", v?.toString() ?: "")
        return out
    }
    override fun toString(): String = template
}

