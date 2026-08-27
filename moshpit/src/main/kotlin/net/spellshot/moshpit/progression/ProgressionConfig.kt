package net.spellshot.moshpit.progression

import net.spellshot.core.framework.Config
import net.spellshot.core.framework.ConfigurableMessage
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.spellshot.moshpit.loadout.LoadoutItemType
import org.springframework.stereotype.Component

@Serializable
@SerialName("minigame.ProgressionConfig")
data class ProgressionConfig(
    val maxLevel: Int = 100,
    val maxPrestige: Int = 10,
    val levelUpMessage: ConfigurableMessage = ConfigurableMessage("You have reached level {level}!"),
    // requiredLevel gates each item in the loadout menu. Level 1 is the starting
    // level (see the `level` extension), so anything at 0 or 1 is unlocked from
    // the outset and each category keeps one always-available option.
    val items: Map<LoadoutItemType, List<ItemProgressionConfig>> = mapOf(
        LoadoutItemType.PRIMARY to listOf(
            ItemProgressionConfig("AR14", 0),
            ItemProgressionConfig("Famas", 12),
            ItemProgressionConfig("Thompson", 10),
            ItemProgressionConfig("FAMP10", 18),
            // The M40 fires with Revolver_Shoot rather than an interaction of its
            // own -- 150 head, 85 chest, 80 blocks -- so the two guns are welded
            // together and retuning either one moves both. What makes it a sniper
            // rather than a second revolver is its two-round magazine and the 10x
            // scope on Secondary, not its damage. Give it M40_Shoot before touching
            // those numbers.
            ItemProgressionConfig("M40", 26),
            ItemProgressionConfig("Remington870", 34),
            ItemProgressionConfig("DoubleBarrel", 42)
        ),
        LoadoutItemType.SECONDARY to listOf(
            ItemProgressionConfig("M1911", 0),
            ItemProgressionConfig("Revolver", 5)
        ),
        LoadoutItemType.MELEE to listOf(
            ItemProgressionConfig("CombatKnife", 0)
        ),
        // RequestAirstrike is deliberately absent: it is a killstreak reward
        // (see KillStreakConfig), not something you can equip from spawn.
        LoadoutItemType.UTILITY to listOf(
            ItemProgressionConfig("FragGrenade", 0),
            ItemProgressionConfig("ImpactGrenade", 8),
            ItemProgressionConfig("Syringe", 16)
        ),
        // One in the Chamber picks from here and nowhere else. Both are unlocked
        // from level 1 on purpose: OitC is meant to put everyone on equal terms, so
        // gating the sniper behind a level would hand veterans a scope in the one
        // mode built on nobody having an advantage. It is a choice, not a reward --
        // four shots and no scope, or two shots and a 10x.
        LoadoutItemType.OITC to listOf(
            ItemProgressionConfig("Revolver", 0),
            ItemProgressionConfig("M40", 0)
        )
    ),
) {
    @Component
    class Binder : Config<ProgressionConfig>(ProgressionConfig())
}

@Serializable
data class ItemProgressionConfig(
    val itemId: String,
    @Description("The level required to unlock this item.")
    val requiredLevel: Int = 0,
    val skins: List<ItemSkinProgressionConfig> = emptyList(),
)

@Serializable
data class ItemSkinProgressionConfig(
    val itemId: String = "Item",
    val name: String,
    @Description("The level required to unlock this skin.")
    val requiredLevel: Int = 0,
    @Description("The prestige required to unlock this skin.")
    val requiredPrestige: Int = 0,
)

