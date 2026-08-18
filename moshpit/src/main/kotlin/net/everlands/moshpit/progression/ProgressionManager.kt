package net.everlands.moshpit.progression

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.everlands.core.ext.playerRefOrNull
import net.everlands.core.framework.ChatModule
import net.everlands.core.framework.MessageContext
import net.everlands.core.framework.EverlandsUser
import net.everlands.core.ext.play2DSound
import net.everlands.moshpit.item.skin.PermissionSkinsProvider
import net.everlands.moshpit.item.skin.SkinProvider
import org.springframework.stereotype.Service
import kotlin.math.pow

@Service
class ProgressionManager(
    val config: ProgressionConfig.Binder, val skinProviders: List<SkinProvider>,
    val experienceConfig: ExperienceConfig.Binder, val chatModule: ChatModule
) {


    fun canPrestigeLevelUp(user: EverlandsUser): Boolean {
        val config = config.value
        return user.level >= config.maxLevel && user.prestige < config.maxPrestige
    }

    fun getSkins(user: EverlandsUser, playerRef: PlayerRef, itemId: String): List<ItemSkinProgressionConfig> {
        return buildList {
            for (provider in skinProviders) {
                val skins = provider.provide(user, playerRef, itemId)
                if (provider is ProgressionSkinProvider) {
                    for (skin in skins) {
                        val config = getSkinConfig(skin.itemId) ?: continue
                        add(config)
                    }
                } else if (provider is PermissionSkinsProvider) { // TODO: Refactor permission skin provider
                    for (skin in skins) {
                        add(ItemSkinProgressionConfig(skin.itemId, skin.name, 0, 0))
                    }
                }

            }
        }
    }

    fun getSkinParentItemId(user: EverlandsUser, skinId: String): String? {
        return skinProviders.firstNotNullOfOrNull {
            it.findDefaultItemId(user, skinId)
        }
    }

    fun getSkinConfig(skinId: String): ItemSkinProgressionConfig? {
        val skins = config.value.items.values.flatten().flatMap { it.skins }
        return skins.firstOrNull { it.itemId == skinId }
    }

    fun isSkin(skinId: String): Boolean {
        for (provider in skinProviders) {
            if (provider.isSkin(skinId)) {
                return true
            }
        }
        return false
    }

    suspend fun addPrestige(user: EverlandsUser): Int {
        val currentPrestige = user.prestige
        if (currentPrestige >= config.value.maxPrestige) {
            return currentPrestige
        }
        val nextPrestige = currentPrestige + 1
        user.setCustomData("prestige", nextPrestige)
        user.setCustomData("xp", 0)
        user.setCustomData("level", 1)
        return nextPrestige
    }

    suspend fun removeXP(user: EverlandsUser, amount: Int) {
        var currentXP = user.xp
        var currentLevel = user.level
        if (currentLevel <= 1 && currentXP <= 0) {
            return
        }
        currentXP -= amount
        while (currentLevel > 1) {
            val xpForCurrentLevel = getTotalXPForLevel(currentLevel)
            if (currentXP < xpForCurrentLevel) {
                currentLevel--
            } else {
                break
            }
        }
        if (currentXP < 0) {
            currentXP = 0
        }
        user.setCustomData("xp", currentXP)
        user.setCustomData("level", currentLevel)
    }

    suspend fun addXP(user: EverlandsUser, amount: Int) {
        val maxLevel = config.value.maxLevel
        var currentXP = user.xp
        val initialLevel = user.level
        var currentLevel = user.level
        if (currentLevel >= maxLevel) {
            return
        }
        currentXP += amount
        while (currentLevel < maxLevel) {
            val xpForNextLevel = getTotalXPForLevel(currentLevel + 1)
            if (currentXP >= xpForNextLevel) {
                currentLevel++
            } else {
                break
            }
        }
        if (currentLevel > initialLevel) {
            val playerRef = user.playerRef
            if (playerRef != null) {
                playerRef.play2DSound("SFX_Level_Up")
                val msgContext =
                    MessageContext(player = playerRef, variables = mapOf("level" to currentLevel.toString()))
                chatModule.sendConfigurableMessage(playerRef, msgContext, config.value.levelUpMessage)
            }

        }
        user.setCustomData("xp", currentXP)
        user.setCustomData("level", currentLevel)
    }


}

val EverlandsUser.level get() = customData.getInt("level") ?: 1
val EverlandsUser.prestige get() = customData.getInt("prestige") ?: 0
val EverlandsUser.xp get() = customData.getInt("xp") ?: 0
val EverlandsUser.xpForNextLevel get() = getTotalXPForLevel(level + 1)

val EverlandsUser.xpForNextLevelPercentage: Float
    get() {
        val currentLevelTotal = getTotalXPForLevel(level)
        val nextLevelTotal = getTotalXPForLevel(level + 1)

        val progressNumerator = (xp - currentLevelTotal).toFloat()
        val progressDenominator = (nextLevelTotal - currentLevelTotal).toFloat()

        return if (progressDenominator <= 0f) 1.0f
        else (progressNumerator / progressDenominator).coerceIn(0.0f, 1.0f)
    }

fun getXPIncrement(level: Int) = 65 * level.toDouble().pow(1.6) + 4500

fun getTotalXPForLevel(level: Int): Int {
    var totalXP = 0.0
    for (i in 1 until level) {
        totalXP += getXPIncrement(i)
    }
    return totalXP.toInt()
}
