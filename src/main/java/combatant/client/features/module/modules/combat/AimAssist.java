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
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(
        id = "aimassist",
        displayName = "AimAssist",
        aliases = {"LegitAim", "SmoothAim"},
        category = ModuleCategory.COMBAT,
        description = "Smoothly guides your crosshair towards nearby targets within your field of view"
)
public final class AimAssist extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("aimAssistRange", "range", 4.5, 2.0, 8.0);

    private final NumberValue<Double> fov =
            num("aimAssistFov", "fov", 60.0, 10.0, 180.0);

    private final NumberValue<Double> speed =
            num("aimAssistSpeed", "speed", 4.0, 0.5, 20.0);

    private final BooleanValue clickOnly =
            bool("aimAssistClickOnly", "click_only", true);

    private final BooleanValue weaponsOnly =
            bool("aimAssistWeaponsOnly", "weapons_only", true);

    private final BooleanValue playersOnly =
            bool("aimAssistPlayersOnly", "players_only", true);

    private final BooleanValue ignoreFriends =
            bool("aimAssistIgnoreFriends", "ignore_friends", true);

    public AimAssist() {
        super();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || ClientScreen.current() != null) return;

        if (clickOnly.get() && !mc.options.keyAttack.isDown()) return;

        if (weaponsOnly.get() && !isHoldingWeapon(player)) return;

        LivingEntity bestTarget = findTarget(player);
        if (bestTarget == null) return;

        float[] targetRot = RotationUtil.getRotationsToEntity(player, bestTarget);
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float yawDiff = RotationUtil.angleDifference(targetRot[0], currentYaw);
        float pitchDiff = RotationUtil.angleDifference(targetRot[1], currentPitch);

        float maxStep = speed.get().floatValue();

        float yawStep = Math.copySign(Math.min(Math.abs(yawDiff), maxStep), yawDiff);
        float pitchStep = Math.copySign(Math.min(Math.abs(pitchDiff), maxStep * 0.75f), pitchDiff);

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

    private boolean isHoldingWeapon(LocalPlayer player) {
        var item = player.getMainHandItem();
        return item.is(ItemTags.SWORDS) || item.is(ItemTags.AXES) || item.is(ItemTags.MACE_ENCHANTABLE);
    }
}
