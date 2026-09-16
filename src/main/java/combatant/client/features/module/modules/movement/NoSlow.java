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

    private final BooleanValue items = bool("noslowItems", "items", true);
    private final EnumValue<WebMode> webs = enumSetting("noslowWebs", "webs", WebMode.VANILLA, WebMode.values());
    private final BooleanValue soulSand = bool("noslowSoulSand", "soul_sand", true);
    private final BooleanValue honey = bool("noslowHoney", "honey", true);
    private final BooleanValue slime = bool("noslowSlime", "slime", true);
    private final NumberValue<Float> forwardFactor = num("noslowForwardFactor", "forward_factor", 1.0f, 0.2f, 1.0f);
    private final NumberValue<Float> strafeFactor = num("noslowStrafeFactor", "strafe_factor", 1.0f, 0.2f, 1.0f);

    public boolean shouldCancelItemSlowdown() {
        return isEnabled() && items.get();
    }

    public Vec2 modifyItemInput(Vec2 input) {
        if (!shouldCancelItemSlowdown()) {
            return input;
        }

        float f = forwardFactor.get();
        float s = strafeFactor.get();
        return new Vec2(input.x * s, input.y * f);
    }

    public WebMode getWebMode() {
        return isEnabled() ? webs.get() : WebMode.VANILLA;
    }

    public boolean shouldCancelSoulSand() {
        return isEnabled() && soulSand.get();
    }

    public boolean shouldCancelHoney() {
        return isEnabled() && honey.get();
    }

    public boolean shouldCancelSlime() {
        return isEnabled() && slime.get();
    }

    public enum WebMode {
        VANILLA,
        OFF,
        FAST
    }
}
