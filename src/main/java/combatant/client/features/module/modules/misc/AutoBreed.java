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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(
        id = "autobreed",
        displayName = "AutoBreed",
        aliases = {"AnimalBreeder", "FastBreed"},
        category = ModuleCategory.MISC,
        description = "Automatically breeds nearby animals when holding their breeding food."
)
public final class AutoBreed extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("autoBreedRange", "range", 4.5, 2.0, 6.0);

    private final NumberValue<Integer> delayTicks =
            num("autoBreedDelayTicks", "delay_ticks", 3, 1, 20);

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

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        InteractionHand hand = null;
        if (!main.isEmpty()) {
            hand = InteractionHand.MAIN_HAND;
        } else if (!off.isEmpty()) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand == null) return;

        ItemStack foodStack = player.getItemInHand(hand);
        double maxDistSq = range.get() * range.get();
        AABB box = player.getBoundingBox().inflate(range.get());

        for (Animal animal : mc.level.getEntitiesOfClass(Animal.class, box, a -> a != null && a.isAlive())) {
            if (player.distanceToSqr(animal) > maxDistSq) continue;

            if (animal.isFood(foodStack) && animal.canFallInLove()) {
                mc.gameMode.interact(player, animal, new EntityHitResult(animal), hand);
                player.swing(hand);
                cooldown = delayTicks.get();
                return;
            }
        }
    }
}
