package net.spellshot.moshpit.minigame.engine.ext

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder

fun UICommandBuilder.setTimeLeft(count: Int, selector: String = "#Time.Text") {
    if (count == -1) {
        set("#Time.Text", "")
        return
    }
    set(selector, String.format("%02d:%02d", count / 60, count % 60))
}
