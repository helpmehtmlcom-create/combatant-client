/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(
        id = "autoshear",
        displayName = "AutoShear",
        aliases = {"FastShear", "SheepShearer"},
        category = ModuleCategory.MISC,
        description = "Automatically shears nearby sheep when holding shears."
)
public final class AutoShear extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("autoShearRange", "range", 4.5, 2.0, 6.0);

    private final NumberValue<Integer> delayTicks =
            num("autoShearDelayTicks", "delay_ticks", 3, 1, 20);

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

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        InteractionHand hand = null;
        if (player.getMainHandItem().is(Items.SHEARS)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.SHEARS)) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand == null) return;

        double maxDistSq = range.get() * range.get();
        AABB box = player.getBoundingBox().inflate(range.get());

        for (Sheep sheep : mc.level.getEntitiesOfClass(Sheep.class, box, s -> s != null && s.isAlive())) {
            if (player.distanceToSqr(sheep) > maxDistSq) continue;

            if (sheep.readyForShearing()) {
                mc.gameMode.interact(player, sheep, new EntityHitResult(sheep), hand);
                player.swing(hand);
                cooldown = delayTicks.get();
                return;
            }
        }
    }
}
