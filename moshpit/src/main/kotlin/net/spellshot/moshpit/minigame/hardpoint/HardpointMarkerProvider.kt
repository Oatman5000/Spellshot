package net.spellshot.moshpit.minigame.hardpoint

import com.hypixel.hytale.protocol.Direction
import com.hypixel.hytale.protocol.Position
import com.hypixel.hytale.protocol.Transform
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerIconSize
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector

class HardpointMarkerProvider(val session: HardpointMiniGameSession) : WorldMapManager.MarkerProvider {

    override fun update(
        world: World,
        player: Player,
        collector: MarkersCollector
    ) {
        val point = session.currentPoint
        val transform = Transform(Position(point.x, point.y, point.z), Direction(0f, 0f, 0f))
        collector.add(
            MapMarker(
                "point-${transform.hashCode()}",
                Message.raw("Point to capture").formattedMessage,
                "Spawn.png",
                transform,
                null,
                null,
                // Update 7 added two trailing parameters to MapMarker. Major is the
                // larger of the two icon sizes and is right here: the hardpoint is the
                // one thing on the map players are meant to be converging on, so it
                // should not be drawn at the same weight as ordinary scenery.
                MapMarkerIconSize.Major,
                // The trailing String? is optional and left unset, which keeps the
                // "Spawn.png" icon above in charge of how this draws.
                null
            )
        )
    }
}
