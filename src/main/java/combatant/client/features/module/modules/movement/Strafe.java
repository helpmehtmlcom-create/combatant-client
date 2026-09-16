/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.util.OmniItemUtils;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.MovementUtil;

import java.util.List;

@ModuleInfo(
        id = "strafe",
        displayName = "Strafe",
        category = ModuleCategory.MOVEMENT,
        description = "Grants full mid-air strafe control and maintains speed while turning."
)
public final class Strafe extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumMode("mode", Mode.NCP);
    private final BooleanValue jump = bool("jump", false);
    private final NumberValue<Float> speed =
            visibleWhen(num("speed", 0.2873f, 0.1f, 1.0f), () -> mode.get() == Mode.VANILLA);


    @EventHandler(priority = 2000)
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        if (!canOperate(player)) {
            return;
        }

        if (!MovementUtil.isMoving()) {
            return;
        }

        if (jump.get() && player.onGround()) {
            player.jumpFromGround();
        }

        double moveSpeed = switch (mode.get()) {
            case VANILLA -> speed.get();
            case NCP -> Math.max(MovementUtil.getHorizontalMotion(player), MovementUtil.getBaseMoveSpeed(player));
            case STRICT -> MovementUtil.getBaseMoveSpeed(player);
        };

        MovementUtil.strafe(player, moveSpeed);
    }

    private boolean canOperate(LocalPlayer player) {
        return player != null && !isFreecamActive();
    }

    private boolean isFreecamActive() {
        return Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled();
    }

    public enum Mode {
        VANILLA,
        NCP,
        STRICT
    }
}
