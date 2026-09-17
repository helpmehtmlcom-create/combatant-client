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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        id = "automine",
        displayName = "AutoMine",
        aliases = {"autocity", "city", "cityboss", "citybreaker", "cevbreaker", "autocev", "cev"},
        category = ModuleCategory.COMBAT,
        description = "Automatically targets and breaks enemy burrow, city, surround, and defense blocks via SpeedMine, with integrated CevBreaker offensive attacks."
)
public final class AutoMine extends Module {

    public enum Mode implements EnumValue.IdProvider {
        SMART("smart"),
        CITY("city"),
        SURROUND("surround"),
        BURROW("burrow"),
        ANTI_CEV("anti_cev"),
        SELF_WEB("self_web"),
        CEV_BREAKER("cev_breaker");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = tooltip(
            enumSetting("automineMode", "mode", Mode.SMART, Mode.values()),
            "Targeting mode: Smart for optimal priority, City for surround blocks with crystal space, Surround for all feet blocks, Burrow for inside feet, Anti-Cev for above head, Self-Web for defense."
    );

    private final NumberValue<Float> range = tooltip(
            num("automineRange", "range", 5.0f, 2.0f, 7.0f),
            "Maximum search distance to detect enemy targets."
    );

    private final BooleanValue doubleMine = tooltip(
            bool("automineDoubleMine", "double_mine", true),
            "Enables simultaneous mining of two optimal target blocks via SpeedMine."
    );

    private final BooleanValue autoEnableSpeedMine = tooltip(
            bool("automineAutoEnableSpeedmine", "auto_enable_speedmine", true),
            "Automatically enables the SpeedMine module when AutoMine is active."
    );

    private final BooleanValue render = tooltip(
            bool("automineRender", "render", true),
            "Renders 3D visual indicators on current AutoMine target blocks."
    );

    private final RGBAColorValue fillColor = visibleWhen(
            color("automineFillColor", "fill_color", "#D6303155"),
            render::get
    );

    private final RGBAColorValue lineColor = visibleWhen(
            color("automineLineColor", "line_color", "#FF7675FF"),
            render::get
    );

    private final RGBAColorValue secondaryFillColor = visibleWhen(
            color("automineSecondaryFillColor", "secondary_fill_color", "#0984E355"),
            () -> render.get() && doubleMine.get()
    );

    private final RGBAColorValue secondaryLineColor = visibleWhen(
            color("automineSecondaryLineColor", "secondary_line_color", "#74B9FFFF"),
            () -> render.get() && doubleMine.get()
    );

    private BlockPos targetBlock = null;
    private float progress = 0.0f;
    private BlockPos secondaryTargetBlock = null;
    private float secondaryProgress = 0.0f;

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
        SpeedMine speedMine = Modules.get(SpeedMine.class);
        if (speedMine != null) {
            if (targetBlock != null) {
                speedMine.abortMining(true);
            }
            if (secondaryTargetBlock != null) {
                speedMine.abortMining(false);
            }
        }
        targetBlock = null;
        secondaryTargetBlock = null;
        progress = 0.0f;
        secondaryProgress = 0.0f;
        currentTarget = null;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            reset();
            return;
        }

        // Verify and depend on SpeedMine
        SpeedMine speedMine = Modules.get(SpeedMine.class);
        if (speedMine == null) {
            reset();
            return;
        }

        if (!speedMine.isEnabled()) {
            if (autoEnableSpeedMine.get()) {
                speedMine.setEnabled(true);
            } else {
                reset();
                return;
            }
        }

        LocalPlayer player = mc.player;

        // 1. Select target player
        currentTarget = findTargetPlayer(player);

        // 2. Select candidate target blocks
        List<BlockPos> bestBlocks = (currentTarget != null) ? findBestBlocks(currentTarget, speedMine.getRange()) : List.of();

        // 3. Process primary target via SpeedMine
        BlockPos bestPrimary = !bestBlocks.isEmpty() ? bestBlocks.get(0) : null;
        processPrimary(bestPrimary, speedMine);

