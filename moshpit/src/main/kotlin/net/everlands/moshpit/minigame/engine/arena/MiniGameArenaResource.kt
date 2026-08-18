package net.everlands.moshpit.minigame.engine.arena

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import net.everlands.core.ext.builderCodec
import net.everlands.core.ext.map
import net.everlands.core.ext.property

data class MiniGameArenaResource(
    var entries: MutableMap<String, MiniGameArenaEntry> = mutableMapOf()
) : Resource<ChunkStore> {

    override fun clone() = copy()

    companion object {
        lateinit var resourceType: ResourceType<ChunkStore, MiniGameArenaResource>
        val CODEC = builderCodec(::MiniGameArenaResource)
            .property(MiniGameArenaResource::entries, MiniGameArenaEntry.CODEC.map())
            .build()
    }
}

data class MiniGameArenaEntry(
    var locations: MutableMap<String, Transform> = mutableMapOf()
) {
    companion object {
        val CODEC = builderCodec(::MiniGameArenaEntry)
            .property(MiniGameArenaEntry::locations, Transform.CODEC.map())
            .build()
    }
}


val World.arenaResource: MiniGameArenaResource
    get() {
        return chunkStore.store.getResource(MiniGameArenaResource.resourceType)
    }

/**
 * Writes a marker, replacing the codec's read-only maps with mutable copies.
 *
 * ObjectMapCodec wraps everything it decodes in `Collections.unmodifiableMap`,
 * regardless of the `LinkedHashMap()` supplier it is handed. So both [entries]
 * and each entry's `locations` come back read-only, and the obvious
 * `entries.computeIfAbsent(id) { ... }.locations[name] = transform` throws a bare
 * `UnsupportedOperationException` -- no message, no indication of which map.
 *
 * Copy-on-write is used rather than making the codec produce mutable maps,
 * because that codec helper is shared with the loadout and user stores and this
 * is the only place that writes back into a decoded map.
 */
fun MiniGameArenaResource.setLocation(arenaId: String, name: String, transform: Transform) {
    val newEntries = LinkedHashMap<String, MiniGameArenaEntry>(entries)
    val newLocations = LinkedHashMap<String, Transform>(newEntries[arenaId]?.locations ?: emptyMap())
    newLocations[name] = transform
    newEntries[arenaId] = MiniGameArenaEntry(newLocations)
    entries = newEntries
}

/** Removes a marker. Returns false if the arena or the marker did not exist. */
fun MiniGameArenaResource.removeLocation(arenaId: String, name: String): Boolean {
    val existing = entries[arenaId] ?: return false
    if (!existing.locations.containsKey(name)) return false
    val newLocations = LinkedHashMap<String, Transform>(existing.locations)
    newLocations.remove(name)
    val newEntries = LinkedHashMap<String, MiniGameArenaEntry>(entries)
    newEntries[arenaId] = MiniGameArenaEntry(newLocations)
    entries = newEntries
    return true
}
