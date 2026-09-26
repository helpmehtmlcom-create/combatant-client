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
        id = "portalchat",
        displayName = "PortalChat",
        aliases = {"PortalGui", "PortalScreens"},
        category = ModuleCategory.PLAYER,
        description = "Allows opening chat, inventory, and GUIs while inside Nether and End portals."
)
public final class PortalChat extends Module {
}
