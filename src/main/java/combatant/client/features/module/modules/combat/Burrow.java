/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "burrow",
        displayName = "Burrow",
        aliases = {"SelfBurrow", "FeetBurrow", "InstantBurrow"},
        category = ModuleCategory.COMBAT,
        description = "Places a blast-resistant block inside your feet for explosion and knockback immunity."
)
public final class Burrow extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<BurrowBlock> blockType =
            enumSetting("burrowBlockType", "block_type", BurrowBlock.OBSIDIAN, BurrowBlock.values());

    private final BooleanValue center =
            bool("burrowCenter", "center", true);

    private final NumberValue<Float> rubberbandOffset =
            num("burrowRubberbandOffset", "rubberband_offset", 1.15f, 1.1f, 5.0f);

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) {
            setEnabled(false);
            return;
        }

        if (!player.onGround()) {
            setEnabled(false);
            return;
        }

        BlockPos feet = player.blockPosition();
        if (!mc.level.getBlockState(feet).canBeReplaced()) {
            setEnabled(false);
            return;
        }

        int slot = findBurrowSlot(player);
        if (slot == -1) {
            setEnabled(false);
            return;
        }

        if (center.get()) {
            double cx = Math.floor(player.getX()) + 0.5;
            double cz = Math.floor(player.getZ()) + 0.5;
            player.setPos(cx, player.getY(), cz);
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 1. Send jump packet sequence
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + 0.42, z, false, false));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + 0.75, z, false, false));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + 1.01, z, false, false));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + 1.16, z, false, false));

        // 2. Place block at feet
        InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);

        BlockHitResult hit = new BlockHitResult(
                new Vec3(x, y, z),
                Direction.UP,
                feet.below(),
                false
        );

        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND);

        // 3. Send rubberband offset packet
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + rubberbandOffset.get(), z, false, false));

        setEnabled(false);
    }

    private int findBurrowSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isTargetBlock(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isTargetBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return switch (blockType.get()) {
                case OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
                case ENDER_CHEST -> block == Blocks.ENDER_CHEST;
                case RESPAWN_ANCHOR -> block == Blocks.RESPAWN_ANCHOR;
                case ANVIL -> block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
            };
        }
        return false;
    }

    public enum BurrowBlock {
        OBSIDIAN,
        ENDER_CHEST,
        RESPAWN_ANCHOR,
        ANVIL
    }
}
