package net.spellshot.moshpit.loadout

/**
 * A row in the loadout editor.
 *
 * [displayName] is not cosmetic: it is spliced into CustomUI selectors, so it must
 * match an element id in Edit.ui (`#Edit #<displayName> #Button`) and in the
 * selection entry files (`#<displayName>Icon`). Adding a value here without adding
 * those elements makes the client fail to resolve the selector, which drops the
 * connection rather than logging anything useful.
 */
enum class LoadoutItemType(val displayName: String, val titleText: String, val imagePath: String) {
    PRIMARY("Primary", "Select Primary Gun", "Images/Guns/"),
    SECONDARY("Secondary", "Select Secondary Gun", "Images/Guns/"),
    MELEE("Melee", "Select Melee Weapon", "Images/Melee/"),
    UTILITY("Utility", "Select Utility Item", "Images/Utility/"),

    /**
     * One in the Chamber only. Every other mode ignores it -- the spawn kit in
     * LoadoutService names the four slots it hands out one by one, deliberately, so
     * this one cannot leak into FFA as a free extra gun.
     */
    OITC("Oitc", "Select One in the Chamber Gun", "Images/Guns/");
}
