/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.CategoryService;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.aiming.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "aimassist",
        displayName = "AimAssist",
        description = "Smoothly and subtly guides player crosshair towards targets without sudden camera snapping.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.LEGIT,
        aliases = {"smoothaim", "legitaim", "aimhelper"}
)
public class AimAssist extends Module {

    private final NumberValue<Float> smooth =
            num("aimassist_smooth", "smooth", 6.0f, 1.0f, 25.0f);
    private final NumberValue<Float> fov =
            num("aimassist_fov", "fov", 70.0f, 10.0f, 180.0f);
    private final NumberValue<Double> range =
            num("aimassist_range", "range", 4.5, 2.0, 6.5);
    private final BooleanValue pitch =
            bool("aimassist_pitch", "pitch", false);
    private final BooleanValue onlyClicking =
            bool("aimassist_only_click", "only_click", true);
    private final BooleanValue onlyWeapon =
            bool("aimassist_only_weapon", "only_weapon", true);
    private final BooleanValue targetPlayers =
            bool("aimassist_players", "players", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (ClientScreen.current() != null) return;

        if (onlyClicking.get() && !mc.options.keyAttack.isDown()) return;
        if (onlyWeapon.get() && !isHoldingWeapon(player)) return;

        LivingEntity target = findBestTarget(player);
        if (target == null) return;

        Vec3 eyes = player.getEyePosition();
        Vec3 targetPos = target.getBoundingBox().getCenter();
        float[] needed = RotationUtil.calculateRotations(eyes, targetPos);

        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float yawDiff = RotationUtil.wrapDegrees(needed[0] - currentYaw);
        float pitchDiff = RotationUtil.wrapDegrees(needed[1] - currentPitch);

        if (Math.abs(yawDiff) > fov.get() * 0.5f) return;

        float smoothFactor = Math.max(1.0f, smooth.get());
        float deltaYaw = yawDiff / smoothFactor;
        float deltaPitch = pitchDiff / smoothFactor;

        double gcd = RotationUtil.gcd();
        if (gcd > 0.0) {
            deltaYaw = (float) (Math.round(deltaYaw / gcd) * gcd);
            deltaPitch = (float) (Math.round(deltaPitch / gcd) * gcd);
        }

        player.setYRot(currentYaw + deltaYaw);
        if (pitch.get()) {
            player.setXRot(Mth.clamp(currentPitch + deltaPitch, -90.0f, 90.0f));
        }
    }

    private LivingEntity findBestTarget(LocalPlayer player) {
        LivingEntity best = null;
        double bestDistSq = range.get() * range.get();
        float bestFov = fov.get() * 0.5f;

        Vec3 eyes = player.getEyePosition();
        for (Player other : mc.level.players()) {
            if (other == null || other == player || !other.isAlive() || other.isSpectator()) continue;
            if (CategoryService.isFriend(other)) continue;
            if (!targetPlayers.get()) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq > bestDistSq) continue;

            float[] rots = RotationUtil.calculateRotations(eyes, other.getBoundingBox().getCenter());
            float yawDiff = Math.abs(RotationUtil.wrapDegrees(rots[0] - player.getYRot()));
            if (yawDiff < bestFov) {
                bestFov = yawDiff;
                best = other;
            }
        }
        return best;
    }

    private boolean isHoldingWeapon(LocalPlayer player) {
        return player.getMainHandItem().is(ItemTags.SWORDS)
                || player.getMainHandItem().is(ItemTags.AXES)
                || player.getMainHandItem().is(ItemTags.MACE_ENCHANTABLE);
    }
}
