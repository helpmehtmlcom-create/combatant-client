/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoStun;
import combatant.client.features.module.modules.movement.NoSlow;

public enum EnvironmentMovementController {
    ;

    private static final float DEFAULT_SLIPPERINESS = 0.6F;
    private static final float DEFAULT_VELOCITY_MULTIPLIER = 1.0F;
    private static final float STICKY_SLIPPERINESS = 0.8F;
    private static final float STICKY_VELOCITY_MULTIPLIER = 0.4F;

    public static Float getSlipperinessOverride(Block block) {
        if (block == null) {
            return null;
        }
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.isEnabled()) {
            if (noSlow.shouldCancelHoney() && block == Blocks.HONEY_BLOCK) {
                return DEFAULT_SLIPPERINESS;
            }
            if (noSlow.shouldCancelSlime() && block == Blocks.SLIME_BLOCK) {
                return DEFAULT_SLIPPERINESS;
            }
        }


        NoStun noStun = Modules.get(NoStun.class);
        if (noStun != null) {
            Float override = noStun.getSlipperinessOverride(block, vanillaSlipperiness(block));
            if (override != null) {
                return override;
            }
        }

        return null;
    }

    public static Float getVelocityMultiplierOverride(Block block) {
        if (block == null) {
            return null;
        }
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.isEnabled()) {
            if (noSlow.shouldCancelSoulSand() && block == Blocks.SOUL_SAND) {
                return DEFAULT_VELOCITY_MULTIPLIER;
            }
            if (noSlow.shouldCancelHoney() && block == Blocks.HONEY_BLOCK) {
                return DEFAULT_VELOCITY_MULTIPLIER;
            }
        }


        NoStun noStun = Modules.get(NoStun.class);
        if (noStun != null) {
            Float override = noStun.getVelocityMultiplierOverride(block, vanillaVelocityMultiplier(block));
            if (override != null) {
                return override;
            }
        }

        return null;
    }

    public static boolean shouldPreserveHoneySlide(Entity entity) {
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.shouldCancelHoney()) {
            return true;
        }

        if (isNoStunHoneyEnabled()) {
            return true;
        }

        return false;
    }

    private static boolean isNoStunHoneyEnabled() {
        NoStun noStun = Modules.get(NoStun.class);
        return noStun != null
                && noStun.isEnabled()
                && noStun.isFunctionEnabled(NoStun.fnEnvBlocks())
                && noStun.isEnvBlockEnabled(NoStun.envHoney());
    }

    private static float vanillaSlipperiness(Block block) {
        if (block == Blocks.HONEY_BLOCK || block == Blocks.SLIME_BLOCK) {
            return STICKY_SLIPPERINESS;
        }

        return DEFAULT_SLIPPERINESS;
    }

    private static float vanillaVelocityMultiplier(Block block) {
        if (block == Blocks.HONEY_BLOCK || block == Blocks.SOUL_SAND) {
            return STICKY_VELOCITY_MULTIPLIER;
        }

        return DEFAULT_VELOCITY_MULTIPLIER;
    }
}
