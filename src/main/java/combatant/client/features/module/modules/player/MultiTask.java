/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "multitask",
        displayName = "MultiTask",
        category = ModuleCategory.PLAYER
)
public final class MultiTask extends Module {

    private final BooleanValue mining = bool("multitaskMining", "mining", true);
    private final BooleanValue attacking = bool("multitaskAttacking", "attacking", true);

    public boolean canMineWhileUsing() {
        return isEnabled() && mining.get();
    }

    public boolean canAttackWhileUsing() {
        return isEnabled() && attacking.get();
    }
}
