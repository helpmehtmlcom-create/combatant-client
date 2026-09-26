/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(
        id = "autowalk",
        displayName = "AutoWalk",
        aliases = {"AutoForward", "ForwardWalk"},
        category = ModuleCategory.MOVEMENT,
        description = "Automatically walks forward continuously."
)
public final class AutoWalk extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("autoWalkMode", "mode", Mode.INPUT, Mode.values());

    private final BooleanValue autoJump =
            bool("autoWalkAutoJump", "auto_jump", false);

    @Override
    public void onDisable() {
        if (mode.get() == Mode.KEY_PRESS && mc.options != null) {
            mc.options.keyUp.setDown(false);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (mode.get() == Mode.KEY_PRESS && mc.options != null) {
            mc.options.keyUp.setDown(true);

            if (autoJump.get() && mc.player != null && mc.player.horizontalCollision && mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
        }
    }

    @EventHandler
    public void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || mode.get() != Mode.INPUT) return;

        event.setForward(true);

        LocalPlayer player = mc.player;
        if (autoJump.get() && player != null && player.horizontalCollision && player.onGround()) {
            event.setJump(true);
        }
    }

    public enum Mode {
        INPUT,
        KEY_PRESS
    }
}
