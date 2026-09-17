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
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockMiningSystem {

    public static final BlockMiningSystem INSTANCE = new BlockMiningSystem();

    private final Minecraft mc = Minecraft.getInstance();

    private MiningTask primaryTask = null;
    private MiningTask secondaryTask = null;

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
     * Starts or updates mining on a block. If the task is already running on the same position, it continues.
     */
    public MiningTask startMining(BlockPos pos, Direction side, boolean isPrimary, float speedMultiplier) {
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

        // Send START_DESTROY_BLOCK packet
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
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
    public void tick(LocalPlayer player, boolean silentSwitch, boolean swing, float breakThreshold) {
        if (player == null || mc.level == null) return;

        if (primaryTask != null) {
            tickTask(player, primaryTask, true, silentSwitch, swing, breakThreshold);
        }
        if (secondaryTask != null) {
            tickTask(player, secondaryTask, false, silentSwitch, swing, breakThreshold);
        }
    }

    private void tickTask(LocalPlayer player, MiningTask task, boolean isPrimary,
                          boolean silentSwitch, boolean swing, float breakThreshold) {
        BlockPos pos = task.getPos();
        BlockState state = mc.level.getBlockState(pos);

        // If block became air, it has been broken
        if (state.isAir()) {
            rebreakCache.put(pos, System.currentTimeMillis());
            task.setCompleted(true);
            if (isPrimary) {
                primaryTask = null;
            } else {
                secondaryTask = null;
            }
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        // Find the best tool to use for progress calculation
        int bestSlot = MiningDamageCalculator.findBestHotbarTool(player, state, pos);
        task.setBestToolSlot(bestSlot);
        ItemStack tool = (bestSlot >= 0 && bestSlot < 9) ? player.getInventory().getItem(bestSlot) : ItemStack.EMPTY;

        task.tickProgress(player, tool, state);

        // Check if ready to break
        if (task.getProgress() >= breakThreshold) {
            finishMining(task, isPrimary, silentSwitch, swing);
        }
    }

    /**
     * Finishes breaking the block by silently leasing the best tool and sending STOP_DESTROY_BLOCK.
     */
    public void finishMining(MiningTask task, boolean isPrimary, boolean silentSwitch, boolean swing) {
        if (task == null || mc.getConnection() == null || mc.player == null) return;
        BlockPos pos = task.getPos();

        int toolSlot = task.getBestToolSlot();
        if (toolSlot >= 0 && toolSlot < 9 && silentSwitch) {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
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
        task.setCompleted(true);

        if (isPrimary) {
            primaryTask = null;
        } else {
            secondaryTask = null;
        }
    }

    /**
     * Instant rebreak (CivBreak): sends STOP_DESTROY_BLOCK on an already-broken or cached position
     * when a new block is placed there.
     */
    public boolean instantRebreak(BlockPos pos, Direction side, boolean silentSwitch, boolean swing) {
        if (pos == null || mc.getConnection() == null || mc.player == null || mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return false;

        Direction effectiveSide = side != null ? side : findBestFace(mc.player, pos);
        int toolSlot = MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
        if (toolSlot >= 0 && toolSlot < 9 && silentSwitch) {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
        }

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                effectiveSide
        ));

        if (swing) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
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
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    public void reset() {
        abortMining(true);
        abortMining(false);
        primaryTask = null;
        secondaryTask = null;
        rebreakCache.clear();
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    public boolean isRebreakCached(BlockPos pos, long maxAgeMs) {
        if (pos == null) return false;
        Long time = rebreakCache.get(pos);
        if (time == null) return false;
        return System.currentTimeMillis() - time <= maxAgeMs;
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
