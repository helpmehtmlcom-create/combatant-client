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
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.modules.player.SpeedMine;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        id = "automine",
        displayName = "AutoMine",
        aliases = {"autocity", "city"},
        category = ModuleCategory.COMBAT
)
public final class AutoMine extends Module {

    public enum Mode {
        CITY,
        SURROUND,
        BURROW
    }

    public enum MineMode implements EnumValue.IdProvider {
        NORMAL("normal"),
        FAST("fast"),
        INSTANT("instant");

        private final String id;

        MineMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("automineMode", "mode", Mode.CITY, Mode.values());
    private final EnumValue<MineMode> mineMode = enumSetting("automineMineMode", "mine_mode", MineMode.FAST, MineMode.values());
    private final NumberValue<Float> fastSpeed = visibleWhen(num("automineFastSpeed", "fast_speed", 1.5f, 1.1f, 3.0f), () -> mineMode.get() == MineMode.FAST);
    private final BooleanValue rebreak = bool("automineRebreak", "rebreak", true);
    private final BooleanValue doubleMine = bool("automineDoubleMine", "double_mine", true);
    private final NumberValue<Float> range = num("automineRange", "range", 5.0f, 2.0f, 7.0f);
    private final BooleanValue selfCheck = bool("automineSelfCheck", "self_check", true);
    private final BooleanValue render = bool("automineRender", "render", true);
    private final RGBAColorValue fillColor = color("automineFillColor", "#D6303155");
    private final RGBAColorValue lineColor = color("automineLineColor", "#FF7675FF");
    private final RGBAColorValue secondaryFillColor = color("automineSecondaryFillColor", "#0984E355");
    private final RGBAColorValue secondaryLineColor = color("automineSecondaryLineColor", "#74B9FFFF");

    private BlockPos targetBlock = null;
    private float progress = 0.0f;
    private BlockPos secondaryTargetBlock = null;
    private float secondaryProgress = 0.0f;

    private BlockPos rebreakPos = null;
    private float rebreakProgress = 0.0f;
    private BlockPos secondaryRebreakPos = null;
    private float secondaryRebreakProgress = 0.0f;

    private Player currentTarget = null;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        abortMining(targetBlock);
        abortMining(secondaryTargetBlock);
        targetBlock = null;
        secondaryTargetBlock = null;
        rebreakPos = null;
        secondaryRebreakPos = null;
        progress = 0.0f;
        secondaryProgress = 0.0f;
        rebreakProgress = 0.0f;
        secondaryRebreakProgress = 0.0f;
        currentTarget = null;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            reset();
            return;
        }

        LocalPlayer player = mc.player;

        if (rebreak.get()) {
            handleRebreak(player);
        }

        currentTarget = findTargetPlayer();
        if (currentTarget == null) {
            if (targetBlock != null) {
                abortMining(targetBlock);
                targetBlock = null;
                progress = 0.0f;
            }
            if (secondaryTargetBlock != null) {
                abortMining(secondaryTargetBlock);
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
            return;
        }

        List<BlockPos> bestBlocks = findBestBlocks(currentTarget);
        BlockPos bestPrimary = !bestBlocks.isEmpty() ? bestBlocks.get(0) : null;
        BlockPos bestSecondary = (doubleMine.get() && bestBlocks.size() > 1) ? bestBlocks.get(1) : null;

        processTarget(bestPrimary, true);

        if (doubleMine.get()) {
            processTarget(bestSecondary, false);
        } else if (secondaryTargetBlock != null) {
            abortMining(secondaryTargetBlock);
            secondaryTargetBlock = null;
            secondaryProgress = 0.0f;
        }
    }

    private void processTarget(BlockPos best, boolean isPrimary) {
        BlockPos current = isPrimary ? targetBlock : secondaryTargetBlock;

        if (best == null) {
            if (current != null) {
                abortMining(current);
                if (isPrimary) {
                    targetBlock = null;
                    progress = 0.0f;
                } else {
                    secondaryTargetBlock = null;
                    secondaryProgress = 0.0f;
                }
            }
            return;
        }

        if (!best.equals(current)) {
            if (current != null) {
                abortMining(current);
            }
            if (isPrimary) {
                targetBlock = best;
                progress = 0.0f;
            } else {
                secondaryTargetBlock = best;
                secondaryProgress = 0.0f;
            }

            startMiningTarget(best);
        } else {
            updateMiningProgress(best, isPrimary);
        }
    }

