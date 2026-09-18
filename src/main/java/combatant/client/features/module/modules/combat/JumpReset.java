/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "jumpreset",
        displayName = "JumpReset",
        description = "Legit knockback reduction technique for DonutSMP. Jumps on the exact tick damage is received to cancel incoming velocity.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.LEGIT,
        aliases = {"jumpvelo", "legitvelo", "kbreset"}
)
public class JumpReset extends Module {

    private final NumberValue<Integer> chance =
            num("jumpreset_chance", "chance", 100, 1, 100);
    private final NumberValue<Integer> delayTicks =
            num("jumpreset_delay", "delay", 0, 0, 3);
    private final BooleanValue onlyMoving =
            bool("jumpreset_only_moving", "only_moving", true);
    private final BooleanValue onlyOnGround =
            bool("jumpreset_only_ground", "only_ground", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int pendingJumpTicks = -1;
    private int lastHurtTime = 0;

    @Override
    public void onEnable() {
        pendingJumpTicks = -1;
        lastHurtTime = 0;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        int currentHurt = player.hurtTime;
        // Detect the exact initial tick of damage (typically hurtTime starts at 9 or 10)
        if (currentHurt > 0 && currentHurt > lastHurtTime) {
            if (ThreadLocalRandom.current().nextInt(100) < chance.get()) {
                if (delayTicks.get() <= 0) {
                    executeJump(player);
                } else {
                    pendingJumpTicks = delayTicks.get();
                }
            }
        }
        lastHurtTime = currentHurt;

        if (pendingJumpTicks > 0) {
            pendingJumpTicks--;
            if (pendingJumpTicks == 0) {
                executeJump(player);
                pendingJumpTicks = -1;
            }
        }
    }

    private void executeJump(LocalPlayer player) {
        if (onlyOnGround.get() && !player.onGround()) return;
        if (onlyMoving.get() && !isMoving(player)) return;

        player.jumpFromGround();
    }

    private boolean isMoving(LocalPlayer player) {
        return player.input != null
                && (player.input.keyPresses.forward()
                || player.input.keyPresses.backward()
                || player.input.keyPresses.left()
                || player.input.keyPresses.right());
    }
}
