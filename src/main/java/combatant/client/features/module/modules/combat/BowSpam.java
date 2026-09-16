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
import net.minecraft.world.item.Items;
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
        id = "bowspam",
        displayName = "BowSpam",
        category = ModuleCategory.COMBAT,
        aliases = {"fastbow"},
        description = "Rapidly and automatically fires bow arrows as soon as minimal charge is reached."
)
public class BowSpam extends Module {

    private static final int ROTATION_PRIORITY = 50;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("range", 32.0, 4.0, 64.0);
    private final NumberValue<Integer> minCharge = num("minCharge", 3, 1, 20);

    private boolean autoUsing;

    @Override
    public void onEnable() {
        autoUsing = false;
    }

    @Override
    public void onDisable() {
        stopAutoUsing();
        TargetManager.setModuleTarget(null);
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
            TargetManager.setModuleTarget(null);
            return;
        }

        if (mc.player.isUsingItem() && !mc.player.getUseItem().is(Items.BOW)) {
            return;
        }

        LivingEntity target = TargetManager.resolveTarget(mc.player, mc.level, range.get(), TargetingUtil.TargetPriority.DISTANCE);
        TargetManager.setModuleTarget(target);

        if (target != null) {
            Rotation angle = ProjectilePredictionUtil.calculateBowAngle(
                    mc.player.getEyePosition(),
                    target,
                    minCharge.get()
            );

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

            if (!mc.player.isUsingItem()) {
                mc.gameMode.useItem(mc.player, bowHand);
                autoUsing = true;
                return;
            }

            if (mc.player.getTicksUsingItem() >= minCharge.get()) {
                releaseAndShoot(bowHand);
            }
        } else {
            if (autoUsing) {
                stopAutoUsing();
            } else {
                RotationManager.INSTANCE.release(this);
            }
        }
    }

    private void releaseAndShoot(InteractionHand bowHand) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        ));
        mc.player.stopUsingItem();

        if (mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, bowHand);
            autoUsing = true;
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

    private void stopAutoUsing() {
        if (mc.player != null && mc.player.isUsingItem() && autoUsing) {
            mc.player.stopUsingItem();
        }
        autoUsing = false;
        RotationManager.INSTANCE.clear(this);
    }
}
