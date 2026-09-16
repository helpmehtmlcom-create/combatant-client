/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically scans and fills 1x1 blast-resistant holes near enemies using Obsidian or Cobwebs
 * to prevent enemies from burrowing or anchoring in safety.
 */
@ModuleInfo(
        id = "holefill",
        displayName = "HoleFill",
        category = ModuleCategory.COMBAT,
        aliases = {"holefiller", "autofill"},
        description = "Fills nearby safe holes to prevent enemies from anchoring or taking cover."
)
public class HoleFill extends Module {

    private static final Direction[] HOLE_SURROUND_DIRS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };

    private final Minecraft mc = Minecraft.getInstance();

    // Core settings
    private final NumberValue<Double> range =
            num("range", "range", 4.5, 1.0, 6.0);
    private final NumberValue<Double> enemyDistance =
            num("enemy_distance", "enemy_distance", 3.0, 1.0, 8.0);
    private final NumberValue<Integer> blocksPerTick =
            num("blocks_per_tick", "blocks_per_tick", 2, 1, 4);
    private final EnumValue<HoleBlockType> blockType =
            enumSetting("block_type", "block_type", HoleBlockType.OBSIDIAN, HoleBlockType.values());
    private final BooleanValue selfSafety =
            bool("self_safety", "self_safety", true);

    // Optional utility settings
    private final BooleanValue rotate =
            bool("rotate", "rotate", true);
    private final BooleanValue render =
            bool("render", "render", true);

    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        renderBlocks.clear();
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        RotationManager.INSTANCE.clear(this);
        renderBlocks.clear();
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;

        executeHoleFill(player, level);
    }

    private void executeHoleFill(LocalPlayer player, Level level) {
        double r = range.get();
        double rSq = r * r;
        double maxEnemyDist = enemyDistance.get();
        double maxEnemyDistSq = maxEnemyDist * maxEnemyDist;

        Vec3 eyes = player.getEyePosition();

        int minX = Mth.floor(player.getX() - r);
        int maxX = Mth.floor(player.getX() + r);
        int minY = Math.max(level.getMinY() + 1, Mth.floor(player.getY() - r));
        int maxY = Math.min(level.getMaxY() - 1, Mth.floor(player.getY() + r));
        int minZ = Mth.floor(player.getZ() - r);
        int maxZ = Mth.floor(player.getZ() + r);

        List<HoleTarget> candidates = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    // Check player placement reach
                    if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > rSq) continue;

                    // Filter out player's own position & self-safety
                    if (isPlayerInHole(player, pos)) continue;

                    // Validate 1x1 hole surrounded by blast-resistant blocks
                    if (!is1x1Hole(level, pos)) continue;

                    // Proximity to enemy player or nearest combat target
                    double enemyDistSq = getMinDistanceToEnemySq(level, player, pos);
                    if (enemyDistSq > maxEnemyDistSq) continue;

                    // Entity collision check
                    boolean hasEntity = isLivingEntityColliding(level, pos);
                    if (hasEntity && blockType.get() == HoleBlockType.OBSIDIAN) {
                        continue;
                    }

                    // Resolve placement hit result via unified BlockPlacer
                    BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, r);
                    if (hit == null) continue;

                    candidates.add(new HoleTarget(pos, hit, enemyDistSq, hasEntity));
                }
            }
        }

        if (candidates.isEmpty()) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        // Prioritize holes closest to enemies
        candidates.sort(Comparator.comparingDouble(HoleTarget::enemyDistSq));

        int maxBlocks = blocksPerTick.get();
        int placed = 0;

        for (HoleTarget target : candidates) {
            if (placed >= maxBlocks) break;
            if (placeHole(player, target)) {
                placed++;
            }
        }
    }

    private boolean placeHole(LocalPlayer player, HoleTarget target) {
        HoleBlockType type = blockType.get();
        boolean entityInHole = target.hasEntity();

        // 1. Check offhand first
        if (isHoleFillItem(player.getOffhandItem(), type, entityInHole)) {
            boolean success = BlockPlacer.placeBlock(
                    this,
                    target.hit(),
                    InteractionHand.OFF_HAND,
                    -1,
                    rotate.get(),
                    BlockPlacer.SwingMode.CLIENT_AND_SERVER
            );
            if (success) {
                renderBlocks.put(target.pos(), System.currentTimeMillis());
            }
            return success;
        }

        // 2. Find in hotbar or inventory
        int slot = findBlockSlot(player, type, entityInHole);
        if (slot < 0) return false;

        boolean success = BlockPlacer.placeBlock(
                this,
                target.hit(),
                InteractionHand.MAIN_HAND,
                slot,
                rotate.get(),
                BlockPlacer.SwingMode.CLIENT_AND_SERVER
        );
        if (success) {
            renderBlocks.put(target.pos(), System.currentTimeMillis());
        }
        return success;
    }

    private boolean is1x1Hole(Level level, BlockPos pos) {
        // Target block itself must be air or replaceable
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;

        // Block above hole must also be clear
        BlockState aboveState = level.getBlockState(pos.above());
        if (!aboveState.isAir() && !aboveState.canBeReplaced()) return false;

        // Check surround 5 sides: bottom, North, South, East, West
        for (Direction dir : HOLE_SURROUND_DIRS) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            Block block = neighborState.getBlock();

            // Hole must be surrounded by blast resistant blocks (Bedrock, Obsidian, Crying Obsidian, Ender Chest)
            boolean isSafeBlock = block == Blocks.BEDROCK
                    || block == Blocks.OBSIDIAN
                    || block == Blocks.CRYING_OBSIDIAN
                    || block == Blocks.ENDER_CHEST
                    || block == Blocks.RESPAWN_ANCHOR;

            if (!isSafeBlock) {
                return false;
            }
        }

        return true;
    }

    private boolean isPlayerInHole(LocalPlayer player, BlockPos holePos) {
        if (!selfSafety.get()) return false;

        AABB playerBox = player.getBoundingBox();
        AABB holeBox = new AABB(holePos);

        // Disallow if player intersects the hole or is immediately above it
        return playerBox.intersects(holeBox) || playerBox.intersects(holeBox.expandTowards(0, 1.5, 0));
    }

    private boolean isLivingEntityColliding(Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : entities) {
            if (e.isAlive() && !e.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private double getMinDistanceToEnemySq(Level level, LocalPlayer player, BlockPos pos) {
        Vec3 posCenter = Vec3.atCenterOf(pos);
        double minDistanceSq = Double.MAX_VALUE;

        // First check primary target from TargetManager
        LivingEntity currentTarget = TargetManager.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && currentTarget != player) {
            return posCenter.distanceToSqr(currentTarget.position());
        }

        // Otherwise find closest non-friendly player
        for (Player other : level.players()) {
            if (!combatant.client.util.target.TargetingUtil.isValidPlayerTarget(player, other, Double.MAX_VALUE)) continue;
            double distSq = posCenter.distanceToSqr(other.position());
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
            }
        }

        return minDistanceSq;
    }

    private int findBlockSlot(LocalPlayer player, HoleBlockType type, boolean entityInHole) {
        // First check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isHoleFillItem(stack, type, entityInHole)) {
                return i;
            }
        }

        // Then check main inventory and swap into active slot
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isHoleFillItem(stack, type, entityInHole)) {
                int targetHotbarSlot = InventorySwap.INSTANCE.clientSelectedSlot();
                if (targetHotbarSlot < 0 || targetHotbarSlot >= 9) targetHotbarSlot = 0;
                InventorySwap.INSTANCE.swapInventoryToHotbar(i, targetHotbarSlot);
                return targetHotbarSlot;
            }
        }

        return -1;
    }

    private boolean isHoleFillItem(ItemStack stack, HoleBlockType type, boolean entityInHole) {
        if (stack == null || stack.isEmpty()) return false;

        boolean isObsidian = stack.is(Items.OBSIDIAN)
                || stack.is(Items.CRYING_OBSIDIAN)
                || stack.is(Items.ENDER_CHEST)
                || stack.is(Blocks.OBSIDIAN.asItem())
                || stack.is(Blocks.CRYING_OBSIDIAN.asItem())
                || stack.is(Blocks.ENDER_CHEST.asItem());

        boolean isWeb = stack.is(Items.COBWEB) || stack.is(Blocks.COBWEB.asItem());

        if (entityInHole) {
            return isWeb; // Only web can be placed in entity space
        }

        return switch (type) {
            case OBSIDIAN -> isObsidian;
            case WEB -> isWeb;
            case BOTH -> isObsidian || isWeb;
        };
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = 1.0f - (float) (now - entry.getValue()) / 1000.0f;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);

            AABB box = new AABB(pos);
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x40FFAA00, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xFFFFAA00, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    public enum HoleBlockType {
        OBSIDIAN,
        WEB,
        BOTH
    }

    private record HoleTarget(BlockPos pos, BlockHitResult hit, double enemyDistSq, boolean hasEntity) {
    }
}
