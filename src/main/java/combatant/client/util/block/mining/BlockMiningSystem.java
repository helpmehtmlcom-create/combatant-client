/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.InteractionUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockMiningSystem {

    public static final BlockMiningSystem INSTANCE = new BlockMiningSystem();

    private final Minecraft mc = Minecraft.getInstance();

    private MiningTask primaryTask = null;
    private MiningTask secondaryTask = null;

    private BlockPos rebreakPos = null;
    private Direction rebreakSide = Direction.UP;

    // Fast-access rebreak cache: tracks recently broken blocks for instant rebreak (CivBreak)
    private final Map<BlockPos, Long> rebreakCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
            return size() > 32;
        }
    };

    private BlockMiningSystem() {
    }

    public MiningTask getPrimaryTask() {
        return primaryTask;
    }

    public MiningTask getSecondaryTask() {
        return secondaryTask;
    }

    public BlockPos getRebreakPos() {
        return rebreakPos;
    }

    public Direction getRebreakSide() {
        return rebreakSide;
    }

    public void setRebreakPos(BlockPos pos, Direction side) {
        this.rebreakPos = pos != null ? pos.immutable() : null;
        this.rebreakSide = side != null ? side : Direction.UP;
    }

    public void clearRebreakPos() {
        this.rebreakPos = null;
        this.rebreakSide = Direction.UP;
    }

    public boolean isMining() {
        return (primaryTask != null && !primaryTask.isCompleted())
                || (secondaryTask != null && !secondaryTask.isCompleted());
    }

    public boolean isMining(BlockPos pos) {
        if (pos == null) return false;
        return (primaryTask != null && pos.equals(primaryTask.getPos()) && !primaryTask.isCompleted())
                || (secondaryTask != null && pos.equals(secondaryTask.getPos()) && !secondaryTask.isCompleted());
    }

    /**
     * Starts or updates mining on a block.
     */
    public MiningTask startMining(BlockPos pos, Direction side, boolean isPrimary, float speedMultiplier) {
        return startMining(pos, side, isPrimary, speedMultiplier, 0.0f);
    }

    /**
     * Starts or updates mining on a block with initial progress.
     */
    public MiningTask startMining(BlockPos pos, Direction side, boolean isPrimary, float speedMultiplier, float startProgress) {
        if (pos == null || mc.level == null || mc.player == null) return null;

        MiningTask current = isPrimary ? primaryTask : secondaryTask;
        if (current != null && current.getPos().equals(pos)) {
            current.setSpeedMultiplier(speedMultiplier);
            return current;
        }

        if (current != null) {
            abortMining(isPrimary);
        }

        Direction effectiveSide = side != null ? side : findBestFace(mc.player, pos);
        MiningTask task = new MiningTask(pos, effectiveSide);
        task.setSpeedMultiplier(speedMultiplier);
        if (startProgress > 0.0f) {
            task.setProgress(startProgress);
        }

        // Send START_DESTROY_BLOCK and STOP_DESTROY_BLOCK for packet-based breaking
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    effectiveSide
            ));
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    effectiveSide
            ));
        }
        task.setStarted(true);

        if (isPrimary) {
            primaryTask = task;
        } else {
            secondaryTask = task;
        }

        return task;
    }

    /**
     * Ticks the mining tasks: updates progress using the best available tool, and checks for completion.
     */
    public void tick(LocalPlayer player, boolean silentSwitch, boolean autoSwitch, boolean silentRotate,
                     boolean swing, float breakThreshold, float maxRange, Object owner) {
        if (player == null || mc.level == null) return;

        if (primaryTask != null) {
            tickTask(player, primaryTask, true, silentSwitch, autoSwitch, silentRotate, swing, breakThreshold, maxRange, owner);
        }
        if (secondaryTask != null) {
            tickTask(player, secondaryTask, false, silentSwitch, autoSwitch, silentRotate, swing, breakThreshold, maxRange, owner);
        }
    }

    private void tickTask(LocalPlayer player, MiningTask task, boolean isPrimary,
                          boolean silentSwitch, boolean autoSwitch, boolean silentRotate,
                          boolean swing, float breakThreshold, float maxRange, Object owner) {
        BlockPos pos = task.getPos();

        // Distance check
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > maxRange) {
            abortMining(isPrimary);
            return;
        }

        BlockState state = mc.level.getBlockState(pos);

        // If block became air, it has been broken
        if (state.isAir()) {
            rebreakCache.put(pos, System.currentTimeMillis());
            rebreakPos = pos;
            rebreakSide = task.getSide();
            task.setCompleted(true);
            if (isPrimary) {
                primaryTask = null;
            } else {
                secondaryTask = null;
            }
            InventorySwap.INSTANCE.releaseHotbar(owner);
            return;
        }

        // Find best tool for destruction progress
        int bestSlot = MiningDamageCalculator.findBestHotbarTool(player, state, pos);
        task.setBestToolSlot(bestSlot);
        ItemStack tool = (bestSlot >= 0 && bestSlot < 9) ? player.getInventory().getItem(bestSlot) : player.getMainHandItem();

        if (autoSwitch && !silentSwitch && bestSlot >= 0 && bestSlot < 9 && InventorySwap.INSTANCE.clientSelectedSlot() != bestSlot) {
            InventorySwap.INSTANCE.selectHotbar(bestSlot);
        }

        task.tickProgress(player, tool, state);

        // Check if ready to break
        if (task.getProgress() >= breakThreshold) {
            finishMining(task, isPrimary, silentSwitch, autoSwitch, silentRotate, swing, owner);
        }
    }

    /**
     * Finishes breaking the block by silently leasing the best tool and sending STOP_DESTROY_BLOCK.
     */
    public void finishMining(MiningTask task, boolean isPrimary, boolean silentSwitch, boolean autoSwitch,
                             boolean silentRotate, boolean swing, Object owner) {
        if (task == null || mc.getConnection() == null || mc.player == null) return;
        BlockPos pos = task.getPos();

        int toolSlot = task.getBestToolSlot();
        if (toolSlot >= 0 && toolSlot < 9 && silentSwitch) {
            InventorySwap.INSTANCE.leaseHotbar(owner, toolSlot, 2);
        } else if (toolSlot >= 0 && toolSlot < 9 && autoSwitch) {
            InventorySwap.INSTANCE.selectHotbar(toolSlot);
        }

        if (silentRotate) {
            Rotation rot = Rotation.lookingAt(Vec3.atCenterOf(pos), mc.player.getEyePosition());
            RotationManager.INSTANCE.snapServerRotation(rot, 25, owner, 1);
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                task.getSide()
        ));

        if (swing) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        rebreakCache.put(pos, System.currentTimeMillis());
        rebreakPos = pos;
        rebreakSide = task.getSide();
        task.setCompleted(true);

        if (isPrimary) {
            primaryTask = null;
        } else {
            secondaryTask = null;
        }

        InventorySwap.INSTANCE.releaseHotbar(owner);
    }

    /**
     * Handles instant rebreak (CivBreak) on a previously broken block if a new block is placed there.
     */
    public boolean handleInstantRebreak(LocalPlayer player, float maxRange, boolean silentSwitch,
                                         boolean autoSwitch, boolean silentRotate, boolean swing, Object owner) {
        if (rebreakPos == null || player == null || mc.level == null || mc.getConnection() == null) {
            return false;
        }

        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(rebreakPos)) > maxRange) {
            rebreakPos = null;
            return false;
        }

        BlockState state = mc.level.getBlockState(rebreakPos);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK || state.getDestroySpeed(mc.level, rebreakPos) < 0) {
            return false;
        }

        int toolSlot = MiningDamageCalculator.findBestHotbarTool(player, state, rebreakPos);
        if (toolSlot >= 0 && toolSlot < 9 && silentSwitch) {
            InventorySwap.INSTANCE.leaseHotbar(owner, toolSlot, 2);
        } else if (toolSlot >= 0 && toolSlot < 9 && autoSwitch) {
            InventorySwap.INSTANCE.selectHotbar(toolSlot);
        }

        if (silentRotate) {
            Rotation rot = Rotation.lookingAt(Vec3.atCenterOf(rebreakPos), player.getEyePosition());
            RotationManager.INSTANCE.snapServerRotation(rot, 25, owner, 1);
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                rebreakPos,
                rebreakSide
        ));

        if (swing) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        InventorySwap.INSTANCE.releaseHotbar(owner);
        return true;
    }

    /**
     * Sends immediate break packets for instant mode.
     */
    public void instantBreak(BlockPos pos, Direction side, boolean silentSwitch, boolean autoSwitch,
                             boolean silentRotate, boolean swing, Object owner) {
        if (pos == null || mc.getConnection() == null || mc.player == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return;

        Direction effectiveSide = side != null ? side : findBestFace(mc.player, pos);
        int toolSlot = MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);

        if (toolSlot >= 0 && toolSlot < 9 && silentSwitch) {
            InventorySwap.INSTANCE.leaseHotbar(owner, toolSlot, 2);
        } else if (toolSlot >= 0 && toolSlot < 9 && autoSwitch) {
            InventorySwap.INSTANCE.selectHotbar(toolSlot);
        }

        if (silentRotate) {
            Rotation rot = Rotation.lookingAt(Vec3.atCenterOf(pos), mc.player.getEyePosition());
            RotationManager.INSTANCE.snapServerRotation(rot, 25, owner, 1);
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                effectiveSide
        ));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                effectiveSide
        ));

        if (swing) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        InventorySwap.INSTANCE.releaseHotbar(owner);
    }

    public void abortMining(boolean isPrimary) {
        MiningTask task = isPrimary ? primaryTask : secondaryTask;
        if (task != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    task.getPos(),
                    task.getSide()
            ));
        }
        if (isPrimary) {
            primaryTask = null;
        } else {
            secondaryTask = null;
        }
    }

    public void abortAllMining() {
        abortMining(true);
        abortMining(false);
    }

    public void reset() {
        abortAllMining();
        primaryTask = null;
        secondaryTask = null;
        rebreakPos = null;
        rebreakSide = Direction.UP;
        rebreakCache.clear();
    }

    public boolean isRebreakCached(BlockPos pos, long maxAgeMs) {
        if (pos == null) return false;
        Long time = rebreakCache.get(pos);
        if (time == null) return false;
        return System.currentTimeMillis() - time <= maxAgeMs;
    }

    /**
     * Checks if a block can be broken instantly with the specified tool.
     *
     * @param pos the block position
     * @param tool the tool stack to check with
     * @return true if destroy progress is greater than or equal to 1.0f
     */
    public boolean isInstantBreak(BlockPos pos, ItemStack tool) {
        if (pos == null || mc.player == null || mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        float progress = MiningDamageCalculator.calculateDestroyProgress(
                mc.player,
                tool != null ? tool : ItemStack.EMPTY,
                state,
                pos
        );
        return progress >= 1.0f;
    }

    /**
     * Checks if a block can be packet-broken.
     * Requires the position to be non-null, within vanilla block reach of the player,
     * and not unbreakable (such as bedrock or air).
     *
     * @param pos the block position
     * @return true if the block can be targeted for packet breaking
     */
    public boolean canPacketBreak(BlockPos pos) {
        if (pos == null || mc.player == null || mc.level == null) {
            return false;
        }
        if (!InteractionUtil.isInReach(mc.player, Vec3.atCenterOf(pos), InteractionUtil.VANILLA_BLOCK_REACH)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(mc.level, pos) < 0.0f) {
            return false;
        }
        return true;
    }

    /**
     * Determines the most visible block face relative to player eye position.
     */
    public static Direction findBestFace(LocalPlayer player, BlockPos pos) {
        if (player == null || pos == null) return Direction.UP;
        Vec3 eyes = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);

        double dx = eyes.x - center.x;
        double dy = eyes.y - center.y;
        double dz = eyes.z - center.z;

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absY >= absX && absY >= absZ) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX >= absZ) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
