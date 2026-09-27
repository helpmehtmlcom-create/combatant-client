/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;

@ModuleInfo(
        id = "freeze",
        displayName = "Freeze",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.freeze.description")
public final class Freeze extends Module {

    public Freeze() {
        setDefaultBind("COMMA");
    }
}
