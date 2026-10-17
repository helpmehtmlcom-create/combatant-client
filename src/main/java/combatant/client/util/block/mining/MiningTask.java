/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningTask {

    private final BlockPos pos;
    private Direction side;
    private float progress;
    private int bestToolSlot = -1;
    private boolean started;
    private boolean completed;
    private boolean rebreak;
    private long startTimeMs;
    private int elapsedTicks;
    private float speedMultiplier = 1.0f;

    public MiningTask(BlockPos pos, Direction side) {
        this.pos = pos.immutable();
        this.side = side != null ? side : Direction.UP;
        this.progress = 0.0f;
        this.started = false;
        this.completed = false;
        this.rebreak = false;
        this.startTimeMs = System.currentTimeMillis();
        this.elapsedTicks = 0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getSide() {
        return side;
    }

    public void setSide(Direction side) {
        this.side = side != null ? side : Direction.UP;
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = Math.min(1.0f, Math.max(0.0f, progress));
    }

    public int getBestToolSlot() {
        return bestToolSlot;
    }

    public void setBestToolSlot(int bestToolSlot) {
        this.bestToolSlot = bestToolSlot;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isRebreak() {
        return rebreak;
    }

    public void setRebreak(boolean rebreak) {
        this.rebreak = rebreak;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.speedMultiplier = Math.max(0.1f, speedMultiplier);
    }

    /**
     * Ticks mining progress using the best available tool or specified tool.
     */
    public float tickProgress(Player player, ItemStack tool, BlockState state) {
        elapsedTicks++;
        if (state == null || state.isAir()) {
            return progress;
        }

        float delta = MiningDamageCalculator.calculateDestroyProgress(player, tool, state, pos) * speedMultiplier;
        progress = Math.min(1.0f, progress + delta);
        return progress;
    }

    public void reset() {
        progress = 0.0f;
        started = false;
        completed = false;
        elapsedTicks = 0;
        startTimeMs = System.currentTimeMillis();
    }
}
