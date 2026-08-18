package net.everlands.core.framework

import com.hypixel.hytale.server.core.Message

object MessageParser {
    private val tag = Regex("<[^>]*>")
    private val legacy = Regex("&([0-9a-fk-or])")
    fun parse(input: String): Message = Message.raw(strip(input))
    fun strip(input: String): String = legacy.replace(tag.replace(input, ""), "")
}
