/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.projectile.ProjectilePredictionUtil;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;

@ModuleInfo(
        id = "bowaim",
        displayName = "BowAim",
        aliases = {"BowAimbot", "ArrowAim"},
        category = ModuleCategory.COMBAT,
        description = "Calculates ballistic projectile trajectory and smoothly aims bows at targets"
)
public final class BowAim extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("bowAimRange", "range", 40.0, 5.0, 80.0);

    private final NumberValue<Double> fov =
            num("bowAimFov", "fov", 90.0, 10.0, 180.0);

    private final NumberValue<Double> speed =
            num("bowAimSpeed", "speed", 6.0, 0.5, 30.0);

    private final BooleanValue playersOnly =
            bool("bowAimPlayersOnly", "players_only", true);

    private final BooleanValue ignoreFriends =
            bool("bowAimIgnoreFriends", "ignore_friends", true);

    public BowAim() {
        super();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || ClientScreen.current() != null) return;

        if (!isHoldingProjectileWeapon(player)) return;

        if (!player.isUsingItem() && !isCrossbowCharged(player)) return;

        LivingEntity bestTarget = findTarget(player);
        if (bestTarget == null) return;

        Rotation targetRot = ProjectilePredictionUtil.calculateForHeldItem(player, bestTarget, true);
        if (targetRot == null) return;

        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float yawDiff = RotationUtil.angleDifference(targetRot.yaw(), currentYaw);
        float pitchDiff = RotationUtil.angleDifference(targetRot.pitch(), currentPitch);

        float maxStep = speed.get().floatValue();

        float yawStep = Math.copySign(Math.min(Math.abs(yawDiff), maxStep), yawDiff);
        float pitchStep = Math.copySign(Math.min(Math.abs(pitchDiff), maxStep), pitchDiff);

        player.setYRot(currentYaw + yawStep);
        player.setXRot(Mth.clamp(currentPitch + pitchStep, -90.0f, 90.0f));
    }

    private LivingEntity findTarget(LocalPlayer player) {
        LivingEntity best = null;
        double bestDistSq = range.get() * range.get();
        double maxFov = fov.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive()) {
                continue;
            }

            if (playersOnly.get() && !(living instanceof Player)) {
                continue;
            }

            if (living instanceof Player targetPlayer && ignoreFriends.get()) {
                String name = targetPlayer.getGameProfile().name();
                if (PlayerRelations.get().isFriend(name)) {
                    continue;
                }
            }

            double distSq = player.distanceToSqr(living);
            if (distSq > bestDistSq) continue;

            float[] rot = RotationUtil.getRotationsToEntity(player, living);
            float diff = Math.abs(RotationUtil.angleDifference(rot[0], player.getYRot()));
            if (diff > maxFov / 2.0) continue;

            best = living;
            bestDistSq = distSq;
        }

        return best;
    }

    private boolean isHoldingProjectileWeapon(LocalPlayer player) {
        var item = player.getMainHandItem().getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return true;
        var off = player.getOffhandItem().getItem();
        return off instanceof BowItem || off instanceof CrossbowItem || off instanceof TridentItem;
    }

    private boolean isCrossbowCharged(LocalPlayer player) {
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(stack);
        }
        var off = player.getOffhandItem();
        if (off.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(off);
        }
        return false;
    }
}
