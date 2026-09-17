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

    // CevBreaker offensive settings
    private final BooleanValue cevBreaker = tooltip(
            bool("automineCevBreaker", "cev_breaker", false),
            "Executes CevBreaker attacks on enemies in holes or safe positions by placing obsidian and crystals above their heads, then mining via SpeedMine."
    );

    private final NumberValue<Double> cevRange = visibleWhen(
            num("automineCevRange", "cev_range", 4.5, 2.0, 6.0),
            () -> cevBreaker.get() || mode.get() == Mode.CEV_BREAKER
    );

    private final NumberValue<Integer> cevBreakDelay = visibleWhen(
            num("automineCevBreakDelay", "cev_break_delay", 0, 0, 5),
            () -> cevBreaker.get() || mode.get() == Mode.CEV_BREAKER
    );

    private final BooleanValue cevAntiSuicide = visibleWhen(
            bool("automineCevAntiSuicide", "cev_anti_suicide", true),
            () -> cevBreaker.get() || mode.get() == Mode.CEV_BREAKER
    );

    public enum CevStage {
        PLACE_OBSIDIAN,
        PLACE_CRYSTAL,
        MINE_OBSIDIAN,
        DETONATE
    }

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private CevStage cevStage = CevStage.PLACE_OBSIDIAN;
    private Player cevTarget = null;
    private BlockPos cevTargetCeiling = null;
    private int cevBreakDelayTimer = 0;

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
        resetCevState();
    }

    private void resetCevState() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        cevStage = CevStage.PLACE_OBSIDIAN;
        cevTarget = null;
        cevTargetCeiling = null;
        cevBreakDelayTimer = 0;
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

        if (cevBreakDelayTimer > 0) {
            cevBreakDelayTimer--;
        }

        // 1. Select target player
        currentTarget = findTargetPlayer(player);

        // 2. Check and execute CevBreaker offensive pipeline if enabled or in CEV_BREAKER mode
        if (mode.get() == Mode.CEV_BREAKER || (cevBreaker.get() && currentTarget != null && isTargetInHoleOrSafe(mc.level, currentTarget))) {
            if (updateCevBreaker(player, mc.level, speedMine)) {
                progress = speedMine.getMiningProgress();
                secondaryProgress = speedMine.getSecondaryMiningProgress();
                return;
            }
        }

        // 3. Select candidate target blocks
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

        // 6. CevBreaker Mode Blocks (target ceiling)
        if (currentMode == Mode.CEV_BREAKER) {
            BlockPos headPos = targetFeet.above(2);
            if (!candidates.contains(headPos)) {
                candidates.add(headPos);
            }
            return filterByMiningRange(candidates, maxMiningRange);
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

        // Render CevBreaker ceiling target
        if ((cevBreaker.get() || mode.get() == Mode.CEV_BREAKER) && cevTargetCeiling != null && !cevTargetCeiling.equals(targetBlock)) {
            BlockState ceilingState = mc.level.getBlockState(cevTargetCeiling);
            if (!ceilingState.isAir()) {
                renderBlock(renderer, cevTargetCeiling, progress, 0x60FF5500, 0xFFFF5500);
            }
        }
    }

    // ==========================================
    //   CevBreaker Offensive Execution Pipeline
    // ==========================================

    private boolean updateCevBreaker(LocalPlayer player, Level level, SpeedMine speedMine) {
        if (cevTarget == null || !isValidCevTarget(player, cevTarget, cevRange.get()) || !isTargetInHoleOrSafe(level, cevTarget)) {
            cevTarget = findCevTarget(player, level, cevRange.get());
            if (cevTarget == null) {
                resetCevState();
                return false;
            }
            cevStage = CevStage.PLACE_OBSIDIAN;
            cevTargetCeiling = cevTarget.blockPosition().above(2);
        } else {
            cevTargetCeiling = cevTarget.blockPosition().above(2);
        }

        if (cevTargetCeiling == null) return false;

        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(cevTargetCeiling)) > cevRange.get() + 1.5) {
            resetCevState();
            return false;
        }

        switch (cevStage) {
            case PLACE_OBSIDIAN -> handleCevPlaceObsidian(player, level, speedMine);
            case PLACE_CRYSTAL -> handleCevPlaceCrystal(player, level, speedMine);
            case MINE_OBSIDIAN -> handleCevMineObsidian(player, level, speedMine);
            case DETONATE -> handleCevDetonate(player, level);
        }
        return true;
    }

    private void handleCevPlaceObsidian(LocalPlayer player, Level level, SpeedMine speedMine) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (isObsidian(ceilingState)) {
            cevStage = CevStage.PLACE_CRYSTAL;
            handleCevPlaceCrystal(player, level, speedMine);
            return;
        }

        if (ceilingState.is(Blocks.BEDROCK)) {
            cevTarget = null;
            return;
        }

        if (isReplaceable(level, cevTargetCeiling)) {
            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, cevTargetCeiling, cevRange.get().floatValue() + 1.5f);
            if (hit != null) {
                int obbySlot = findObsidianSlot(player);
                if (obbySlot != -1) {
                    if (BlockPlacer.placeBlock(this, hit, InteractionHand.MAIN_HAND, obbySlot, true, BlockPlacer.SwingMode.SERVER)) {
                        cevStage = CevStage.PLACE_CRYSTAL;
                    }
                }
            }
        }
    }

    private void handleCevPlaceCrystal(LocalPlayer player, Level level, SpeedMine speedMine) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (!isObsidian(ceilingState) && !ceilingState.is(Blocks.BEDROCK)) {
            cevStage = CevStage.PLACE_OBSIDIAN;
            return;
        }

        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal existingCrystal = findExistingCrystal(level, crystalAir);
        if (existingCrystal != null) {
            cevStage = CevStage.MINE_OBSIDIAN;
            handleCevMineObsidian(player, level, speedMine);
            return;
        }

        if (!level.isInWorldBounds(crystalAir)) return;

        Vec3 clickVec = new Vec3(cevTargetCeiling.getX() + 0.5, cevTargetCeiling.getY() + 1.0, cevTargetCeiling.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(clickVec, Direction.UP, cevTargetCeiling, false);

        int crystalSlot = findCrystalSlot(player);
        if (crystalSlot != -1) {
            InteractionHand hand = player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (hand == InteractionHand.OFF_HAND || InventorySwap.INSTANCE.leaseHotbar(this, crystalSlot, 2)) {
                mc.gameMode.useItemOn(player, hand, hit);
                player.swing(hand);
                cevStage = CevStage.MINE_OBSIDIAN;
            }
        }
    }

    private void handleCevMineObsidian(LocalPlayer player, Level level, SpeedMine speedMine) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (ceilingState.isAir() || !isObsidian(ceilingState)) {
            cevStage = CevStage.DETONATE;
            handleCevDetonate(player, level);
            return;
        }

        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);
        if (crystal == null) {
            cevStage = CevStage.PLACE_CRYSTAL;
            return;
        }

        processPrimary(cevTargetCeiling, speedMine);
        targetBlock = cevTargetCeiling;
        progress = speedMine.getMiningProgress();

        if (progress >= 1.0f || ceilingState.isAir()) {
            cevStage = CevStage.DETONATE;
            handleCevDetonate(player, level);
        }
    }

    private void handleCevDetonate(LocalPlayer player, Level level) {
        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);

        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            cevStage = CevStage.PLACE_OBSIDIAN;
            cevBreakDelayTimer = 0;
            return;
        }

        if (cevBreakDelayTimer > 0) return;

        if (cevAntiSuicide.get()) {
            Vec3 explosionPos = crystal.position();
            float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, explosionPos, 0, false);
            if (!ExplosionDamageRules.isSafe(player, selfDamage, false)) {
                return;
            }
        }

        Rotation rot = Rotation.lookingAt(crystal.getBoundingBox().getCenter(), player.getEyePosition()).normalize();
        RotationManager.INSTANCE.snapServerRotation(rot, 32, this, 2);

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundInteractPacket(
                    crystal.getId(),
                    null,
                    null,
                    player.isShiftKeyDown()
            ));
        } else if (mc.gameMode != null) {
            mc.gameMode.attack(player, crystal);
        }
        player.swing(InteractionHand.MAIN_HAND);
        cevBreakDelayTimer = cevBreakDelay.get();
        cevStage = CevStage.PLACE_OBSIDIAN;
    }

    private EndCrystal findExistingCrystal(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.5);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive);
        return crystals.isEmpty() ? null : crystals.get(0);
    }

    private Player findCevTarget(LocalPlayer player, Level level, double maxRange) {
        double maxDistSq = maxRange * maxRange;
        Player best = null;
        double bestDistSq = maxDistSq;

        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (CategoryRules.determine(other.getGameProfile().name()) == CategoryType.FRIEND) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq <= bestDistSq && isTargetInHoleOrSafe(level, other)) {
                bestDistSq = distSq;
                best = other;
            }
        }
        return best;
    }

    private boolean isValidCevTarget(LocalPlayer player, Player target, double maxRange) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved()) return false;
        if (player.distanceTo(target) > maxRange) return false;
        return CategoryRules.determine(target.getGameProfile().name()) != CategoryType.FRIEND;
    }

    private boolean isTargetInHoleOrSafe(Level level, Player target) {
        BlockPos pos = target.blockPosition();
        int safeSides = 0;
        for (Direction dir : HORIZONTALS) {
            BlockPos side = pos.relative(dir);
            BlockState sideState = level.getBlockState(side);
            if (sideState.is(Blocks.BEDROCK) || sideState.is(Blocks.OBSIDIAN) || sideState.is(Blocks.CRYING_OBSIDIAN)) {
                safeSides++;
            }
        }
        return safeSides >= 3;
    }

    private boolean isObsidian(BlockState state) {
        return state != null && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN));
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private int findObsidianSlot(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.OBSIDIAN)) return 40;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private int findCrystalSlot(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.END_CRYSTAL)) return 40;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }

    public CevStage getCevStage() {
        return cevStage;
    }

    public Player getCevTarget() {
        return cevTarget;
    }

    public BlockPos getCevTargetCeiling() {
        return cevTargetCeiling;
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
