/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.player.inventory.InventorySwap;

@ModuleInfo(
        id = "antibed",
        displayName = "AntiBed",
        category = ModuleCategory.COMBAT
)
public final class AntiBed extends Module {

    private final NumberValue<Double> range = num("range", 5.0, 1.0, 6.0);
    private final BooleanValue placeOnHead = bool("place_on_head", true);
    private final BooleanValue antiAnchor = bool("anti_anchor", true);


    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        int defenseSlot = findDefenseItem();
        if (defenseSlot == -1) return;

        BlockPos playerPos = player.blockPosition();

        // 1. Defend player head from bed placement
        if (placeOnHead.get()) {
            BlockPos headPos = playerPos.above(2);
            if (mc.level.getBlockState(headPos).isAir()) {
                placeBlock(headPos, defenseSlot);
                return;
            }
        }

        // 2. Scan for placed beds or anchors around player
        int r = (int) Math.ceil(range.get());
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos targetPos = playerPos.offset(x, y, z);
                    if (player.position().distanceTo(Vec3.atCenterOf(targetPos)) > range.get()) continue;

                    BlockState state = mc.level.getBlockState(targetPos);
                    boolean isThreat = state.getBlock() instanceof BedBlock
                            || (antiAnchor.get() && state.is(Blocks.RESPAWN_ANCHOR));

                    if (isThreat) {
                        BlockPos coverPos = targetPos.above();
                        if (mc.level.getBlockState(coverPos).isAir()) {
                            placeBlock(coverPos, defenseSlot);
                            return;
                        }
                    }
                }
            }
        }
    }

    private int findDefenseItem() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.STRING) || stack.is(Items.OAK_BUTTON) || stack.is(Items.STONE_BUTTON)
                    || stack.is(Items.SKELETON_SKULL) || stack.is(Items.WITHER_SKELETON_SKULL)) {
                return i;
            }
        }
        return -1;
    }

    private void placeBlock(BlockPos pos, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null) return;
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos.below(), false
        );
        BlockPlacer.placeBlock(
                this,
                hit,
                InteractionHand.MAIN_HAND,
                hotbarSlot,
                true,
                BlockPlacer.SwingMode.CLIENT_AND_SERVER
        );
    }
}
