/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "noslow",
        displayName = "NoSlow",
        category = ModuleCategory.MOVEMENT,
        aliases = {"noslowdown"},
        description = "Prevents movement slowdown while using items, webs, soul sand, or sneaking."
)
public final class NoSlow extends Module {

    private final BooleanValue items = bool("items", true);
    private final BooleanValue webs = bool("webs", true);
    private final BooleanValue terrain = bool("terrain", true);

    public boolean shouldCancelItemSlowdown() {
        return isEnabled() && items.get();
    }

    public Vec2 modifyItemInput(Vec2 input) {
        if (!shouldCancelItemSlowdown()) {
            return input;
        }
        return input;
    }

    public WebMode getWebMode() {
        if (!isEnabled()) return WebMode.VANILLA;
        return webs.get() ? WebMode.FAST : WebMode.VANILLA;
    }

    public boolean shouldCancelSoulSand() {
        return isEnabled() && terrain.get();
    }

    public boolean shouldCancelHoney() {
        return isEnabled() && terrain.get();
    }

    public boolean shouldCancelSlime() {
        return isEnabled() && terrain.get();
    }

    public boolean shouldCancelBerryBush() {
        return isEnabled() && terrain.get();
    }

    public enum WebMode {
        VANILLA,
        OFF,
        FAST
    }
}
