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
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.projectile.ProjectilePredictionUtil;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;

import java.util.List;

@ModuleInfo(
        id = "autobow",
        displayName = "AutoBow",
        description = "Automatically charges, aims, and releases bows or crossbows at optimal pull times.",
        category = ModuleCategory.COMBAT
)
public class AutoBow extends Module {

    private static final int ROTATION_PRIORITY = 50;
    private static final double COMBAT_RANGE = 64.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> chargeThreshold = num("chargeThreshold", 90, 0, 100);
    private final EnumValue<TargetingUtil.TargetPriority> priority =
            enumCommon(
                    "priority",
                    "priority",
                    CommonSettingSchemas.COMBAT_PRIORITY,
                    TargetingUtil.TargetPriority.DISTANCE,
                    TargetingUtil.TargetPriority.values()
            );

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        TargetManager.setModuleTarget(null);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }

        InteractionHand bowHand = getBowHand();
        if (bowHand == null) {
            RotationManager.INSTANCE.clear(this);
            TargetManager.setModuleTarget(null);
            return;
        }

        LivingEntity target = TargetManager.resolveTarget(mc.player, mc.level, COMBAT_RANGE, priority.get());
        TargetManager.setModuleTarget(target);

        if (target == null) {
            RotationManager.INSTANCE.release(this);
            return;
        }

        ItemStack stack = mc.player.getItemInHand(bowHand);
        if (stack.getItem() instanceof BowItem) {
            handleBow(bowHand, target);
        } else if (stack.getItem() instanceof CrossbowItem) {
            handleCrossbow(bowHand, stack, target);
        }
    }

    private void handleBow(InteractionHand bowHand, LivingEntity target) {
        float chargeFraction = Math.max(0.1f, chargeThreshold.get() / 100.0f);
        Rotation angle = ProjectilePredictionUtil.calculateBowAngle(mc.player.getEyePosition(), target, chargeFraction);
        if (angle != null) {
            RotationTarget rotTarget = new RotationTarget(
                    angle,
                    target,
                    List.of(),
                    1,
                    2.0f,
                    true,
                    MovementCorrection.SILENT,
                    null
            );
            RotationManager.INSTANCE.setRotationTarget(rotTarget, ROTATION_PRIORITY, this);
        }

        int requiredTicks = Math.max(1, (int) Math.round(20.0 * chargeFraction));
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, bowHand);
        } else if (mc.player.getTicksUsingItem() >= requiredTicks) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ZERO,
                    Direction.DOWN
            ));
            mc.player.stopUsingItem();
        }
    }

    private void handleCrossbow(InteractionHand bowHand, ItemStack stack, LivingEntity target) {
        if (CrossbowItem.isCharged(stack)) {
            Rotation angle = ProjectilePredictionUtil.calculateBowAngle(mc.player.getEyePosition(), target, 1.0f);
            if (angle != null) {
                RotationTarget rotTarget = new RotationTarget(
                        angle,
                        target,
                        List.of(),
                        1,
                        2.0f,
                        true,
                        MovementCorrection.SILENT,
                        null
                );
                RotationManager.INSTANCE.setRotationTarget(rotTarget, ROTATION_PRIORITY, this);
            }
            mc.gameMode.useItem(mc.player, bowHand);
        } else {
            if (!mc.player.isUsingItem()) {
                mc.gameMode.useItem(mc.player, bowHand);
            }
        }
    }

    private InteractionHand getBowHand() {
        if (mc.player == null) return null;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            return InteractionHand.MAIN_HAND;
        }
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public boolean shouldCancelVanillaDoItemUse() {
        if (!isEnabled() || mc.player == null) {
            return false;
        }
        InteractionHand hand = getBowHand();
        return hand != null && TargetManager.getTarget() != null;
    }

    public void onInputCycleHandled() {
    }
}
