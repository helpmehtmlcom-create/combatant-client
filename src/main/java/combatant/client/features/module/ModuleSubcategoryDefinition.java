/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import java.util.Objects;

/**
 * Stable runtime descriptor used by module browsers for second-level grouping.
 *
 * <p>The {@code key} is globally unique ({@code combatant:attack},
 * {@code addon_id:visuals:custom_group}, ...), while {@code id} stays local to the owner namespace.
 * Built-in enum values and addon-defined groups therefore share one UI contract without forcing
 * addons to modify Combatant's enum.</p>
 */
public record ModuleSubcategoryDefinition(
        String key,
        ModuleCategory category,
        String id,
        String displayName
) {
    public ModuleSubcategoryDefinition {
        key = Objects.requireNonNull(key, "key");
        category = Objects.requireNonNull(category, "category");
        id = Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName");
    }
}
