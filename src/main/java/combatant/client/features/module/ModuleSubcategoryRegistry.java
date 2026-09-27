/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime registry that merges Combatant-owned and addon-owned module subcategories. */
public enum ModuleSubcategoryRegistry {
    ;

    private static final EnumMap<ModuleCategory, List<ModuleSubcategoryDefinition>> BUILT_INS =
            new EnumMap<>(ModuleCategory.class);
    private static final LinkedHashMap<String, RegisteredSubcategory> ADDON_DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Module, String> MODULE_ASSIGNMENTS = new IdentityHashMap<>();

    static {
        for (ModuleCategory category : ModuleCategory.values()) {
            List<ModuleSubcategoryDefinition> values = new ArrayList<>();
            for (ModuleSubcategory subcategory : ModuleSubcategory.forCategory(category)) {
                values.add(new ModuleSubcategoryDefinition(
                        builtInKey(subcategory.id()), category, subcategory.id(), subcategory.displayName()));
            }
            BUILT_INS.put(category, List.copyOf(values));
        }
    }

    public static synchronized boolean registerAddon(String addonId,
                                                       ModuleCategory category,
                                                       String id,
                                                       String displayName) {
        String owner = normalizeId(addonId);
        String localId = normalizeId(id);
        String label = displayName == null ? "" : displayName.trim();
        if (owner.isEmpty() || localId.isEmpty() || category == null || label.isEmpty()) return false;

        String key = addonKey(owner, category, localId);
        RegisteredSubcategory existing = ADDON_DEFINITIONS.get(key);
        if (existing != null) {
            return existing.definition().category() == category
                    && existing.definition().displayName().equals(label);
        }

        ADDON_DEFINITIONS.put(key, new RegisteredSubcategory(
                owner,
                new ModuleSubcategoryDefinition(key, category, localId, label)
        ));
        return true;
    }

    public static synchronized boolean assignAddonModule(String addonId, Module module, String subcategoryId) {
        if (module == null) return false;
        String owner = normalizeId(addonId);
        String localId = normalizeId(subcategoryId);
        if (owner.isEmpty() || localId.isEmpty()) return false;

        String key = addonKey(owner, module.getCategory(), localId);
        RegisteredSubcategory registered = ADDON_DEFINITIONS.get(key);
        if (registered == null || registered.definition().category() != module.getCategory()) return false;

        MODULE_ASSIGNMENTS.put(module, key);
        return true;
    }

    public static synchronized List<ModuleSubcategoryDefinition> forCategory(ModuleCategory category) {
        if (category == null) return List.of();
        List<ModuleSubcategoryDefinition> out = new ArrayList<>(BUILT_INS.getOrDefault(category, List.of()));
        for (RegisteredSubcategory registered : ADDON_DEFINITIONS.values()) {
            if (registered.definition().category() == category) out.add(registered.definition());
        }
        return List.copyOf(out);
    }

    public static synchronized ModuleSubcategoryDefinition resolve(Module module) {
        if (module == null) return null;

        String assignedKey = MODULE_ASSIGNMENTS.get(module);
        if (assignedKey != null) {
            RegisteredSubcategory custom = ADDON_DEFINITIONS.get(assignedKey);
            if (custom != null && custom.definition().category() == module.getCategory()) {
                return custom.definition();
            }
        }

        ModuleSubcategory builtIn = module.getEffectiveSubcategory();
        String key = builtInKey(builtIn.id());
        for (ModuleSubcategoryDefinition definition : BUILT_INS.getOrDefault(module.getCategory(), List.of())) {
            if (definition.key().equals(key)) return definition;
        }

        List<ModuleSubcategoryDefinition> fallback = BUILT_INS.getOrDefault(module.getCategory(), List.of());
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    public static synchronized boolean isRegisteredAddonSubcategory(String addonId,
                                                                     ModuleCategory category,
                                                                     String id) {
        String owner = normalizeId(addonId);
        String localId = normalizeId(id);
        RegisteredSubcategory registered = ADDON_DEFINITIONS.get(addonKey(owner, category, localId));
        return registered != null && registered.definition().category() == category;
    }

    private static String builtInKey(String id) {
        return "combatant:" + normalizeId(id);
    }

    private static String addonKey(String addonId, ModuleCategory category, String id) {
        String categoryId = category == null ? "unknown" : category.name().toLowerCase(Locale.ROOT);
        return normalizeId(addonId) + ":" + categoryId + ":" + normalizeId(id);
    }

    private static String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!valid) return "";
        }
        return normalized;
    }

    private record RegisteredSubcategory(String addonId, ModuleSubcategoryDefinition definition) {
    }
}