    private void startMiningTarget(BlockPos pos) {
        if (pos == null || mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !isBreakable(state, pos)) return;

        if (mineMode.get() == MineMode.INSTANT) {
            instantBreak(pos);
        } else {
            startMining(pos);
        }
    }

    private void updateMiningProgress(BlockPos pos, boolean isPrimary) {
        if (pos == null || mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            if (rebreak.get()) {
                if (isPrimary) {
                    rebreakPos = pos;
                    rebreakProgress = 0.0f;
                } else {
                    secondaryRebreakPos = pos;
                    secondaryRebreakProgress = 0.0f;
                }
            }
            if (isPrimary) {
                targetBlock = null;
                progress = 0.0f;
            } else {
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        if (mineMode.get() == MineMode.INSTANT) {
            instantBreak(pos);
            return;
        }

        float delta = state.getDestroyProgress(mc.player, mc.level, pos);
        if (mineMode.get() == MineMode.FAST) {
            delta *= fastSpeed.get();
        }

        if (isPrimary) {
            progress += delta;
            if (progress >= 1.0f) {
                finishBreak(pos);
                if (rebreak.get()) {
                    rebreakPos = pos;
                    rebreakProgress = 0.0f;
                }
                targetBlock = null;
                progress = 0.0f;
            }
        } else {
            secondaryProgress += delta;
            if (secondaryProgress >= 1.0f) {
                finishBreak(pos);
                if (rebreak.get()) {
                    secondaryRebreakPos = pos;
                    secondaryRebreakProgress = 0.0f;
                }
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
        }
    }

    private void handleRebreak(LocalPlayer player) {
        if (rebreakPos != null) {
            handleSingleRebreak(player, rebreakPos, true);
        }
        if (doubleMine.get() && secondaryRebreakPos != null) {
            handleSingleRebreak(player, secondaryRebreakPos, false);
        }
    }

    private void handleSingleRebreak(LocalPlayer player, BlockPos pos, boolean isPrimary) {
        if (pos == null || mc.level == null) return;
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > range.get()) {
            if (isPrimary) rebreakPos = null;
            else secondaryRebreakPos = null;
            return;
        }

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        if (!isBreakable(state, pos)) {
            if (isPrimary) rebreakPos = null;
            else secondaryRebreakPos = null;
            return;
        }

        if (mineMode.get() == MineMode.INSTANT) {
            instantBreak(pos);
        } else {
            float delta = state.getDestroyProgress(player, mc.level, pos);
            if (mineMode.get() == MineMode.FAST) {
                delta *= fastSpeed.get();
            }
            if (isPrimary) {
                rebreakProgress += delta;
                if (rebreakProgress >= 1.0f) {
                    finishBreak(pos);
                    rebreakProgress = 0.0f;
                }
            } else {
                secondaryRebreakProgress += delta;
                if (secondaryRebreakProgress >= 1.0f) {
                    finishBreak(pos);
                    secondaryRebreakProgress = 0.0f;
                }
            }
        }
    }

    private void instantBreak(BlockPos pos) {
        if (mc.getConnection() == null || mc.player == null || pos == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        int toolSlot = findBestHotbarTool(pos);
        if (toolSlot >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        mc.player.swing(InteractionHand.MAIN_HAND);
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void startMining(BlockPos pos) {
        if (mc.getConnection() == null || pos == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        if (mc.player != null) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void finishBreak(BlockPos pos) {
        if (mc.getConnection() == null || mc.player == null || pos == null) return;
        int toolSlot = findBestHotbarTool(pos);
        if (toolSlot >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        mc.player.swing(InteractionHand.MAIN_HAND);
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void abortMining(BlockPos pos) {
        if (mc.getConnection() == null || pos == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
    }

    private Player findTargetPlayer() {
        if (mc.level == null || mc.player == null) return null;
        Player best = null;
        double bestDistSq = range.get() * range.get();

        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            double distSq = p.distanceToSqr(mc.player);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = p;
            }
        }
        return best;
    }

    private List<BlockPos> findBestBlocks(Player target) {
        if (mc.level == null || mc.player == null || target == null) return List.of();

        BlockPos targetFeet = target.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();

        switch (mode.get()) {
            case BURROW -> {
                BlockState state = mc.level.getBlockState(targetFeet);
                if (isBreakable(state, targetFeet)) {
                    candidates.add(targetFeet);
                }
                if (doubleMine.get()) {
                    BlockPos headPos = targetFeet.above();
                    BlockState headState = mc.level.getBlockState(headPos);
                    if (isBreakable(headState, headPos)) {
                        candidates.add(headPos);
                    }
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos surroundPos = targetFeet.relative(dir);
                        BlockState sState = mc.level.getBlockState(surroundPos);
                        if (isBreakable(sState, surroundPos) && !candidates.contains(surroundPos)) {
                            candidates.add(surroundPos);
                        }
                    }
                }
            }
            case CITY -> {
                Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                for (Direction dir : directions) {
                    BlockPos surroundPos = targetFeet.relative(dir);
                    BlockState state = mc.level.getBlockState(surroundPos);
                    if (!isBreakable(state, surroundPos)) continue;

                    candidates.add(surroundPos);
                }
            }
            case SURROUND -> {
                Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                for (Direction dir : directions) {
                    BlockPos pos = targetFeet.relative(dir);
                    BlockState state = mc.level.getBlockState(pos);
                    if (isBreakable(state, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }

        if (candidates.isEmpty()) return List.of();

        BlockPos selfFeet = mc.player.blockPosition();
        if (selfCheck.get()) {
            candidates.removeIf(p -> {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (selfFeet.relative(dir).equals(p)) return true;
                }
                return selfFeet.equals(p);
            });
        }

        if (candidates.isEmpty()) return List.of();

        Vec3 eyePos = mc.player.getEyePosition();
        candidates.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(eyePos)));

        float r = range.get();
        List<BlockPos> inRange = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (eyePos.distanceTo(Vec3.atCenterOf(candidate)) <= r) {
                inRange.add(candidate);
                if (!doubleMine.get() && inRange.size() >= 1) break;
                if (doubleMine.get() && inRange.size() >= 2) break;
            }
        }
        return inRange;
    }

    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        return state.getDestroySpeed(mc.level, pos) >= 0;
    }

    public int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return -1;

        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            float s = stack.getDestroySpeed(state);
            if (s > bestSpeed) {
                bestSpeed = s;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        renderBlock(renderer, targetBlock, progress, fillColor.getArgb(), lineColor.getArgb());

        if (doubleMine.get() && secondaryTargetBlock != null) {
            renderBlock(renderer, secondaryTargetBlock, secondaryProgress, secondaryFillColor.getArgb(), secondaryLineColor.getArgb());
        }

        if (rebreak.get()) {
            if (rebreakPos != null && !rebreakPos.equals(targetBlock)) {
                renderBlock(renderer, rebreakPos, rebreakProgress, fillColor.getArgb(), lineColor.getArgb());
            }
            if (secondaryRebreakPos != null && !secondaryRebreakPos.equals(secondaryTargetBlock)) {
                renderBlock(renderer, secondaryRebreakPos, secondaryRebreakProgress, secondaryFillColor.getArgb(), secondaryLineColor.getArgb());
            }
        }
    }

    private void renderBlock(Renderer3D renderer, BlockPos pos, float currentProgress, int fill, int line) {
        if (pos == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        AABB fullBox = new AABB(pos);
        float p = Math.min(1.0f, Math.max(0.0f, currentProgress));
        AABB renderBox = fullBox.deflate((1.0 - p) * 0.5);

        ExplosionRenderUtil.addFilledBox(renderer, renderBox, fill);
        ExplosionRenderUtil.addOutlineBox(renderer, renderBox, line);
    }

    public BlockPos getTargetBlock() {
        return targetBlock;
    }

    public BlockPos getSecondaryTargetBlock() {
        return secondaryTargetBlock;
    }

    public float getProgress() {
        return progress;
    }

    public float getSecondaryProgress() {
        return secondaryProgress;
    }
}
