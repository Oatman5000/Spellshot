package net.spellshot.moshpit.loadout

enum class LoadoutItemType(val displayName: String, val titleText: String, val imagePath: String) {
    PRIMARY("Primary", "Select Primary Gun", "Images/Guns/"),
    SECONDARY("Secondary", "Select Secondary Gun", "Images/Guns/"),
    MELEE("Melee", "Select Melee Weapon", "Images/Melee/"),
    UTILITY("Utility", "Select Utility Item", "Images/Utility/");
}
