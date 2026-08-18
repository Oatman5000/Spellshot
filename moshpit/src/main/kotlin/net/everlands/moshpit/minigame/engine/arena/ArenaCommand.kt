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

        playerCommand("import", "Load markers from mods/arena-markers.json into this world") {
            handle {
                when (val result = ArenaMarkerImport.load()) {
                    is ArenaMarkerImport.Result.NotFound -> {
                        context.sendRawMessage("No marker file found. Looked for:")
                        for (path in result.looked) context.sendRawMessage(" - $path")
                        context.sendRawMessage("Upload MiniGameArenaResource.json to mods/ as 'arena-markers.json'.")
                    }

                    is ArenaMarkerImport.Result.Unreadable ->
                        context.sendRawMessage("Could not read ${result.path}: ${result.reason}")

                    is ArenaMarkerImport.Result.Empty ->
                        context.sendRawMessage("${result.path} parsed but contains no markers.")

                    is ArenaMarkerImport.Result.Parsed -> {
                        // Merge, not replace: an import should never silently drop a
                        // marker someone placed by hand since the file was exported.
                        val written = world.arenaResource.importEntries(result.arenas, replace = false)
                        context.sendRawMessage("Imported $written markers from ${result.path}:")
                        for ((arenaId, locations) in result.arenas) {
                            context.sendRawMessage(" - $arenaId: ${locations.size}")
                        }
                        context.sendRawMessage(
                            "Live now - spawns work immediately. They reach disk on the next " +
                                "world save, so stop the server cleanly to make it permanent."
                        )
                    }
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
