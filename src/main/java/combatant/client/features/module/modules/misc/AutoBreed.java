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
import combatant.client.features.module.ModuleSubcategory;
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
        aliases = {"AnimalBreeder"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autobreed.description")
public final class AutoBreed extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("autoBreedRange", "range", 4.5D, 2.0D, 6.0D);
    private final NumberValue<Integer> delayTicks = num("autoBreedDelayTicks", "delay_ticks", 4, 1, 20);

    private int cooldown;

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double rangeValue = range.get();
        double rangeSq = rangeValue * rangeValue;
        AABB box = player.getBoundingBox().inflate(rangeValue);

        for (Animal animal : mc.level.getEntitiesOfClass(Animal.class, box, entity -> entity != null && entity.isAlive())) {
            if (player.distanceToSqr(animal) > rangeSq || !animal.canFallInLove()) continue;
            InteractionHand hand = foodHand(player, animal);
            if (hand == null) continue;

            mc.gameMode.interact(player, animal, new EntityHitResult(animal), hand);
            player.swing(hand);
            cooldown = delayTicks.get();
            return;
        }
    }

    private static InteractionHand foodHand(LocalPlayer player, Animal animal) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && animal.isFood(main)) return InteractionHand.MAIN_HAND;
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && animal.isFood(off)) return InteractionHand.OFF_HAND;
        return null;
    }
}
