/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(
        id = "automount",
        displayName = "AutoMount",
        aliases = {"Mount", "HorseMount"},
        category = ModuleCategory.PLAYER,
        description = "Automatically mounts nearby rideable entities and vehicles."
)
public final class AutoMount extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("autoMountRange", "range", 4.0, 1.0, 6.0);

    private final BooleanValue boats =
            bool("autoMountBoats", "boats", true);

    private final BooleanValue minecarts =
            bool("autoMountMinecarts", "minecarts", true);

    private final BooleanValue horses =
            bool("autoMountHorses", "horses", true);

    private final NumberValue<Integer> delayTicks =
            num("autoMountDelayTicks", "delay_ticks", 5, 0, 20);

    private int cooldown = 0;

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        if (player.isPassenger()) {
            cooldown = 0;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double r = range.get();
        double maxDistSq = r * r;
        AABB box = player.getBoundingBox().inflate(r);

        for (Entity entity : mc.level.getEntities(player, box)) {
            if (entity.distanceToSqr(player) > maxDistSq) continue;
            if (entity.isPassenger() || entity.hasPassenger(player)) continue;

            boolean canMount = (boats.get() && entity instanceof AbstractBoat)
                    || (minecarts.get() && entity instanceof AbstractMinecart)
                    || (horses.get() && entity instanceof AbstractHorse);

            if (canMount) {
                mc.gameMode.interact(player, entity, new EntityHitResult(entity), InteractionHand.MAIN_HAND);
                cooldown = delayTicks.get();
                return;
            }
        }
    }
}
