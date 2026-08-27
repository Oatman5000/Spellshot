package net.spellshot.moshpit.minigame.engine.arena

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import net.spellshot.core.ext.builderCodec
import net.spellshot.core.ext.map
import net.spellshot.core.ext.property

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

/**
 * Bulk-writes whole arenas in one copy, for `/arena import`.
 *
 * [setLocation] copies both maps on every call, so importing ~300 markers through it
 * would do ~300 full map copies. This does one.
 *
 * When [replace] is true an incoming arena wholly replaces the existing one; otherwise
 * incoming markers are merged over it, keeping any local markers not in the import.
 * Arenas absent from [incoming] are never touched either way -- an import can only add.
 *
 * Returns the number of markers written.
 */
fun MiniGameArenaResource.importEntries(
    incoming: Map<String, Map<String, Transform>>,
    replace: Boolean,
): Int {
    val newEntries = LinkedHashMap<String, MiniGameArenaEntry>(entries)
    var written = 0
    for ((arenaId, locations) in incoming) {
        val merged = LinkedHashMap<String, Transform>()
        if (!replace) merged.putAll(newEntries[arenaId]?.locations ?: emptyMap())
        merged.putAll(locations)
        newEntries[arenaId] = MiniGameArenaEntry(merged)
        written += locations.size
    }
    entries = newEntries
    return written
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
