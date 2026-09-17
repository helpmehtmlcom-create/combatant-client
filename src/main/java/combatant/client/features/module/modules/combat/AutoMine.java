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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.block.mining.BlockMiningSystem;
import combatant.client.util.block.mining.MiningDamageCalculator;
import combatant.client.util.block.mining.MiningTask;
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
        SMART,
        CITY,
        SURROUND,
        BURROW,
        ANTI_CEV,
        SELF_WEB
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

    private final NumberValue<Float> speed = num("automineSpeed", "speed", 1.5f, 1.0f, 3.0f);
    private final NumberValue<Float> range = num("automineRange", "range", 5.0f, 2.0f, 7.0f);
    private final BooleanValue doubleMine = bool("automineDoubleMine", "double_mine", true);
    private final BooleanValue rebreak = bool("automineRebreak", "rebreak", true);
    private final BooleanValue render = bool("automineRender", "render", true);
    private final RGBAColorValue fillColor = color("automineFillColor", "#D6303155");
    private final RGBAColorValue lineColor = color("automineLineColor", "#FF7675FF");
    private final RGBAColorValue secondaryFillColor = color("automineSecondaryFillColor", "#0984E355");
    private final RGBAColorValue secondaryLineColor = color("automineSecondaryLineColor", "#74B9FFFF");

    private final BooleanValue silentSwitch = bool("automineSilentSwitch", "silent_switch", true);
    private static final float BREAK_THRESHOLD = 1.0f;
    private static final boolean SWING = true;
    private static final boolean REBREAK = true;
    private static final boolean SELF_CHECK = true;

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
        if (targetBlock != null) {
            BlockMiningSystem.INSTANCE.abortMining(true);
        }
        if (secondaryTargetBlock != null) {
            BlockMiningSystem.INSTANCE.abortMining(false);
        }
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

        // 1. Automatic instant rebreak on replacement
        handleRebreak(player);

        // 2. Select target player and candidate blocks
        currentTarget = findTargetPlayer();
        List<BlockPos> bestBlocks = (currentTarget != null) ? findBestBlocks(currentTarget) : List.of();

        // 3. Process primary target
        BlockPos bestPrimary = !bestBlocks.isEmpty() ? bestBlocks.get(0) : null;
        processPrimary(bestPrimary, player);

        // 4. Process secondary target (double mine)
        if (doubleMine.get() && bestBlocks.size() > 1) {
            BlockPos bestSecondary = bestBlocks.get(1);
            processSecondary(bestSecondary, player);
        } else if (secondaryTargetBlock != null) {
            BlockMiningSystem.INSTANCE.abortMining(false);
            secondaryTargetBlock = null;
            secondaryProgress = 0.0f;
        }

        // 5. Update active progress state from BlockMiningSystem
        MiningTask primaryTask = BlockMiningSystem.INSTANCE.getPrimaryTask();
        if (primaryTask != null) {
            targetBlock = primaryTask.getPos();
            progress = primaryTask.getProgress();
        } else if (targetBlock != null) {
            BlockState st = mc.level.getBlockState(targetBlock);
            if (st.isAir()) {
                rebreakPos = targetBlock;
                targetBlock = null;
                progress = 0.0f;
            }
        }

        MiningTask secondaryTask = BlockMiningSystem.INSTANCE.getSecondaryTask();
        if (secondaryTask != null) {
            secondaryTargetBlock = secondaryTask.getPos();
            secondaryProgress = secondaryTask.getProgress();
        } else if (secondaryTargetBlock != null) {
            BlockState st = mc.level.getBlockState(secondaryTargetBlock);
            if (st.isAir()) {
                secondaryRebreakPos = secondaryTargetBlock;
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
        }
    }

    private void processPrimary(BlockPos best, LocalPlayer player) {
        if (best == null) {
            if (targetBlock != null) {
                BlockMiningSystem.INSTANCE.abortMining(true);
                targetBlock = null;
                progress = 0.0f;
            }
            return;
        }

        float spd = speed.get();
        if (!best.equals(targetBlock)) {
            targetBlock = best;
            progress = 0.0f;
            BlockMiningSystem.INSTANCE.startMining(best, null, true, spd);
        } else {
            MiningTask task = BlockMiningSystem.INSTANCE.getPrimaryTask();
            if (task != null) {
                task.setSpeedMultiplier(spd);
                BlockMiningSystem.INSTANCE.tick(player, silentSwitch.get(), SWING, BREAK_THRESHOLD);
                progress = task.getProgress();
            }
        }
    }

    private void processSecondary(BlockPos best, LocalPlayer player) {
        if (best == null) {
            if (secondaryTargetBlock != null) {
                BlockMiningSystem.INSTANCE.abortMining(false);
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
            return;
        }

        float spd = speed.get();
        if (!best.equals(secondaryTargetBlock)) {
            secondaryTargetBlock = best;
            secondaryProgress = 0.0f;
            BlockMiningSystem.INSTANCE.startMining(best, null, false, spd);
        } else {
            MiningTask task = BlockMiningSystem.INSTANCE.getSecondaryTask();
            if (task != null) {
                task.setSpeedMultiplier(spd);
                BlockMiningSystem.INSTANCE.tick(player, silentSwitch.get(), SWING, BREAK_THRESHOLD);
                secondaryProgress = task.getProgress();
            }
        }
    }

    private void handleRebreak(LocalPlayer player) {
        if (rebreakPos != null) {
            if (isRebreakValid(player, rebreakPos)) {
                BlockMiningSystem.INSTANCE.instantRebreak(rebreakPos, null, silentSwitch.get(), SWING);
            } else {
                rebreakPos = null;
            }
        }
        if (doubleMine.get() && secondaryRebreakPos != null) {
            if (isRebreakValid(player, secondaryRebreakPos)) {
                BlockMiningSystem.INSTANCE.instantRebreak(secondaryRebreakPos, null, silentSwitch.get(), SWING);
            } else {
                secondaryRebreakPos = null;
            }
        }
    }

    private boolean isRebreakValid(LocalPlayer player, BlockPos pos) {
        if (pos == null || mc.level == null) return false;
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > range.get()) return false;
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && isBreakable(state, pos);
    }

    private void instantBreak(BlockPos pos) {
        BlockMiningSystem.INSTANCE.instantRebreak(pos, null, silentSwitch.get(), SWING);
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
        if (mc.level == null || mc.player == null) return List.of();

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos targetFeet = target != null ? target.blockPosition() : null;
        BlockPos selfFeet = mc.player.blockPosition();

        // 1. Self Web check (automatic self-defense)
        BlockState selfState = mc.level.getBlockState(selfFeet);
        if (selfState.is(Blocks.COBWEB)) {
            candidates.add(selfFeet);
        }
        BlockState selfHeadState = mc.level.getBlockState(selfFeet.above());
        if (selfHeadState.is(Blocks.COBWEB) && !candidates.contains(selfFeet.above())) {
            candidates.add(selfFeet.above());
        }
        if (!candidates.isEmpty()) {
            return candidates;
        }

        if (target == null || targetFeet == null) {
            return candidates;
        }

        // Priority A: Target Burrow
        BlockState inFeet = mc.level.getBlockState(targetFeet);
        if (isBurrowBlock(inFeet, targetFeet)) {
            candidates.add(targetFeet);
        }

        // Priority B: City Surround Blocks (blocks with adjacent air where crystal can be placed)
        List<BlockPos> cityBlocks = evaluateCityBlocks(targetFeet);
        for (BlockPos cp : cityBlocks) {
            if (!candidates.contains(cp)) {
                candidates.add(cp);
            }
        }

        // Priority C: Anti-Cev / Trap
        BlockPos headTrap = targetFeet.above(2);
        BlockState headTrapState = mc.level.getBlockState(headTrap);
        if (isBreakable(headTrapState, headTrap) && !candidates.contains(headTrap)) {
            candidates.add(headTrap);
        }

        // Priority D: General Surround
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sp = targetFeet.relative(dir);
            BlockState ss = mc.level.getBlockState(sp);
            if (isBreakable(ss, sp) && !candidates.contains(sp)) {
                candidates.add(sp);
            }
        }

        if (candidates.isEmpty()) return List.of();

        // Automatic anti-self-mine check: protect our own surround and feet
        candidates.removeIf(p -> {
            if (selfFeet.equals(p)) return true;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (selfFeet.relative(dir).equals(p)) return true;
            }
            return false;
        });

        // Filter by eye reach
        Vec3 eyePos = mc.player.getEyePosition();
        float maxRange = range.get();
        candidates.removeIf(p -> eyePos.distanceTo(Vec3.atCenterOf(p)) > maxRange);

        // Sort candidates by break speed and distance
        candidates.sort(Comparator.comparingDouble((BlockPos p) -> {
            BlockState st = mc.level.getBlockState(p);
            float dmg = MiningDamageCalculator.calculateDestroyProgress(mc.player, mc.player.getMainHandItem(), st, p);
            double distSq = Vec3.atCenterOf(p).distanceToSqr(eyePos);
            return distSq - (dmg * 10.0);
        }));

        return candidates;
    }

    private List<BlockPos> evaluateCityBlocks(BlockPos targetFeet) {
        List<BlockPos> valid = new ArrayList<>();
        if (mc.level == null) return valid;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos surroundPos = targetFeet.relative(dir);
            BlockState state = mc.level.getBlockState(surroundPos);
            if (!isBreakable(state, surroundPos)) continue;

            // Check if crystal can be placed adjacent to this surround block
            for (Direction crystalDir : Direction.Plane.HORIZONTAL) {
                BlockPos crystalPos = surroundPos.relative(crystalDir);
                BlockPos crystalBase = crystalPos.below();
                BlockState baseState = mc.level.getBlockState(crystalBase);

                boolean validBase = baseState.is(Blocks.OBSIDIAN) || baseState.is(Blocks.BEDROCK);
                boolean airAbove = mc.level.getBlockState(crystalPos).isAir();

                if (validBase && airAbove) {
                    valid.add(surroundPos);
                    break;
                }
            }

            if (!valid.contains(surroundPos)) {
                valid.add(surroundPos);
            }
        }
        return valid;
    }

    private boolean isBurrowBlock(BlockState state, BlockPos pos) {
        if (state == null || state.isAir()) return false;
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST) || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL);
    }

    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        return state.getDestroySpeed(mc.level, pos) >= 0;
    }

    public int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        return MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        if (targetBlock != null) {
            renderBlock(renderer, targetBlock, progress, fillColor.getArgb(), lineColor.getArgb());
        }

        if (doubleMine.get() && secondaryTargetBlock != null) {
            renderBlock(renderer, secondaryTargetBlock, secondaryProgress, secondaryFillColor.getArgb(), secondaryLineColor.getArgb());
        }

        if (rebreak.get()) {
            if (rebreakPos != null && !rebreakPos.equals(targetBlock)) {
                renderBlock(renderer, rebreakPos, 1.0f, fillColor.getArgb(), lineColor.getArgb());
            }
            if (secondaryRebreakPos != null && !secondaryRebreakPos.equals(secondaryTargetBlock)) {
                renderBlock(renderer, secondaryRebreakPos, 1.0f, secondaryFillColor.getArgb(), secondaryLineColor.getArgb());
            }
        }
    }

    private void renderBlock(Renderer3D renderer, BlockPos pos, float currentProgress, int fill, int line) {
        if (pos == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        AABB fullBox = new AABB(pos);
        float p = Math.min(1.0f, Math.max(0.0f, currentProgress));

        // Smooth box animation scaling from center outward as mining progresses
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
