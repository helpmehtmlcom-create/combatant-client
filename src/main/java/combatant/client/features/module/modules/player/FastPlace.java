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
import combatant.client.mixins.accessors.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "fastplace",
        displayName = "FastPlace",
        aliases = {"FastUseItem", "FastRightClick"},
        category = ModuleCategory.PLAYER,
        description = "Removes or customizes right-click delay for blocks, crystals, exp bottles, and items."
)
public final class FastPlace extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delay =
            num("fastPlaceDelay", "delay", 0, 0, 4);

    private final BooleanValue blocks =
            bool("fastPlaceBlocks", "blocks", true);

    private final BooleanValue crystals =
            bool("fastPlaceCrystals", "crystals", true);

    private final BooleanValue exp =
            bool("fastPlaceExp", "exp", true);

    private final BooleanValue fireworks =
            bool("fastPlaceFireworks", "fireworks", true);

    private final BooleanValue all =
            bool("fastPlaceAll", "all", false);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (all.get() || isMatchingItem(player.getMainHandItem()) || isMatchingItem(player.getOffhandItem())) {
            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            if (accessor.combatant$getItemUseCooldown() > delay.get()) {
                accessor.combatant$setItemUseCooldown(delay.get());
            }
        }
    }

    private boolean isMatchingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (blocks.get() && stack.getItem() instanceof BlockItem) return true;
        if (crystals.get() && stack.is(Items.END_CRYSTAL)) return true;
        if (exp.get() && stack.is(Items.EXPERIENCE_BOTTLE)) return true;
        if (fireworks.get() && stack.is(Items.FIREWORK_ROCKET)) return true;

        return false;
    }
}
