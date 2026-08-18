package net.everlands.moshpit.minigame.engine.arena

import net.everlands.core.framework.dsl.EverlandsCommand
import net.everlands.core.framework.dsl.buildCommandCollection
import net.everlands.core.ext.transform
import net.everlands.core.ext.playerOrErr
import net.everlands.core.ext.referenceOrErr
import net.everlands.core.ext.requireGamePermission
import net.everlands.core.ext.sendRawMessage
import org.springframework.stereotype.Service

@Service
class ArenaCommand(val manager: MiniGameArenaManager) : EverlandsCommand({
    buildCommandCollection("arena", "Arena management commands") {
        command.requireGamePermission("arena")
        playerCommand("entries", "Get all entries about current world") {
            handle {
                val player = playerRef.playerOrErr
                val world = player.world!!
                val arena = world.arenaResource
                context.sendRawMessage("Arenas in world '${world.name}':")
                for (id in arena.entries.keys) {
                    context.sendRawMessage(" - $id")
                }
            }
        }
        playerCommand("debug", "Visualize arena locations") {
            val idArg = required<String>("id", "ID of the arena")
            handle {
                val id = idArg()
                val player = playerRef.playerOrErr
                val world = player.world!!
                val arena = world.arenaResource
                if (!arena.entries.contains(id)) {
                    context.sendRawMessage("$id is not a valid arena")
                    return@handle
                }
                context.sendRawMessage("Visualizing arena '$id' locations for 30 seconds...")
                val entry = arena.entries[id]!!
                manager.visualize(playerRef, world, entry)
            }
        }
        playerCommand("info", "Get information about a arena") {
            val idArg = required<String>("id", "ID of the arena")
            handle {
                val id = idArg()
                val player = playerRef.playerOrErr
                val world = player.world!!
                val arena = world.arenaResource
                if (!arena.entries.contains(id)) {
                    context.sendRawMessage("$id is not a valid arena")
                    return@handle
                }
                context.sendRawMessage("Arena ID: $id")
                context.sendRawMessage("Locations:")
                for ((key, transform) in arena.entries[id]!!.locations) {
                    context.sendRawMessage(" - $key: X: ${transform.position.x} Y: ${transform.position.y} Z: ${transform.position.z}")
                }
            }
        }
        collection("loc", "Location management commands") {
            playerCommand("set", "Set a location in the arena") {
                val idArg = required<String>("id", "ID of the arena")
                val locNameArg = required<String>("location", "The name of the location")
                handle {
                    val id = idArg()
                    val player = playerRef.playerOrErr
                    val world = player.world!!
                    val locName = locNameArg()
                    val arena = world.arenaResource
                    val here = player.referenceOrErr.store
                        .transform(player.referenceOrErr).transform.clone()
                    arena.setLocation(id, locName, here)
                    context.sendRawMessage(
                        "Set '$locName' for arena '$id' at " +
                            "${here.position.x.toInt()}, ${here.position.y.toInt()}, ${here.position.z.toInt()}"
                    )
                }
            }
            playerCommand("del", "Remove a location from the arena") {
                val idArg = required<String>("id", "ID of the arena")
                val locNameArg = required<String>("location", "The name of the location")
                handle {
                    val id = idArg()
                    val player = playerRef.playerOrErr
                    val world = player.world!!
                    val locName = locNameArg()
                    val arena = world.arenaResource
                    if (!arena.entries.containsKey(id)) {
                        context.sendRawMessage("$id is not a valid arena")
                        return@handle
                    }
                    if (!arena.removeLocation(id, locName)) {
                        context.sendRawMessage("$locName is not a valid location for arena '$id'")
                        return@handle
                    }
                    context.sendRawMessage("Removed location '$locName' from arena '$id'")
                }
            }
        }
    }
})
