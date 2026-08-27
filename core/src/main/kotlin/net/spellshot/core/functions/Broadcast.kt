package net.spellshot.core.functions

import com.hypixel.hytale.server.core.universe.Universe
import net.spellshot.core.ext.sendMessageFormat

fun broadcastFormat(message: String) {
    for (ref in Universe.get().players) {
        ref.sendMessageFormat(message)
    }
}
