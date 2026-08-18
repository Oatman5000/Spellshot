package net.everlands.core.ext

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

fun AbstractCommand.requireGamePermission(permission: String) {
    this.requirePermission("game.$permission")
}

fun CommandContext.sendRawMessage(message: String) {
    this.sendMessage(Message.raw(message))
}

val CommandContext.playerReferenceOrErr: Ref<EntityStore>
    get() = this.senderAsPlayerRef() ?: error("Player reference not found!")

val CommandContext.playerRef: PlayerRef get() {
    val ref = playerReferenceOrErr
    return ref.store.getComponent(ref, PlayerRef.getComponentType()) ?: error("PlayerRef component not found!")
}
