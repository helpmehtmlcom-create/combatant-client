/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.scaffold.ScaffoldBlockItemSelection;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapMode;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Automates highway pavement construction on 2b2t and anarchy servers.
 * Locks movement to the cardinal walking axis (X or Z), prioritizes pavement materials,
 * fills potholes, and smoothly builds out the highway surface.
 */
@ModuleInfo(
        id = "autohighway",
        displayName = "AutoHighway",
        category = ModuleCategory.MOVEMENT,
        aliases = {"HighwayBuilder", "Highway"},
        description = "Automates highway pavement construction on 2b2t and anarchy servers."
)
public class AutoHighway extends Module {

    private static final int OFFHAND_SLOT = -2;

    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private final NumberValue<Integer> width =
            num("width", "width", 3, 1, 3);
    private final NumberValue<Integer> placeDelay =
            num("place_delay", "place_delay", 1, 0, 5);
    private final BooleanValue fillHoles =
            bool("fill_holes", "fill_holes", true);
    private final BooleanValue silentSwitch =
            bool("silent_switch", "silent_switch", true);
    private final BooleanValue rotate =
            bool("rotate", "rotate", true);
    private final BooleanValue render =
            bool("render", "render", true);

    // State
    private Direction lockedDirection = null;
    private int delayCounter = 0;
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (mc.player != null) {
            lockedDirection = getNearestCardinal(mc.player.getYRot());
            mc.player.setYRot(getCardinalYaw(lockedDirection));
        }
        delayCounter = 0;
        renderBlocks.clear();
    }

    @Override
    public void onDisable() {
        lockedDirection = null;
        delayCounter = 0;
        renderBlocks.clear();
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;
        if (ClientScreen.current() != null) return;

        // Maintain or update cardinal walking axis lock
        updateDirectionLock(player);

        // Check placement delay
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Execute highway placement cycle
        executePlacementCycle(player, level);
    }

    private void updateDirectionLock(LocalPlayer player) {
        float currentYaw = player.getYRot();
        if (lockedDirection == null) {
            lockedDirection = getNearestCardinal(currentYaw);
        } else {
            float targetYaw = getCardinalYaw(lockedDirection);
            float diff = Math.abs(Mth.wrapDegrees(currentYaw - targetYaw));
            // Update locked direction if player deliberately turns away by more than 60 degrees
            if (diff > 60.0f) {
                lockedDirection = getNearestCardinal(currentYaw);
            }
        }
        player.setYRot(getCardinalYaw(lockedDirection));
    }

    private Direction getNearestCardinal(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        if (normalized >= -45.0f && normalized < 45.0f) {
            return Direction.SOUTH; // 0 deg (+Z)
        } else if (normalized >= 45.0f && normalized < 135.0f) {
            return Direction.WEST;  // 90 deg (-X)
        } else if (normalized >= -135.0f && normalized < -45.0f) {
            return Direction.EAST;  // -90 deg (+X)
        } else {
            return Direction.NORTH; // 180 deg (-Z)
        }
    }

    private float getCardinalYaw(Direction dir) {
        if (dir == null) return 0.0f;
        return switch (dir) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    private void executePlacementCycle(LocalPlayer player, Level level) {
        Direction forward = lockedDirection != null ? lockedDirection : player.getDirection();
        Direction left = forward.getCounterClockWise();
        Direction right = forward.getClockWise();

        int highwayY = Mth.floor(player.getY() + 0.1) - 1;
        BlockPos playerPos = player.blockPosition();
        BlockPos basePos = new BlockPos(playerPos.getX(), highwayY, playerPos.getZ());

        int w = width.get();
        List<Integer> lateralOffsets;
        if (w == 1) {
            lateralOffsets = List.of(0);
        } else if (w == 2) {
            boolean preferLeft = isCloserToLeft(player, forward);
            lateralOffsets = preferLeft ? List.of(0, -1) : List.of(0, 1);
        } else {
            lateralOffsets = List.of(0, -1, 1);
        }

        // Collect positions: first potholes underneath (if enabled), then pavement surface
        // Iterate forward from beneath player feet (0) up to 3 blocks ahead
        LinkedHashSet<BlockPos> holeTargets = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> pavementTargets = new LinkedHashSet<>();

        int[] forwardSteps = {0, 1, 2, 3};
        for (int step : forwardSteps) {
            for (int lat : lateralOffsets) {
                BlockPos pavementPos = basePos.relative(forward, step);
                if (lat > 0) {
                    pavementPos = pavementPos.relative(right, lat);
                } else if (lat < 0) {
                    pavementPos = pavementPos.relative(left, -lat);
                }

                // Check potholes underneath highway pavement
                if (fillHoles.get()) {
                    BlockPos holePos = pavementPos.below();
                    if (isReplaceable(level, holePos) && !isEntityBlocked(player, level, holePos)) {
                        holeTargets.add(holePos);
                    }
                }

                // Check pavement block itself
                if (isReplaceable(level, pavementPos) && !isEntityBlocked(player, level, pavementPos)) {
                    pavementTargets.add(pavementPos);
                }
            }
        }

        if (holeTargets.isEmpty() && pavementTargets.isEmpty()) {
            return;
        }

        // Order of placement: potholes under highway first (foundation), then pavement surface
        List<BlockPos> queue = new ArrayList<>();
        queue.addAll(holeTargets);
        queue.addAll(pavementTargets);

        int slot = findPavingSlot(player);
        if (slot == -1) {
            return;
        }

        int maxPerTick = Math.max(1, w);
        int placed = 0;

        for (BlockPos targetPos : queue) {
            if (placed >= maxPerTick) break;

            // Ensure not already placed by prior placement in this cycle or world update
            if (!isReplaceable(level, targetPos)) continue;

            BlockHitResult hitResult = resolveHitResult(level, player, targetPos);
            if (hitResult == null) continue;

            // Re-verify material slot in case current stack depleted
            slot = findPavingSlot(player);
            if (slot == -1) break;

            if (placeBlock(player, hitResult, targetPos, slot)) {
                placed++;
            }
        }

        if (placed > 0) {
            delayCounter = placeDelay.get();
        }
    }

    private boolean isCloserToLeft(LocalPlayer player, Direction forward) {
        if (forward.getAxis() == Direction.Axis.Z) {
            double fracX = player.getX() - Mth.floor(player.getX());
            return forward == Direction.SOUTH ? fracX > 0.5 : fracX < 0.5;
        } else {
            double fracZ = player.getZ() - Mth.floor(player.getZ());
            return forward == Direction.EAST ? fracZ < 0.5 : fracZ > 0.5;
        }
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private boolean isEntityBlocked(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        // If pos is below the player's feet level, player does not self-block placement
        if (player.getBoundingBox().intersects(box)) {
            if (pos.getY() >= Mth.floor(player.getY() + 0.1)) {
                return true;
            }
        }

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity != null && entity.isAlive() && !entity.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private BlockHitResult resolveHitResult(Level level, LocalPlayer player, BlockPos pos) {
        Vec3 eyes = player.getEyePosition();
        double bestDist = Double.MAX_VALUE;
        BlockHitResult best = null;

        for (Direction dir : Direction.values()) {
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
            if (distSq > 20.25) continue; // max 4.5 blocks reach

            if (distSq < bestDist) {
                bestDist = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private boolean placeBlock(LocalPlayer player, BlockHitResult hitResult, BlockPos targetPos, int slot) {
        if (mc.gameMode == null || hitResult == null) return false;

        // Offhand
        if (slot == OFFHAND_SLOT) {
            if (rotate.get()) {
                sendRotation(player, hitResult);
            }
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
            if (result != null && result.consumesAction()) {
                player.swing(InteractionHand.OFF_HAND);
                renderBlocks.put(targetPos, System.currentTimeMillis());
                return true;
            }
            return false;
        }

        // Already held in main hand
        int currentSelected = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        if (slot == currentSelected) {
            if (rotate.get()) {
                sendRotation(player, hitResult);
            }
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
            if (result != null && result.consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND);
                renderBlocks.put(targetPos, System.currentTimeMillis());
                return true;
            }
            return false;
        }

        // Swapped via InventorySwap
        InventorySwapMode mode = silentSwitch.get() ? InventorySwapMode.SILENT_FULL : InventorySwapMode.NORMAL_FULL;
        boolean[] success = {false};

        InventorySwap.INSTANCE.withSwap(slot, mode, () -> {
            if (rotate.get()) {
                sendRotation(player, hitResult);
            }
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
            if (result != null && result.consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND);
                renderBlocks.put(targetPos, System.currentTimeMillis());
                success[0] = true;
            }
        });

        return success[0];
    }

    private void sendRotation(LocalPlayer player, BlockHitResult hitResult) {
        if (mc.getConnection() == null) return;
        Rotation rot = Rotation.lookingAt(hitResult.getLocation(), player.getEyePosition());
        RotationManager.INSTANCE.snapServerRotation(rot, 40, this, 2);
    }

    private int findPavingSlot(LocalPlayer player) {
        // Priority 1: Obsidian
        int slot = findSlotForPredicate(player, stack -> stack.is(Items.OBSIDIAN) || stack.is(Blocks.OBSIDIAN.asItem()));
        if (slot != -1) return slot;

        // Priority 2: Netherrack
        slot = findSlotForPredicate(player, stack -> stack.is(Items.NETHERRACK) || stack.is(Blocks.NETHERRACK.asItem()));
        if (slot != -1) return slot;

        // Priority 3: Cobblestone
        slot = findSlotForPredicate(player, stack -> stack.is(Items.COBBLESTONE) || stack.is(Blocks.COBBLESTONE.asItem()));
        if (slot != -1) return slot;

        // Priority 4: Stone
        slot = findSlotForPredicate(player, stack -> stack.is(Items.STONE) || stack.is(Blocks.STONE.asItem()));
        if (slot != -1) return slot;

        // Fallback: any valid solid building block
        return findSlotForPredicate(player, ScaffoldBlockItemSelection::isValidBlock);
    }

    private int findSlotForPredicate(LocalPlayer player, Predicate<ItemStack> predicate) {
        // Check offhand
        if (predicate.test(player.getOffhandItem())) {
            return OFFHAND_SLOT;
        }

        // Check hotbar (0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (predicate.test(stack)) {
                return i;
            }
        }

        // Check main inventory (9..35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (predicate.test(stack)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || renderBlocks.isEmpty() || mc.player == null || mc.level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = 1.0f - (float) (now - entry.getValue()) / 1000.0f;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);

            AABB box = new AABB(pos);
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x3500BFFF, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xD000BFFF, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }
}
