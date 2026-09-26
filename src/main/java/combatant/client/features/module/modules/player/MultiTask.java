/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "multitask",
        displayName = "MultiTask",
        aliases = {"SimultaneousActions", "DualAction"},
        category = ModuleCategory.PLAYER,
        description = "Allows attacking, mining, and placing blocks while eating or using items."
)
public final class MultiTask extends Module {
}
