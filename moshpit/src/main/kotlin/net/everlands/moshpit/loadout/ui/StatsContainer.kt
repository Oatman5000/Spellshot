package net.everlands.moshpit.loadout.ui

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import net.everlands.core.framework.EverlandsUser
import net.everlands.core.leaderboard.killDeathRatioText
import net.everlands.moshpit.progression.getMoshpitStatsValue
import net.everlands.moshpit.progression.level
import net.everlands.moshpit.progression.prestige
import net.everlands.moshpit.progression.xpForNextLevelPercentage

class StatsContainer(val page: LoadoutPage) {

    fun updateStats(user: EverlandsUser, providedBuilder: UICommandBuilder? = null) {
        val builder = providedBuilder ?: UICommandBuilder()
        val kills = user.getMoshpitStatsValue("kills")
        val deaths = user.getMoshpitStatsValue("deaths")
        builder.set("#Kills #Amount.Text", kills.toString())
        builder.set("#Deaths #Amount.Text", deaths.toString())
        // Shared with the leaderboard so the menu and the board cannot disagree.
        builder.set("#KD #Amount.Text", killDeathRatioText(kills, deaths))

        val level = user.level
        val prestige = user.prestige

        builder.set("#Level #Amount.Text", level.toString())
        builder.set("#Prestige #Amount.Text", prestige.toString())
        builder.set("#Level #Bar.Value", user.xpForNextLevelPercentage)

        if (providedBuilder == null) {
            page.update(builder)
        }
    }
}
