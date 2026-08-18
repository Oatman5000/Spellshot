package net.everlands.moshpit.minigame.engine.arena

import net.everlands.core.framework.dsl.EverlandsCommand
import net.everlands.core.framework.dsl.buildCommandCollection
import net.everlands.core.ext.transform
import net.everlands.core.ext.requireGamePermission
import net.everlands.core.ext.sendRawMessage
import org.springframework.stereotype.Service

/**
 * `/arena` -- inspect and edit the marker set for the world you are standing in.
 *
 * Every handler used to open with `playerRef.playerOrErr` followed by
 * `player.world!!`, which re-derives what the DSL already hands it: `HandleScope`
 * carries `world`, `ref` and `store` for exactly this. That removed seven not-null
 * assertions from this file, each of which would have surfaced as a bare
 * NullPointerException.
 */
@Service
class ArenaCommand(val manager: MiniGameArenaManager) : EverlandsCommand({
    buildCommandCollection("arena", "Arena management commands") {
        command.requireGamePermission("arena")

        playerCommand("entries", "Get all entries about current world") {
            handle {
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
                // One lookup, not a containsKey followed by an unchecked get.
                val entry = world.arenaResource.entries[id]
                if (entry == null) {
                    context.sendRawMessage("$id is not a valid arena")
                    return@handle
                }
                context.sendRawMessage("Visualizing arena '$id' locations for 30 seconds...")
                manager.visualize(playerRef, world, entry)
            }
        }

        playerCommand("info", "Get information about a arena") {
            val idArg = required<String>("id", "ID of the arena")
            handle {
                val id = idArg()
                val entry = world.arenaResource.entries[id]
                if (entry == null) {
                    context.sendRawMessage("$id is not a valid arena")
                    return@handle
                }
                context.sendRawMessage("Arena ID: $id  (${entry.locations.size} markers)")
                for ((key, transform) in entry.locations) {
                    val p = transform.position
                    context.sendRawMessage(" - $key: X: ${p.x} Y: ${p.y} Z: ${p.z}")
                }
            }
        }

        collection("loc", "Location management commands") {
            playerCommand("set", "Set a location in the arena") {
                val idArg = required<String>("id", "ID of the arena")
                val locNameArg = required<String>("location", "The name of the location")
                handle {
                    val id = idArg()
                    val locName = locNameArg()
                    val here = store.transform(ref).transform.clone()
                    world.arenaResource.setLocation(id, locName, here)
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
