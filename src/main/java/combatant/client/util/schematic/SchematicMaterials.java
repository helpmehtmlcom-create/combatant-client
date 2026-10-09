/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Turns palette counts into an item shopping list. */
public final class SchematicMaterials {

    private SchematicMaterials() {
    }

    public record Entry(Item item, int count) {
    }

    public static Item itemFor(BlockState state) {
        if (state == null || state.isAir()) return Items.AIR;
        return state.getBlock().asItem();
    }

    /** How many items one cell of this state costs: the second half of doors and beds is free, double slabs cost two. */
    public static int itemsPerCell(BlockState state) {
        if (state == null || state.isAir()) return 0;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        return 1;
    }

    public static List<Entry> materials(Schematic schematic, int[] paletteHistogram) {
        Map<Item, int[]> totals = new IdentityHashMap<>();
        for (int i = 1; i < paletteHistogram.length; i++) {
            int cells = paletteHistogram[i];
            if (cells == 0) continue;
            BlockState state = schematic.paletteState(i);
            Item item = itemFor(state);
            int perCell = itemsPerCell(state);
            if (item == Items.AIR || perCell == 0) continue;
            totals.computeIfAbsent(item, ignored -> new int[1])[0] += cells * perCell;
        }
        List<Entry> out = new ArrayList<>(totals.size());
        for (Map.Entry<Item, int[]> entry : totals.entrySet()) {
            out.add(new Entry(entry.getKey(), entry.getValue()[0]));
        }
        out.sort((a, b) -> b.count != a.count ? Integer.compare(b.count, a.count)
                : a.item.toString().compareTo(b.item.toString()));
        return out;
    }

    public static int countInInventory(Player player, Item item) {
        if (player == null || item == null || item == Items.AIR) return 0;
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        if (!offhand.isEmpty() && offhand.getItem() == item) count += offhand.getCount();
        return count;
    }
}
