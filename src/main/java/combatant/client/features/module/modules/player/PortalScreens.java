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
import combatant.client.features.module.ModuleSubcategory;

@ModuleInfo(
        id = "portalscreens",
        displayName = "PortalScreens",
        aliases = {"PortalChat", "PortalGui"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.portalscreens.description")
public final class PortalScreens extends Module {
    @Override
    public String getConfigName() {
        return "portalchat";
    }
}
