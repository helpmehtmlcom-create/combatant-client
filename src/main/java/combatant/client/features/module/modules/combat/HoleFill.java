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
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

    private static final int ROTATION_PRIORITY = 30;
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

                    // Resolve placement hit result
                    BlockHitResult hit = resolveHitResult(level, eyes, pos, r);
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
            return performPlace(player, InteractionHand.OFF_HAND, target.hit(), target.pos(), -1);
        }

        // 2. Find in hotbar or inventory
        int slot = findBlockSlot(player, type, entityInHole);
        if (slot < 0) return false;

        return performPlace(player, InteractionHand.MAIN_HAND, target.hit(), target.pos(), slot);
    }

    private boolean performPlace(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                 BlockPos pos, int hotbarSlot) {
        if (mc.gameMode == null || hit == null) return false;

        // Rotation
        if (rotate.get()) {
            Rotation rot = Rotation.lookingAt(hit.getLocation(), player.getEyePosition()).normalize();
            RotationTarget rotTarget = new RotationTarget(
                    rot,
                    null,
                    List.of(),
                    1,
                    4.0f,
                    true,
                    MovementCorrection.SILENT,
                    null
            );
            RotationManager.INSTANCE.setRotationTarget(rotTarget, ROTATION_PRIORITY, this);

            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        rot.yaw(),
                        rot.pitch(),
                        player.onGround(),
                        player.horizontalCollision
                ));
            }
        }

        if (hand == InteractionHand.OFF_HAND) {
            InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
            if (result != InteractionResult.FAIL) {
                player.swing(hand);
                renderBlocks.put(pos, System.currentTimeMillis());
                return true;
            }
            return false;
        }

        // Silent hotbar leasing
        boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 1);
        if (!leased) return false;

        try {
            InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
            if (result != InteractionResult.FAIL) {
                player.swing(hand);
                renderBlocks.put(pos, System.currentTimeMillis());
                return true;
            }
            return false;
        } finally {
            InventorySwap.INSTANCE.releaseHotbar(this);
        }
    }

    private BlockHitResult resolveHitResult(Level level, Vec3 eyes, BlockPos pos, double maxRange) {
        double bestDistSq = Double.MAX_VALUE;
        BlockHitResult best = null;

        for (Direction dir : HOLE_SURROUND_DIRS) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) continue;

            BlockState state = level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, neighbor).isEmpty()) {
                continue;
            }

            Direction clickFace = dir.getOpposite();
            Vec3 normal = Vec3.atLowerCornerOf(clickFace.getUnitVec3i());
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(normal.scale(0.5));
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > maxRange * maxRange) continue;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private boolean is1x1Hole(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) return false;

        // Position itself must be air or replaceable
        if (!isReplaceable(level, pos)) return false;

        // Space above must be air or replaceable to be an open hole
        if (!isReplaceable(level, pos.above())) return false;

        // Floor must be blast-resistant
        if (!isBlastResistant(level, pos.below())) return false;

        // All 4 horizontal sides must be blast-resistant
        return isBlastResistant(level, pos.north())
                && isBlastResistant(level, pos.south())
                && isBlastResistant(level, pos.east())
                && isBlastResistant(level, pos.west());
    }

    private static boolean isBlastResistant(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.ENDER_CHEST
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL;
    }

    private static boolean isReplaceable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private boolean isPlayerInHole(LocalPlayer player, BlockPos pos) {
        if (player == null || pos == null) return false;
        AABB holeBox = new AABB(pos);
        if (player.getBoundingBox().intersects(holeBox)) {
            return true;
        }
        if (player.blockPosition().equals(pos)) {
            return true;
        }
        if (selfSafety.get()) {
            AABB safetyBox = holeBox.expandTowards(0, 1.0, 0);
            if (player.getBoundingBox().intersects(safetyBox)) {
                return true;
            }
            if (player.blockPosition().equals(pos.above())) {
                return true;
            }
        }
        return false;
    }

    private boolean isLivingEntityColliding(Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity.isAlive() && !entity.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private double getMinDistanceToEnemySq(Level level, LocalPlayer player, BlockPos holePos) {
        Vec3 center = Vec3.atCenterOf(holePos);
        double minDistSq = Double.MAX_VALUE;

        // Check TargetManager target
        LivingEntity managed = TargetManager.getTarget();
        if (managed != null && managed != player && managed.isAlive() && !managed.isSpectator()) {
            minDistSq = Math.min(minDistSq, managed.position().distanceToSqr(center));
        }

        // Check all other non-friendly players
        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            CategoryType type = CategoryRules.determine(other.getGameProfile().name());
            if (type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF) continue;

            minDistSq = Math.min(minDistSq, other.position().distanceToSqr(center));
        }

        return minDistSq;
    }

    private int findBlockSlot(LocalPlayer player, HoleBlockType type, boolean entityInHole) {
        // First check hotbar (0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isHoleFillItem(stack, type, entityInHole)) {
                return i;
            }
        }

        // If not in hotbar, search main inventory (9..35) and swap to current hotbar slot
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
        if (stack.isEmpty()) return false;
        if (entityInHole) {
            // Entities inside the hole block solid placement; cobwebs can still be placed
            return stack.is(Items.COBWEB) && (type == HoleBlockType.COBWEB || type == HoleBlockType.ANY);
        }
        return switch (type) {
            case OBSIDIAN -> stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN);
            case COBWEB -> stack.is(Items.COBWEB);
            case ANY -> stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN) || stack.is(Items.COBWEB);
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
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x4000E5FF, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xFF00E5FF, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    private record HoleTarget(BlockPos pos, BlockHitResult hit, double enemyDistSq, boolean hasEntity) {}

    public enum HoleBlockType {
        OBSIDIAN,
        COBWEB,
        ANY
    }
}
