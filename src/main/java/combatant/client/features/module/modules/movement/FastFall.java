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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "fastfall",
        displayName = "FastFall",
        aliases = {"QuickFall", "FastDrop"},
        category = ModuleCategory.MOVEMENT,
        description = "Accelerates downward velocity when falling off ledges to land quickly."
)
public final class FastFall extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("fastFallMode", "mode", Mode.MOTION, Mode.values());

    private final NumberValue<Float> fallSpeed =
            num("fastFallSpeed", "speed", 2.5f, 0.5f, 10.0f);

    private final NumberValue<Float> maxDistance =
            num("fastFallMaxDistance", "max_distance", 3.5f, 1.0f, 10.0f);

    private final BooleanValue avoidVoid =
            bool("fastFallAvoidVoid", "avoid_void", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (player.onGround()
                || player.isFallFlying()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger()
                || player.getAbilities().flying) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y >= 0.0) {
            return;
        }

        Vec3 start = player.position();
        Vec3 end = start.add(0, -maxDistance.get() - 1.0, 0);

        HitResult hit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            if (avoidVoid.get()) {
                return;
            }
        }

        double groundY = hit.getLocation().y;
        double dist = player.getY() - groundY;
        if (dist > maxDistance.get() || dist <= 0.2) {
            return;
        }

        switch (mode.get()) {
            case MOTION -> player.setDeltaMovement(velocity.x, -fallSpeed.get(), velocity.z);
            case STEP -> player.setDeltaMovement(velocity.x, Math.min(velocity.y, -fallSpeed.get()), velocity.z);
        }
    }

    public enum Mode {
        MOTION,
        STEP
    }
}
