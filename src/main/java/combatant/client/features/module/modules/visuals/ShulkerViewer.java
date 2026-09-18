/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(
        id = "shulkerviewer",
        displayName = "ShulkerViewer",
        description = "DonutSMP shulker peek utility. Inspect shulker box contents without opening or placing them.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"shulkerpeek", "boxviewer", "peekshulker"}
)
public class ShulkerViewer extends Module {

    private final BooleanValue peekPlaced =
            bool("shulkerviewer_placed", "placed_boxes", true);
    private final BooleanValue showEmpty =
            bool("shulkerviewer_empty", "show_empty", false);

    private final Minecraft mc = Minecraft.getInstance();

    public static boolean isShulker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    public static NonNullList<ItemStack> getShulkerContents(ItemStack stack) {
        if (!isShulker(stack)) return NonNullList.create();
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return NonNullList.create();

        NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
        container.copyInto(list);
        return list;
    }

    public NonNullList<ItemStack> getLookedAtShulkerContents() {
        if (!peekPlaced.get() || mc.level == null || mc.hitResult == null) return NonNullList.create();
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return NonNullList.create();

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof ShulkerBoxBlockEntity shulker) {
            NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int i = 0; i < 27; i++) {
                list.set(i, shulker.getItem(i));
            }
            return list;
        }
        return NonNullList.create();
    }
}
