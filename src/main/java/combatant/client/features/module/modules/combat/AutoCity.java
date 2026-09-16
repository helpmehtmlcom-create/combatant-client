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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
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
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.block.mining.MiningDamageCalculator;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "autocity",
        displayName = "AutoCity",
        aliases = {"cityboss", "citybreaker"},
        category = ModuleCategory.COMBAT,
        description = "Automatically breaks enemy surround blocks to expose them for crystal damage."
)
public final class AutoCity extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("autocityRange", "range", 4.5, 2.0, 6.0);
    private final NumberValue<Double> breakRange = num("autocityBreakRange", "break_range", 4.5, 2.0, 6.0);
    private final BooleanValue autoTool = bool("autocityAutoTool", "auto_tool", true);
    private final BooleanValue render = bool("autocityRender", "render", true);
    private final RGBAColorValue fillColor = color("autocityFillColor", "fill_color", "#E74C3C55");
    private final RGBAColorValue lineColor = color("autocityLineColor", "line_color", "#C0392BFF");

    private Player currentTarget = null;
    private BlockPos targetBlock = null;
    private float progress = 0.0f;
    private boolean miningStarted = false;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        if (targetBlock != null) {
            abortMining(targetBlock);
        }
        targetBlock = null;
        currentTarget = null;
        progress = 0.0f;
        miningStarted = false;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) {
            reset();
            return;
        }

        LocalPlayer player = mc.player;
        Level level = mc.level;

        // 1. Find nearest enemy player who is standing in a hole or surround
        currentTarget = findTargetPlayer(player, level);
        if (currentTarget == null) {
            if (targetBlock != null) {
                reset();
            }
            return;
        }

        // 2. Evaluate and select the optimal surround block
        BlockPos bestBlock = findBestCityBlock(player, level, currentTarget);
        if (bestBlock == null) {
            if (targetBlock != null) {
                reset();
            }
            return;
        }

        // 3. Interface with SpeedMine or perform direct packet mining
        processMining(bestBlock);
    }

    private Player findTargetPlayer(LocalPlayer player, Level level) {
        Player best = null;
        double bestDistSq = range.get() * range.get();

        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;

            CategoryType type = CategoryRules.determine(other.getGameProfile().name());
            if (type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq > bestDistSq) continue;

            if (!isStandingInHoleOrSurround(level, other)) continue;
            if (getCityableSurroundBlocks(player, level, other).isEmpty()) continue;

            bestDistSq = distSq;
            best = other;
        }

        return best;
    }

    private boolean isStandingInHoleOrSurround(Level level, Player target) {
        if (level == null || target == null) return false;
        BlockPos feet = target.blockPosition();

        int resistantCount = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos surroundPos = feet.relative(dir);
            BlockState state = level.getBlockState(surroundPos);
            if (isBlastResistantOrSurround(state, surroundPos)) {
                resistantCount++;
            }
        }
        return resistantCount >= 3;
    }

    private boolean isBlastResistantOrSurround(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        Block b = state.getBlock();
        return b == Blocks.BEDROCK
                || b == Blocks.OBSIDIAN
                || b == Blocks.CRYING_OBSIDIAN
                || b == Blocks.ENDER_CHEST
                || b == Blocks.NETHERITE_BLOCK
                || b == Blocks.RESPAWN_ANCHOR
                || b == Blocks.ANVIL
                || b == Blocks.CHIPPED_ANVIL
                || b == Blocks.DAMAGED_ANVIL;
    }

    private List<BlockPos> getCityableSurroundBlocks(LocalPlayer player, Level level, Player target) {
        if (player == null || level == null || target == null) return List.of();
        BlockPos feet = target.blockPosition();
        List<BlockPos> validBlocks = new ArrayList<>();

        // Inspect 4 surround positions: (x+1, y, z), (x-1, y, z), (x, y, z+1), (x, y, z-1)
        Direction[] directions = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        Vec3 eyePos = player.getEyePosition();
        double maxBreakRangeSq = breakRange.get() * breakRange.get();

        for (Direction dir : directions) {
            BlockPos surroundPos = feet.relative(dir);

            if (eyePos.distanceToSqr(Vec3.atCenterOf(surroundPos)) > maxBreakRangeSq) {
                continue;
            }

            BlockState state = level.getBlockState(surroundPos);
            if (!isBreakableCityBlock(state, surroundPos)) {
                continue;
            }

            if (!hasCrystalDamageSpace(level, surroundPos, dir)) {
                continue;
            }

            validBlocks.add(surroundPos);
        }

        return validBlocks;
    }

    private boolean isBreakableCityBlock(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        if (state.getDestroySpeed(mc.level, pos) < 0) return false;

        Block b = state.getBlock();
        return b == Blocks.OBSIDIAN
                || b == Blocks.ENDER_CHEST
                || b == Blocks.NETHERITE_BLOCK
                || b == Blocks.CRYING_OBSIDIAN
                || b == Blocks.RESPAWN_ANCHOR;
    }

    private boolean hasCrystalDamageSpace(Level level, BlockPos surroundPos, Direction dir) {
        if (level == null || surroundPos == null || dir == null) return false;

        BlockPos above = surroundPos.above();
        BlockPos outer = surroundPos.relative(dir);
        BlockPos outerAbove = outer.above();

        boolean aboveOpen = level.getBlockState(above).isAir() || level.getBlockState(above).canBeReplaced();
        boolean outerOpen = level.getBlockState(outer).isAir() || level.getBlockState(outer).canBeReplaced();
        boolean outerAboveOpen = level.getBlockState(outerAbove).isAir() || level.getBlockState(outerAbove).canBeReplaced();

        if (outerOpen && outerAboveOpen) {
            return true;
        }

        if (aboveOpen) {
            return true;
        }

        return outerOpen;
    }

    private BlockPos findBestCityBlock(LocalPlayer player, Level level, Player target) {
        List<BlockPos> candidates = getCityableSurroundBlocks(player, level, target);
        if (candidates.isEmpty()) return null;

        // If current target block is still a valid candidate, stick to it to maintain mining progress
        if (targetBlock != null && candidates.contains(targetBlock)) {
            BlockState currentState = level.getBlockState(targetBlock);
            if (isBreakableCityBlock(currentState, targetBlock)) {
                return targetBlock;
            }
        }

        Vec3 eyePos = player.getEyePosition();

        // Sort candidates:
        // 1. Prioritize faster-to-break blocks (e.g. Ender Chest has hardness 22.5 vs Obsidian 50.0)
        // 2. Closer distance to player's eye position
        candidates.sort((posA, posB) -> {
            BlockState stateA = level.getBlockState(posA);
            BlockState stateB = level.getBlockState(posB);
            float speedA = stateA.getDestroySpeed(level, posA);
            float speedB = stateB.getDestroySpeed(level, posB);

            if (Float.compare(speedA, speedB) != 0) {
                return Float.compare(speedA, speedB);
            }

            double distA = eyePos.distanceToSqr(Vec3.atCenterOf(posA));
            double distB = eyePos.distanceToSqr(Vec3.atCenterOf(posB));
            return Double.compare(distA, distB);
        });

        return candidates.get(0);
    }

    private void processMining(BlockPos best) {
        if (best == null || mc.level == null || mc.player == null || mc.getConnection() == null) return;

        SpeedMine speedMine = Modules.get(SpeedMine.class);
        if (speedMine != null && speedMine.isEnabled()) {
            targetBlock = best;
            if (!best.equals(speedMine.getMiningPos())) {
                speedMine.startMining(best, Direction.UP);
            }
            progress = speedMine.getMiningProgress();
            return;
        }

        if (targetBlock == null || !targetBlock.equals(best)) {
            if (targetBlock != null) {
                abortMining(targetBlock);
            }
            targetBlock = best;
            progress = 0.0f;
            miningStarted = false;
            startMiningTarget(best);
        } else {
            updateMiningProgress(best);
        }
    }

    private void startMiningTarget(BlockPos pos) {
        if (pos == null || mc.getConnection() == null || mc.level == null || mc.player == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !isBreakableCityBlock(state, pos)) return;

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                Direction.UP
        ));
        mc.player.swing(InteractionHand.MAIN_HAND);
        miningStarted = true;
    }

    private void updateMiningProgress(BlockPos pos) {
        if (pos == null || mc.level == null || mc.player == null || mc.getConnection() == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            reset();
            return;
        }

        if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > breakRange.get()) {
            reset();
            return;
        }

        float delta = calculateDestroyProgress(state, pos);
        progress += delta;

        if (progress >= 1.0f) {
            finishBreak(pos);
            reset();
        }
    }

    private float calculateDestroyProgress(BlockState state, BlockPos pos) {
        if (mc.player == null || mc.level == null) return 0.0f;

        int toolSlot = findBestHotbarTool(pos);
        ItemStack tool = (toolSlot >= 0 && autoTool.get())
                ? mc.player.getInventory().getItem(toolSlot)
                : mc.player.getMainHandItem();

        return MiningDamageCalculator.calculateDestroyProgress(mc.player, tool, state, pos);
    }

    private void finishBreak(BlockPos pos) {
        if (mc.getConnection() == null || mc.player == null || pos == null) return;

        int toolSlot = findBestHotbarTool(pos);
        if (toolSlot >= 0 && autoTool.get()) {
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

    private int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        return MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;
        if (targetBlock == null) return;

        BlockState state = mc.level.getBlockState(targetBlock);
        if (state.isAir()) return;

        AABB fullBox = new AABB(targetBlock);
        float p = Math.min(1.0f, Math.max(0.0f, progress));
        AABB renderBox = fullBox.deflate((1.0 - p) * 0.5);

        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();

        ExplosionRenderUtil.addFilledBox(renderer, renderBox, fill);
        ExplosionRenderUtil.addOutlineBox(renderer, renderBox, line);
    }

    public Player getCurrentTarget() {
        return currentTarget;
    }

    public BlockPos getTargetBlock() {
        return targetBlock;
    }

    public float getProgress() {
        return progress;
    }
}
