/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "autowalk",
        displayName = "AutoWalk",
        category = ModuleCategory.MOVEMENT,
        description = "Automatically walks forward with options for direction locking and highway alignment."
)
public final class AutoWalk extends Module {

    public enum Mode {
        SIMPLE,
        DIRECTION_LOCK,
        HIGHWAY
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.DIRECTION_LOCK);
    private final BooleanValue autoJump = bool("auto_jump", true);
    private final BooleanValue autoUnstick = bool("auto_unstick", true);
    private final BooleanValue lockDiagonal = bool("lock_diagonal", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int stuckTicks = 0;

    @Override
    public void onEnable() {
        stuckTicks = 0;
        if (mc.player != null && (mode.get() == Mode.DIRECTION_LOCK || mode.get() == Mode.HIGHWAY)) {
            lockPlayerDirection();
        }
    }

    @Override
    public void onDisable() {
        stuckTicks = 0;
        if (mc.options != null && mc.options.keyUp != null) {
            mc.options.keyUp.setDown(false);
        }
    }

    @EventHandler
    public void onInput(MovementInputEvent event) {
        if (!isEnabled()) return;
        event.setForward(true);

        if (autoJump.get() && mc.player != null && mc.player.horizontalCollision && mc.player.onGround()) {
            event.setJump(true);
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        // Direction locking
        if (mode.get() == Mode.DIRECTION_LOCK || mode.get() == Mode.HIGHWAY) {
            lockPlayerDirection();
        }

        // Highway auto obstacle detection
        if (mode.get() == Mode.HIGHWAY && player.onGround()) {
            BlockPos inFront = player.blockPosition().relative(player.getDirection());
            BlockState state = mc.level.getBlockState(inFront);
            BlockState above = mc.level.getBlockState(inFront.above());
            if (!state.isAir() && state.isSolidRender() && above.isAir()) {
                player.jumpFromGround();
            }
        }

        // Auto unstick
        if (autoUnstick.get()) {
            if (player.getDeltaMovement().horizontalDistanceSqr() < 0.001 && player.onGround()) {
                stuckTicks++;
                if (stuckTicks > 15) {
                    player.jumpFromGround();
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
        }
    }

    private void lockPlayerDirection() {
        if (mc.player == null) return;
        float currentYaw = mc.player.getYRot();
        float targetYaw;

        if (lockDiagonal.get()) {
            targetYaw = Math.round(currentYaw / 45.0f) * 45.0f;
        } else {
            targetYaw = Math.round(currentYaw / 90.0f) * 90.0f;
        }

        mc.player.setYRot(targetYaw);
    }
}
