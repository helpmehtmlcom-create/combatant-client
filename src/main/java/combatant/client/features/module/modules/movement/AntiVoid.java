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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "antivoid",
        displayName = "AntiVoid",
        aliases = {"VoidSafe", "VoidBlink"},
        category = ModuleCategory.MOVEMENT,
        description = "Saves you from falling into the void in the End or void worlds."
)
public final class AntiVoid extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("antiVoidMode", "mode", Mode.BOUNCE, Mode.values());

    private final NumberValue<Float> triggerHeight =
            num("antiVoidTriggerHeight", "trigger_height", 0.0f, -64.0f, 64.0f);

    private final NumberValue<Float> bounceForce =
            num("antiVoidBounceForce", "bounce_force", 1.5f, 0.5f, 5.0f);

    private final BooleanValue voidOnly =
            bool("antiVoidVoidOnly", "void_only", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (player.onGround()
                || player.isFallFlying()
                || player.getAbilities().flying
                || player.isPassenger()) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y >= 0.0) {
            return;
        }

        if (player.getY() > triggerHeight.get()) {
            return;
        }

        if (voidOnly.get() && hasBlockBelow(player)) {
            return;
        }

        switch (mode.get()) {
            case BOUNCE -> player.setDeltaMovement(velocity.x, bounceForce.get(), velocity.z);
            case GLIDE -> player.setDeltaMovement(velocity.x, -0.05, velocity.z);
            case FREEZE -> player.setDeltaMovement(0, 0, 0);
        }
    }

    private boolean hasBlockBelow(LocalPlayer player) {
        if (mc.level == null) return false;

        int playerX = player.getBlockX();
        int playerZ = player.getBlockZ();
        int startY = (int) Math.floor(player.getY());
        int minY = mc.level.getMinY();

        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(playerX, y, playerZ);
            if (!mc.level.getBlockState(pos).isAir()) {
                return true;
            }
        }

        return false;
    }

    public enum Mode {
        BOUNCE,
        GLIDE,
        FREEZE
    }
}
