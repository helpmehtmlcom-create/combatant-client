/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;

import java.util.List;

@ModuleInfo(
        id = "bowspam",
        displayName = "BowSpam",
        category = ModuleCategory.COMBAT,
        aliases = {"fastbow"}
)
public class BowSpam extends Module {

    public enum BowSpamMode {
        HOLD,
        AUTO
    }

    private static final int ROTATION_PRIORITY = 50;
    private static final double TARGET_RANGE = 64.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> chargeTicks = num("chargeTicks", 3, 2, 20);
    private final EnumValue<BowSpamMode> mode = enumSetting("mode", "mode", BowSpamMode.HOLD, BowSpamMode.values());
    private final BooleanValue aim = bool("aim", true);

    private boolean autoUsing;

    @Override
    public void onEnable() {
        autoUsing = false;
    }

    @Override
    public void onDisable() {
        if (autoUsing) {
            stopAutoUsing();
        } else {
            RotationManager.INSTANCE.clear(this);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }

        InteractionHand bowHand = getBowHand();
        if (bowHand == null) {
            if (autoUsing) {
                stopAutoUsing();
            }
            return;
        }

        if (mc.player.isUsingItem() && !mc.player.getUseItem().is(Items.BOW)) {
            return;
        }

        BowSpamMode currentMode = mode.get();
        if (currentMode == BowSpamMode.HOLD) {
            if (!mc.options.keyUse.isDown()) {
                RotationManager.INSTANCE.clear(this);
                return;
            }

            if (!mc.player.isUsingItem()) {
                mc.gameMode.useItem(mc.player, bowHand);
                return;
            }

            if (mc.player.getTicksUsingItem() >= chargeTicks.get()) {
                releaseAndShoot(bowHand, mc.options.keyUse.isDown());
            }
        } else if (currentMode == BowSpamMode.AUTO) {
            LivingEntity target = findTarget();
            if (target != null) {
                if (!mc.player.isUsingItem()) {
                    mc.gameMode.useItem(mc.player, bowHand);
                    autoUsing = true;
                    return;
                }

                if (mc.player.getTicksUsingItem() >= chargeTicks.get()) {
                    releaseAndShoot(bowHand, target != null || mc.options.keyUse.isDown());
                }
            } else {
                if (autoUsing || (mc.player.isUsingItem() && !mc.options.keyUse.isDown())) {
                    stopAutoUsing();
                }
            }
        }
    }

    private void releaseAndShoot(InteractionHand bowHand, boolean reinitiate) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (aim.get()) {
            LivingEntity target = findTarget();
            if (target != null) {
                applyRotation(target);
            }
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        ));
        mc.player.stopUsingItem();

        if (reinitiate && mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, bowHand);
            if (mode.get() == BowSpamMode.AUTO) {
                autoUsing = true;
            }
        }
    }

    private void applyRotation(LivingEntity target) {
        if (mc.player == null) return;
        Rotation rot = Rotation.lookingAt(target.getEyePosition(), mc.player.getEyePosition()).normalize();
        RotationTarget rotTarget = new RotationTarget(
                rot,
                target,
                List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(rotTarget, ROTATION_PRIORITY, this);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    rot.yaw(),
                    rot.pitch(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
            ));
        }
    }

    private InteractionHand getBowHand() {
        if (mc.player == null) return null;
        if (mc.player.getMainHandItem().is(Items.BOW)) {
            return InteractionHand.MAIN_HAND;
        }
        if (mc.player.getOffhandItem().is(Items.BOW)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private LivingEntity findTarget() {
        LivingEntity managed = TargetManager.getTarget();
        if (managed != null && TargetingUtil.isValidCombatTarget(managed)
                && mc.player != null && mc.player.distanceToSqr(managed) <= TARGET_RANGE * TARGET_RANGE) {
            return managed;
        }

        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                TARGET_RANGE,
                360.0f,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                TargetingUtil.TargetPriority.DISTANCE
        );
        LivingEntity best = TargetingUtil.findBestTarget(mc, settings);
        if (best != null) {
            return best;
        }

        TargetingUtil.TargetingSettings nonVisibleSettings = new TargetingUtil.TargetingSettings(
                TARGET_RANGE,
                360.0f,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                TargetingUtil.TargetPriority.DISTANCE
        );
        return TargetingUtil.findBestTarget(mc, nonVisibleSettings);
    }

    private void stopAutoUsing() {
        if (mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        autoUsing = false;
        RotationManager.INSTANCE.clear(this);
    }
}
