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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automatically burrows the player into a blast-resistant block at their feet
 * to prevent being moved, faceplaced, or affected by explosion knockback.
 */
@ModuleInfo(
        id = "burrow",
        displayName = "Burrow",
        category = ModuleCategory.COMBAT,
        aliases = {"instantburrow", "selfburrow"},
        description = "Glitches the player into a block at their feet to prevent being moved or damaged."
)
public class Burrow extends Module {

    public enum BurrowBlock {
        OBSIDIAN,
        ENDER_CHEST,
        ANVIL
    }

    public enum RubberbandMode {
        PACKET,
        JUMP,
        GLITCH
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<BurrowBlock> blockType =
            enumSetting("blockType", "blockType", BurrowBlock.OBSIDIAN, BurrowBlock.values());
    private final EnumValue<RubberbandMode> rubberbandMode =
            enumSetting("rubberbandMode", "rubberbandMode", RubberbandMode.PACKET, RubberbandMode.values());
    private final NumberValue<Double> height =
            num("height", "height", 1.14, 1.1, 10.0);
    private final BooleanValue rotate =
            bool("rotate", "rotate", false);

    @Override
    public void onEnable() {
        triggerBurrow();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (isEnabled()) {
            triggerBurrow();
        }
    }

    private void triggerBurrow() {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        ClientPacketListener connection = mc.getConnection();
        MultiPlayerGameMode gameMode = mc.gameMode;

        if (player == null || level == null || connection == null || gameMode == null) {
            setEnabled(false);
            return;
        }

        // Player must be on ground
        if (!player.onGround()) {
            setEnabled(false);
            return;
        }

        BlockPos feetPos = player.blockPosition();
        BlockState feetState = level.getBlockState(feetPos);
        if (!feetState.canBeReplaced()) {
            setEnabled(false);
            return;
        }

        // Find chosen block in offhand or hotbar
        InteractionHand hand = null;
        int hotbarSlot = -1;

        if (isTargetBlock(player.getOffhandItem())) {
            hand = InteractionHand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isTargetBlock(stack)) {
                    hotbarSlot = i;
                    hand = InteractionHand.MAIN_HAND;
                    break;
                }
            }
        }

        if (hand == null) {
            setEnabled(false);
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean collision = player.horizontalCollision;

        // Send jump/position packets upward
        connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.42, z, false, collision));
        connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.75, z, false, collision));
        connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 1.01, z, false, collision));
        connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 1.14, z, false, collision));

        // Placement hit result
        BlockHitResult hitResult = resolvePlacementHit(level, feetPos);

        // Rotate if enabled
        if (rotate.get() && hitResult != null) {
            Rotation rot = Rotation.lookingAt(hitResult.getLocation(), player.getEyePosition());
            connection.send(new ServerboundMovePlayerPacket.Rot(
                    rot.yaw(),
                    rot.pitch(),
                    false,
                    collision
            ));
        }

        // Place block at feet position
        if (hand == InteractionHand.OFF_HAND) {
            gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
            player.swing(InteractionHand.OFF_HAND);
        } else {
            boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 1);
            if (leased) {
                try {
                    gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
                    player.swing(InteractionHand.MAIN_HAND);
                } finally {
                    InventorySwap.INSTANCE.releaseHotbar(this);
                }
            } else {
                int prevSlot = InventorySwap.INSTANCE.clientSelectedSlot();
                InventorySwap.INSTANCE.selectHotbar(hotbarSlot);
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
                player.swing(InteractionHand.MAIN_HAND);
                if (prevSlot >= 0 && prevSlot < 9) {
                    InventorySwap.INSTANCE.selectHotbar(prevSlot);
                }
            }
        }

        // Send rubberband teleport packet to glitch into block
        double spoofHeight = height.get();
        switch (rubberbandMode.get()) {
            case PACKET -> connection.send(new ServerboundMovePlayerPacket.Pos(x, y + spoofHeight, z, false, collision));
            case JUMP -> {
                connection.send(new ServerboundMovePlayerPacket.Pos(x, y + spoofHeight, z, false, collision));
                player.jumpFromGround();
            }
            case GLITCH -> {
                connection.send(new ServerboundMovePlayerPacket.Pos(x, y - spoofHeight, z, false, collision));
                connection.send(new ServerboundMovePlayerPacket.Pos(x, y + spoofHeight, z, false, collision));
            }
        }

        // Automatically toggle/disable module after execution
        setEnabled(false);
    }

    private boolean isTargetBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return switch (blockType.get()) {
            case OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
            case ENDER_CHEST -> block == Blocks.ENDER_CHEST;
            case ANVIL -> block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
        };
    }

    private BlockHitResult resolvePlacementHit(Level level, BlockPos feetPos) {
        BlockPos below = feetPos.below();
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isAir() && !belowState.canBeReplaced()) {
            Vec3 hitVec = new Vec3(feetPos.getX() + 0.5, feetPos.getY(), feetPos.getZ() + 0.5);
            return new BlockHitResult(hitVec, Direction.UP, below, false);
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = feetPos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (!state.isAir() && !state.canBeReplaced()) {
                Direction clickFace = dir.getOpposite();
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(clickFace.getUnitVec3i()).scale(0.5));
                return new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return new BlockHitResult(
                new Vec3(feetPos.getX() + 0.5, feetPos.getY(), feetPos.getZ() + 0.5),
                Direction.UP,
                feetPos,
                false
        );
    }
}
