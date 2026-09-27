/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional second-level grouping used by module browsers.
 *
 * <p>The main {@link ModuleCategory} remains the ownership/category contract. A subcategory is
 * presentation metadata: addons may omit it and browsers must fall back to the category default.
 * Values are category-scoped so accidentally assigning (for example) {@link #ESP} to a combat
 * module cannot make that module disappear from the UI.</p>
 */
public enum ModuleSubcategory {
    UNSPECIFIED(null, "", ""),

    ATTACK(ModuleCategory.COMBAT, "attack", "Attack"),
    LEGIT(ModuleCategory.COMBAT, "legit", "Legit"),
    PROTECT(ModuleCategory.COMBAT, "protect", "Protect"),

    BASIC(ModuleCategory.MOVEMENT, "basic", "Basic"),
    RAGE(ModuleCategory.MOVEMENT, "rage", "Rage"),

    EXPLOIT(ModuleCategory.PLAYER, "exploit", "Exploit"),
    AUTOMATION(ModuleCategory.PLAYER, "automation", "Automation"),

    COSMETIC(ModuleCategory.VISUALS, "cosmetic", "Cosmetic"),
    ESP(ModuleCategory.VISUALS, "esp", "ESP"),

    UTILITY(ModuleCategory.MISC, "utility", "Utility"),
    CLIENT(ModuleCategory.MISC, "client", "Client");

    private final ModuleCategory category;
    private final String id;
    private final String displayName;

    ModuleSubcategory(ModuleCategory category, String id, String displayName) {
        this.category = category;
        this.id = id;
        this.displayName = displayName;
    }

    public ModuleCategory category() {
        return category;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean belongsTo(ModuleCategory category) {
        return this != UNSPECIFIED && this.category == category;
    }

    public static ModuleSubcategory fallbackFor(ModuleCategory category) {
        if (category == null) return UNSPECIFIED;
        return switch (category) {
            case COMBAT -> ATTACK;
            case MOVEMENT -> BASIC;
            case PLAYER -> AUTOMATION;
            case VISUALS -> COSMETIC;
            case MISC -> UTILITY;
        };
    }

    public static ModuleSubcategory effective(ModuleCategory category, ModuleSubcategory requested) {
        return requested != null && requested.belongsTo(category) ? requested : fallbackFor(category);
    }

    public static List<ModuleSubcategory> forCategory(ModuleCategory category) {
        if (category == null) return List.of();
        List<ModuleSubcategory> out = new ArrayList<>();
        for (ModuleSubcategory value : values()) {
            if (value.belongsTo(category)) out.add(value);
        }
        return List.copyOf(out);
    }
}