        // 4. Process secondary target (double mine) via SpeedMine
        if (doubleMine.get() && bestBlocks.size() > 1) {
            BlockPos bestSecondary = bestBlocks.get(1);
            processSecondary(bestSecondary, speedMine);
        } else if (secondaryTargetBlock != null) {
            speedMine.abortMining(false);
            secondaryTargetBlock = null;
            secondaryProgress = 0.0f;
        }

        // 5. Update progress states from SpeedMine
        progress = speedMine.getMiningProgress();
        secondaryProgress = speedMine.getSecondaryMiningProgress();
    }

    private void processPrimary(BlockPos best, SpeedMine speedMine) {
        if (best == null) {
            if (targetBlock != null) {
                speedMine.abortMining(true);
                targetBlock = null;
                progress = 0.0f;
            }
            return;
        }

        if (!best.equals(targetBlock)) {
            targetBlock = best;
            progress = 0.0f;
            speedMine.startMining(best, null);
        }
    }

    private void processSecondary(BlockPos best, SpeedMine speedMine) {
        if (best == null) {
            if (secondaryTargetBlock != null) {
                speedMine.abortMining(false);
                secondaryTargetBlock = null;
                secondaryProgress = 0.0f;
            }
            return;
        }

        if (!best.equals(secondaryTargetBlock)) {
            secondaryTargetBlock = best;
            secondaryProgress = 0.0f;
            speedMine.startSecondaryMining(best, null);
        }
    }

    private Player findTargetPlayer(LocalPlayer player) {
        if (mc.level == null || player == null) return null;
        Player best = null;
        double bestDistSq = range.get() * range.get();

        for (Player p : mc.level.players()) {
            if (p == player || !p.isAlive() || p.isSpectator()) continue;
            if (CategoryRules.determine(p.getGameProfile().name()) == CategoryType.FRIEND) continue;

            double distSq = p.distanceToSqr(player);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = p;
            }
        }
        return best;
    }

    private List<BlockPos> findBestBlocks(Player target, float maxMiningRange) {
        if (mc.level == null || mc.player == null) return List.of();

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos targetFeet = target != null ? target.blockPosition() : null;
        BlockPos selfFeet = mc.player.blockPosition();

        Mode currentMode = mode.get();

        // 1. Self Web check (automatic self-defense)
        if (currentMode == Mode.SMART || currentMode == Mode.SELF_WEB) {
            BlockState selfState = mc.level.getBlockState(selfFeet);
            if (selfState.is(Blocks.COBWEB)) {
                candidates.add(selfFeet);
            }
            BlockState selfHeadState = mc.level.getBlockState(selfFeet.above());
            if (selfHeadState.is(Blocks.COBWEB) && !candidates.contains(selfFeet.above())) {
                candidates.add(selfFeet.above());
            }
            if (!candidates.isEmpty() && currentMode == Mode.SELF_WEB) {
                return filterByMiningRange(candidates, maxMiningRange);
            }
        }

        if (target == null || targetFeet == null) {
            return filterByMiningRange(candidates, maxMiningRange);
        }

        // 2. Target Burrow
        if (currentMode == Mode.SMART || currentMode == Mode.BURROW) {
            BlockState inFeet = mc.level.getBlockState(targetFeet);
            if (isBurrowBlock(inFeet, targetFeet)) {
                candidates.add(targetFeet);
            }
            if (currentMode == Mode.BURROW) {
                return filterByMiningRange(candidates, maxMiningRange);
            }
        }

        // 3. City Surround Blocks (blocks with adjacent air where crystal can be placed)
        if (currentMode == Mode.SMART || currentMode == Mode.CITY) {
            List<BlockPos> cityBlocks = evaluateCityBlocks(targetFeet);
            for (BlockPos cp : cityBlocks) {
                if (!candidates.contains(cp)) {
                    candidates.add(cp);
                }
            }
            if (currentMode == Mode.CITY) {
                return filterByMiningRange(candidates, maxMiningRange);
            }
        }

        // 4. General Surround Blocks
        if (currentMode == Mode.SMART || currentMode == Mode.SURROUND) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos sPos = targetFeet.relative(dir);
                if (isBreakable(mc.level.getBlockState(sPos), sPos) && !candidates.contains(sPos)) {
                    candidates.add(sPos);
                }
            }
            if (currentMode == Mode.SURROUND) {
                return filterByMiningRange(candidates, maxMiningRange);
            }
        }

        // 5. Anti-Cev Blocks (above enemy head)
        if (currentMode == Mode.SMART || currentMode == Mode.ANTI_CEV) {
            BlockPos headPos = targetFeet.above(2);
            if (isBreakable(mc.level.getBlockState(headPos), headPos) && !candidates.contains(headPos)) {
                candidates.add(headPos);
            }
            if (currentMode == Mode.ANTI_CEV) {
                return filterByMiningRange(candidates, maxMiningRange);
            }
        }

        return filterByMiningRange(candidates, maxMiningRange);
    }

    private List<BlockPos> filterByMiningRange(List<BlockPos> candidates, float maxMiningRange) {
        if (candidates.isEmpty() || mc.player == null) return List.of();
        Vec3 eyePos = mc.player.getEyePosition();
        double maxDistSq = maxMiningRange * maxMiningRange;

        List<BlockPos> filtered = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (eyePos.distanceToSqr(Vec3.atCenterOf(pos)) <= maxDistSq) {
                filtered.add(pos);
            }
        }
        return filtered;
    }

    private List<BlockPos> evaluateCityBlocks(BlockPos targetFeet) {
        if (mc.level == null || mc.player == null || targetFeet == null) return List.of();
        Level level = mc.level;

        List<BlockPos> candidates = new ArrayList<>();
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : directions) {
            BlockPos surroundPos = targetFeet.relative(dir);
            BlockState state = level.getBlockState(surroundPos);
            if (!isBreakableCityBlock(state, surroundPos)) continue;

            if (hasCrystalDamageSpace(level, surroundPos, dir)) {
                candidates.add(surroundPos);
            }
        }

        if (candidates.isEmpty()) return candidates;

        // If current target block is still a valid candidate, keep it first to preserve progress
        if (targetBlock != null && candidates.contains(targetBlock)) {
            candidates.remove(targetBlock);
            candidates.add(0, targetBlock);
            return candidates;
        }

        // Sort candidates:
        // 1. Faster-to-break blocks (e.g. Ender Chest hardness 22.5 vs Obsidian 50.0)
        // 2. Closer distance to player eye
        Vec3 eyePos = mc.player.getEyePosition();
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

        return candidates;
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

    private boolean isBreakableCityBlock(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        if (state.getDestroySpeed(mc.level, pos) < 0) return false;

        Block b = state.getBlock();
        return b == Blocks.OBSIDIAN
                || b == Blocks.ENDER_CHEST
                || b == Blocks.NETHERITE_BLOCK
                || b == Blocks.CRYING_OBSIDIAN
                || b == Blocks.RESPAWN_ANCHOR
                || b == Blocks.ANVIL
                || b == Blocks.CHIPPED_ANVIL
                || b == Blocks.DAMAGED_ANVIL;
    }

    private boolean isBurrowBlock(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        return isBreakable(state, pos);
    }

    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || mc.level == null) return false;
        if (state.getBlock() == Blocks.BEDROCK) return false;
        return state.getDestroySpeed(mc.level, pos) >= 0;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        // Render primary target
        if (targetBlock != null) {
            renderBlock(renderer, targetBlock, progress, fillColor.getArgb(), lineColor.getArgb());
        }

        // Render secondary target (double mine)
        if (doubleMine.get() && secondaryTargetBlock != null) {
            renderBlock(renderer, secondaryTargetBlock, secondaryProgress, secondaryFillColor.getArgb(), secondaryLineColor.getArgb());
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
