package net.everlands.core.functions

import com.hypixel.hytale.server.core.universe.Universe
import net.everlands.core.ext.sendMessageFormat

fun broadcastFormat(message: String) {
    for (ref in Universe.get().players) {
        ref.sendMessageFormat(message)
    }
}
