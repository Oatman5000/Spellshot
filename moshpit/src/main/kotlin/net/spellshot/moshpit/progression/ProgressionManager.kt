package net.spellshot.moshpit.progression

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.spellshot.core.framework.ChatModule
import net.spellshot.core.framework.MessageContext
import net.spellshot.core.framework.SpellshotUser
import net.spellshot.core.ext.play2DSound
import net.spellshot.moshpit.item.skin.PermissionSkinsProvider
import net.spellshot.moshpit.item.skin.SkinProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.pow

@Service
class ProgressionManager(
    val config: ProgressionConfig.Binder, val skinProviders: List<SkinProvider>,
    val experienceConfig: ExperienceConfig.Binder, val chatModule: ChatModule
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun canPrestigeLevelUp(user: SpellshotUser): Boolean {
        val config = config.value
        return user.level >= config.maxLevel && user.prestige < config.maxPrestige
    }

    /**
     * Every skin available to a player for one item.
     *
     * This type-switches on the provider rather than asking it, which is backwards:
     * a provider knows how to describe its own skins, so `provide` returning
     * `ItemSkinProgressionConfig` directly would delete this branch entirely.
     *
     * The concrete hazard is the missing `else`. A third provider type silently
     * contributes nothing -- no error, no log, its skins just never appear in the
     * menu. Two providers exist today, so this is latent rather than live, but it
     * fails quietly if that changes.
     */
    fun getSkins(user: SpellshotUser, playerRef: PlayerRef, itemId: String): List<ItemSkinProgressionConfig> {
        return buildList {
            for (provider in skinProviders) {
                val skins = provider.provide(user, playerRef, itemId)
                when (provider) {
                    // Progression skins are described by config, keyed on item id.
                    is ProgressionSkinProvider -> for (skin in skins) {
                        val config = getSkinConfig(skin.itemId) ?: continue
                        add(config)
                    }
                    // Permission skins have no unlock requirement, hence 0/0.
                    is PermissionSkinsProvider -> for (skin in skins) {
                        add(ItemSkinProgressionConfig(skin.itemId, skin.name, 0, 0))
                    }
                    else -> if (skins.isNotEmpty()) logger.warn(
                        "Skin provider {} returned {} skins that cannot be described; they will not be shown",
                        provider::class.simpleName, skins.size
                    )
                }
            }
        }
    }

    fun getSkinParentItemId(user: SpellshotUser, skinId: String): String? {
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

    suspend fun addPrestige(user: SpellshotUser): Int {
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

    suspend fun removeXP(user: SpellshotUser, amount: Int) {
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

    suspend fun addXP(user: SpellshotUser, amount: Int) {
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

val SpellshotUser.level get() = customData.getInt("level") ?: 1
val SpellshotUser.prestige get() = customData.getInt("prestige") ?: 0
val SpellshotUser.xp get() = customData.getInt("xp") ?: 0
val SpellshotUser.xpForNextLevel get() = getTotalXPForLevel(level + 1)

val SpellshotUser.xpForNextLevelPercentage: Float
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
