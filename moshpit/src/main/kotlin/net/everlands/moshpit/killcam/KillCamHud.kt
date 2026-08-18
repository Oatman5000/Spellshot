package net.everlands.moshpit.killcam

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.moshpit.ui.AbstractHud
import net.everlands.moshpit.ui.HUDComposer

class KillCamHud(player: Player, playerRef: PlayerRef, val killerName: String, val hudComposer: HUDComposer) :
    AbstractHud(player, playerRef, "KillCam") {

    fun updateCountdown(secondsLeft: Int) {
        hudComposer.update(playerRef, key) {
            if (secondsLeft == 0) {
                set("#Countdown.Text", "Respawning now")
            } else {
                set("#Countdown.Text", "Respawning in $secondsLeft...")
            }
        }
    }

    override fun handle(builder: UICommandBuilder) {
        builder.append("Pages/KillFeed.ui")
        builder.set("#KilledBy.Text", "KILLED BY $killerName")
    }
}
