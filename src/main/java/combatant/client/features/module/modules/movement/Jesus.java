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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventCollision;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "jesus",
        displayName = "Jesus",
        aliases = {"WaterWalk", "LiquidWalk", "Dolphin"},
        category = ModuleCategory.MOVEMENT,
        description = "Allows walking on water and lava surfaces as if they were solid blocks."
)
public final class Jesus extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("jesusMode", "mode", Mode.SOLID, Mode.values());

    private final EnumValue<LiquidType> liquidType =
            enumSetting("jesusLiquidType", "liquid_type", LiquidType.BOTH, LiquidType.values());

    private final NumberValue<Float> upSpeed =
            num("jesusUpSpeed", "up_speed", 0.12f, 0.05f, 0.5f);

    private final BooleanValue sneakSink =
            bool("jesusSneakSink", "sneak_sink", true);

    @EventHandler
    public void onCollision(EventCollision event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (sneakSink.get() && mc.options.keyShift.isDown()) {
            return;
        }

        if (player.fallDistance > 3.0f) {
            return;
        }

        BlockState state = event.getState();
        if (isTargetLiquid(state)) {
            BlockPos pos = event.getPos();
            if (pos.getY() < player.getY()) {
                if (mode.get() == Mode.SOLID) {
                    event.setState(Blocks.BARRIER.defaultBlockState());
                }
            }
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (sneakSink.get() && mc.options.keyShift.isDown()) {
            return;
        }

        boolean inWater = player.isInWater();
        boolean inLava = player.isInLava();

        boolean inTarget = switch (liquidType.get()) {
            case BOTH -> inWater || inLava;
            case WATER_ONLY -> inWater;
            case LAVA_ONLY -> inLava;
        };

        if (inTarget) {
            Vec3 motion = player.getDeltaMovement();
            if (mode.get() == Mode.DOLPHIN) {
                player.setDeltaMovement(motion.x, 0.42, motion.z);
            } else {
                player.setDeltaMovement(motion.x, upSpeed.get(), motion.z);
            }
        } else {
            BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
            BlockState belowState = mc.level.getBlockState(below);
            if (isTargetLiquid(belowState)) {
                Vec3 motion = player.getDeltaMovement();
                if (motion.y < 0.0) {
                    player.setDeltaMovement(motion.x, 0.0, motion.z);
                    player.setOnGround(true);
                }
            }
        }
    }

    private boolean isTargetLiquid(BlockState state) {
        if (state == null) return false;
        boolean isWater = state.is(Blocks.WATER);
        boolean isLava = state.is(Blocks.LAVA);

        return switch (liquidType.get()) {
            case BOTH -> isWater || isLava;
            case WATER_ONLY -> isWater;
            case LAVA_ONLY -> isLava;
        };
    }

    public enum Mode {
        SOLID,
        BOUNCE,
        DOLPHIN
    }

    public enum LiquidType {
        BOTH,
        WATER_ONLY,
        LAVA_ONLY
    }
}
