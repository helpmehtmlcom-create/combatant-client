/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "antiafk",
        displayName = "AntiAFK",
        aliases = {"AfkBot", "NoAfk"},
        category = ModuleCategory.MISC,
        description = "Performs periodic actions and head movements to prevent AFK kicks."
)
public final class AntiAFK extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> intervalSeconds =
            num("antiAfkIntervalSeconds", "interval_seconds", 30, 5, 120);

    private final BooleanValue rotate =
            bool("antiAfkRotate", "rotate", true);

    private final BooleanValue jump =
            bool("antiAfkJump", "jump", true);

    private final BooleanValue swing =
            bool("antiAfkSwing", "swing", true);

    private int timerTicks = 0;

    @Override
    public void onDisable() {
        timerTicks = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            timerTicks = 0;
            return;
        }

        timerTicks++;
        int maxTicks = intervalSeconds.get() * 20;

        if (timerTicks >= maxTicks) {
            timerTicks = 0;

            if (rotate.get()) {
                float deltaYaw = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 60.0f;
                player.setYRot(player.getYRot() + deltaYaw);
            }

            if (jump.get() && player.onGround()) {
                player.jumpFromGround();
            }

            if (swing.get()) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
