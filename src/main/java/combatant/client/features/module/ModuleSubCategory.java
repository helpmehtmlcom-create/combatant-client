/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import java.util.List;
import java.util.Locale;

/**
 * Sub-categories (tabs) for organizing modules within their main {@link ModuleCategory}.
 * Inspired by CatLean and BlackOut-CE architectures, optimized for Combatant Client.
 */
public enum ModuleSubCategory {
    ALL("All"),

    // Combat tabs
    OFFENSE("Offence"),
    DEFENSE("Defence"),
    LEGIT("Legit"),

    // Visuals & Utility tabs
    NORMAL("Normal"),
    DONUTSMP("DonutSMP");

    private final String title;

    ModuleSubCategory(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /**
     * Returns the available sub-categories (tabs) for a given parent {@link ModuleCategory}.
     */
    public static List<ModuleSubCategory> getSubCategoriesFor(ModuleCategory category) {
        if (category == null) return List.of(ALL);
        return switch (category) {
            case COMBAT -> List.of(OFFENSE, DEFENSE, LEGIT);
            case VISUALS -> List.of(NORMAL, DONUTSMP);
            case PLAYER -> List.of(NORMAL, DONUTSMP);
            case MISC -> List.of(NORMAL, DONUTSMP);
            case MOVEMENT -> List.of(NORMAL, LEGIT);
        };
    }

    /**
     * Resolves the effective sub-category of a module.
     * If explicitly declared, that is returned; otherwise, infers the sub-category
     * intelligently based on module name, category, and purpose.
     */
    public static ModuleSubCategory resolve(Module module) {
        if (module == null) return NORMAL;
        ModuleSubCategory declared = module.getDeclaredSubCategory();
        if (declared != null && declared != ALL) {
            return declared;
        }

        ModuleCategory cat = module.getCategory();
        if (cat == null) return NORMAL;

        String id = module.name() != null ? module.name().toLowerCase(Locale.ROOT) : "";

        return switch (cat) {
            case COMBAT -> resolveCombat(id);
            case VISUALS -> resolveVisuals(id);
            case PLAYER -> resolvePlayer(id);
            case MISC -> resolveMisc(id);
            case MOVEMENT -> resolveMovement(id);
        };
    }

    private static ModuleSubCategory resolveCombat(String id) {
        // Legit combat
        if (id.contains("trigger") || id.contains("aimassist") || id.contains("jumpreset")
                || id.contains("reach") || id.contains("clicker") || id.contains("spearassist")
                || id.contains("hitbox")) {
            return LEGIT;
        }
        // Defensive combat
        if (id.contains("surround") || id.contains("blocker") || id.contains("holefill")
                || id.contains("antibed") || id.contains("anticev") || id.contains("selftrap")
                || id.contains("burrow") || id.contains("shield") || id.contains("trap")
                || id.contains("rubberhand") || id.contains("pvpcooldown") || id.contains("tpssync")
                || id.contains("antitotem")) {
            return DEFENSE;
        }
        // Offensive combat (default)
        return OFFENSE;
    }

    private static ModuleSubCategory resolveVisuals(String id) {
        if (id.contains("donut") || id.contains("chunkradar") || id.contains("shulkerviewer")
                || id.contains("stashfinder") || id.contains("basefinder") || id.contains("spawneresp")) {
            return DONUTSMP;
        }
        return NORMAL;
    }

    private static ModuleSubCategory resolvePlayer(String id) {
        if (id.contains("autosell") || id.contains("storagestealer") || id.contains("spawnerdrop")
                || id.contains("echestfarmer") || id.contains("shitdropper")) {
            return DONUTSMP;
        }
        return NORMAL;
    }

    private static ModuleSubCategory resolveMisc(String id) {
        if (id.contains("staffalert") || id.contains("stashfinder") || id.contains("panic")) {
            return DONUTSMP;
        }
        return NORMAL;
    }

    private static ModuleSubCategory resolveMovement(String id) {
        if (id.contains("safewalk") || id.contains("parkour") || id.contains("reversestep")) {
            return LEGIT;
        }
        return NORMAL;
    }
}
