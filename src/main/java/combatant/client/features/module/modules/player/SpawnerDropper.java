/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "spawnerdropper",
        displayName = "SpawnerDropper",
        description = "Automates spawner mob grinding on DonutSMP, attacking grouped mobs and harvesting mob drops.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"mobgrinder", "spawnergrind", "dropfarmer"}
)
public class SpawnerDropper extends Module {

    private final NumberValue<Double> range =
            num("spawnerdropper_range", "range", 4.2, 2.0, 6.0);
    private final BooleanValue onlyCooldown =
            bool("spawnerdropper_cooldown", "only_cooldown", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        if (onlyCooldown.get() && player.getAttackStrengthScale(0.0f) < 0.95f) {
            return;
        }

        double maxDistSq = range.get() * range.get();
        Vec3 eyes = player.getEyePosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Monster || entity instanceof Enemy) || !entity.isAlive()) continue;

            double distSq = eyes.distanceToSqr(entity.getBoundingBox().getCenter());
            if (distSq <= maxDistSq) {
                mc.gameMode.attack(player, entity);
                player.swing(InteractionHand.MAIN_HAND);
                break;
            }
        }
    }
}
