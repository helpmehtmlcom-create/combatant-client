/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "longjump",
        displayName = "LongJump",
        aliases = {"FarJump", "Leap"},
        category = ModuleCategory.MOVEMENT,
        description = "Significantly increases horizontal leap distance when jumping."
)
public final class LongJump extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> boost =
            num("longJumpBoost", "boost", 1.8f, 1.1f, 4.0f);

    private final BooleanValue autoHigh =
            bool("longJumpAutoHigh", "auto_high", false);

    private final NumberValue<Float> highBoost =
            visibleWhen(num("longJumpHighBoost", "high_boost", 1.2f, 1.0f, 2.5f), autoHigh::get);

    private final BooleanValue autoDisable =
            bool("longJumpAutoDisable", "auto_disable", false);

    private boolean jumped = false;

    @Override
    public void onDisable() {
        jumped = false;
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        Vec3 v = player.getDeltaMovement();
        float b = boost.get();
        double my = autoHigh.get() ? v.y * highBoost.get() : v.y;

        player.setDeltaMovement(v.x * b, my, v.z * b);
        jumped = true;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player != null && jumped && player.onGround()) {
            jumped = false;
            if (autoDisable.get()) {
                setEnabled(false);
            }
        }
    }
}
