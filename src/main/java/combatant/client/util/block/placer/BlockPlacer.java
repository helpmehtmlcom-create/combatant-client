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

package combatant.client.util.block.placer;

import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PostPlayerUpdateEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.block.scaffold.ScaffoldTargetFinder;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Unified, anti-cheat resilient block placement system.
 * Handles automatic optimal face finding, GrimAC-safe non-centered hit vectors,
 * silent hotbar leasing, sequence-predicted placement packets, and swing execution.
 */
public final class BlockPlacer {

    private static final Direction[] HORIZONTALS = {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final Minecraft mc = Minecraft.getInstance();

    private final Module module;
    private final Object requester;
    private final SlotFinder slotFinder;
    private final DoubleSupplier rangeSupplier;
    private final DoubleSupplier wallRangeSupplier;
    private final IntSupplier cooldownMinSupplier;
    private final IntSupplier cooldownMaxSupplier;
    private final IntSupplier slotResetDelayMinSupplier;
    private final IntSupplier slotResetDelayMaxSupplier;
    private final IntSupplier sneakTicksSupplier;
    private final BooleanSupplier constructFailResultSupplier;
    private final BooleanSupplier ignoreOpenInventorySupplier;
    private final BooleanSupplier ignoreUsingItemSupplier;
    private final Supplier<RotationMode> rotationModeSupplier;
    private final Supplier<MovementCorrection> movementCorrectionSupplier;
    private final Supplier<SwingMode> swingModeSupplier;
    private final int rotationPriority;

    private final LinkedHashMap<BlockPos, Boolean> blocks = new LinkedHashMap<>();
    private final LinkedHashSet<BlockPos> inaccessible = new LinkedHashSet<>();

    private boolean registered;
    private int ticksToWait;
    private boolean ranAction;
    private int sneakTimes;
    private PlacementPlan currentPlacement;

    public BlockPlacer(
            Module module,
            Object requester,
            int rotationPriority,
            SlotFinder slotFinder,
            DoubleSupplier rangeSupplier,
            DoubleSupplier wallRangeSupplier,
            IntSupplier cooldownMinSupplier,
            IntSupplier cooldownMaxSupplier,
            IntSupplier slotResetDelayMinSupplier,
            IntSupplier slotResetDelayMaxSupplier,
            IntSupplier sneakTicksSupplier,
            BooleanSupplier constructFailResultSupplier,
            BooleanSupplier ignoreOpenInventorySupplier,
            BooleanSupplier ignoreUsingItemSupplier,
            Supplier<RotationMode> rotationModeSupplier,
            Supplier<MovementCorrection> movementCorrectionSupplier
    ) {
        this(
                module,
                requester,
                rotationPriority,
                slotFinder,
                rangeSupplier,
                wallRangeSupplier,
                cooldownMinSupplier,
                cooldownMaxSupplier,
                slotResetDelayMinSupplier,
                slotResetDelayMaxSupplier,
                sneakTicksSupplier,
                constructFailResultSupplier,
                ignoreOpenInventorySupplier,
                ignoreUsingItemSupplier,
                rotationModeSupplier,
                movementCorrectionSupplier,
                () -> SwingMode.CLIENT_AND_SERVER
        );
    }

    public BlockPlacer(
            Module module,
            Object requester,
            int rotationPriority,
            SlotFinder slotFinder,
            DoubleSupplier rangeSupplier,
            DoubleSupplier wallRangeSupplier,
            IntSupplier cooldownMinSupplier,
            IntSupplier cooldownMaxSupplier,
            IntSupplier slotResetDelayMinSupplier,
            IntSupplier slotResetDelayMaxSupplier,
            IntSupplier sneakTicksSupplier,
            BooleanSupplier constructFailResultSupplier,
            BooleanSupplier ignoreOpenInventorySupplier,
            BooleanSupplier ignoreUsingItemSupplier,
            Supplier<RotationMode> rotationModeSupplier,
            Supplier<MovementCorrection> movementCorrectionSupplier,
            Supplier<SwingMode> swingModeSupplier
    ) {
        this.module = module;
        this.requester = requester;
        this.rotationPriority = rotationPriority;
        this.slotFinder = slotFinder;
        this.rangeSupplier = rangeSupplier;
        this.wallRangeSupplier = wallRangeSupplier;
        this.cooldownMinSupplier = cooldownMinSupplier;
        this.cooldownMaxSupplier = cooldownMaxSupplier;
        this.slotResetDelayMinSupplier = slotResetDelayMinSupplier;
        this.slotResetDelayMaxSupplier = slotResetDelayMaxSupplier;
        this.sneakTicksSupplier = sneakTicksSupplier;
        this.constructFailResultSupplier = constructFailResultSupplier;
        this.ignoreOpenInventorySupplier = ignoreOpenInventorySupplier;
        this.ignoreUsingItemSupplier = ignoreUsingItemSupplier;
        this.rotationModeSupplier = rotationModeSupplier;
        this.movementCorrectionSupplier = movementCorrectionSupplier;
        this.swingModeSupplier = swingModeSupplier != null ? swingModeSupplier : () -> SwingMode.CLIENT_AND_SERVER;
    }

    // =========================================================================
    // Static placement utilities
    // =========================================================================

    /**
     * Finds the optimal placement face and hit result for any target BlockPos.
     * Calculates non-centered hit vectors conforming to strict raytraces.
     */
    public static BlockHitResult findOptimalPlacementHit(Level level, LocalPlayer player, BlockPos targetPos, double maxRange) {
        if (level == null || player == null || targetPos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double maxRangeSq = maxRange * maxRange;

        BlockHitResult bestHit = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction dir : HORIZONTALS) {
            BlockPos neighbor = targetPos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) continue;

            BlockState state = level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, neighbor).isEmpty()) {
                continue;
            }

            Direction clickFace = dir.getOpposite();

            // Strict raytrace vector on the neighbor face
            Vec3 hitVec = calculateStrictHitVec(neighbor, clickFace, eyes);
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > maxRangeSq) continue;

            // Verify visibility / reach
            HitResult clip = level.clip(new ClipContext(
                    eyes, hitVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
            ));

            if (clip.getType() == HitResult.Type.BLOCK && !neighbor.equals(((BlockHitResult) clip).getBlockPos())) {
                continue;
            }

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestHit = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return bestHit;
    }

    /**
     * Calculates a strictly valid hit vector on a block face avoiding center-click flags.
     * Samples slightly off-center towards the eye line intersection.
     */
    public static Vec3 calculateStrictHitVec(BlockPos pos, Direction side, Vec3 eyes) {
        Vec3 center = Vec3.atCenterOf(pos);
        double halfX = side.getStepX() * 0.5;
        double halfY = side.getStepY() * 0.5;
        double halfZ = side.getStepZ() * 0.5;

        double faceCenterX = center.x + halfX;
        double faceCenterY = center.y + halfY;
        double faceCenterZ = center.z + halfZ;

        // Slight offset towards player eye pos bounded within face (0.1 .. 0.9)
        double clampMargin = 0.38;
        double dx = Math.max(-clampMargin, Math.min(clampMargin, (eyes.x - faceCenterX) * 0.25));
        double dy = Math.max(-clampMargin, Math.min(clampMargin, (eyes.y - faceCenterY) * 0.25));
        double dz = Math.max(-clampMargin, Math.min(clampMargin, (eyes.z - faceCenterZ) * 0.25));

        if (side.getAxis() == Direction.Axis.X) dx = 0.0;
        if (side.getAxis() == Direction.Axis.Y) dy = 0.0;
        if (side.getAxis() == Direction.Axis.Z) dz = 0.0;

        return new Vec3(faceCenterX + dx, faceCenterY + dy, faceCenterZ + dz);
    }

    /**
     * Direct atomic placement method for modules needing immediate execution.
     */
    public static boolean placeBlock(
            Object owner,
            BlockHitResult hitResult,
            InteractionHand hand,
            int hotbarSlot,
            boolean rotate,
            SwingMode swingMode
    ) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null || client.gameMode == null || hitResult == null) {
            return false;
        }

        if (rotate) {
            Rotation rot = Rotation.lookingAt(hitResult.getLocation(), player.getEyePosition()).normalize();
            RotationManager.INSTANCE.snapServerRotation(rot, 50, owner, 2);
        }

        boolean leased = false;
        if (hand == InteractionHand.MAIN_HAND && hotbarSlot >= 0 && hotbarSlot < 9) {
            leased = InventorySwap.INSTANCE.leaseHotbar(owner, hotbarSlot, 1);
            if (!leased) return false;
        }

        try {
            InteractionResult result = client.gameMode.useItemOn(player, hand, hitResult);
            boolean success = result != null && result.consumesAction();

            if (result == null) {
                InteractionUtil.sendSequencedPacket(sequence ->
                        new ServerboundUseItemOnPacket(hand, hitResult, sequence)
                );
            }

            performSwing(player, hand, swingMode != null ? swingMode : SwingMode.CLIENT_AND_SERVER);
            return success || result != InteractionResult.FAIL;
        } finally {
            if (leased) {
                InventorySwap.INSTANCE.releaseHotbar(owner);
            }
        }
    }

    public static void performSwing(LocalPlayer player, InteractionHand hand, SwingMode swingMode) {
        if (player == null || hand == null || swingMode == SwingMode.NONE) return;

        Minecraft client = Minecraft.getInstance();
        if (swingMode == SwingMode.CLIENT || swingMode == SwingMode.CLIENT_AND_SERVER) {
            player.swing(hand, false);
        }
        if (swingMode == SwingMode.SERVER || swingMode == SwingMode.CLIENT_AND_SERVER) {
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundSwingPacket(hand));
            }
        }
    }

    private static int randomBetween(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static boolean isInteractable(BlockState state) {
        if (state == null) return false;

        Block block = state.getBlock();
        return block instanceof BedBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof EnderChestBlock
                || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof EnchantingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof BeaconBlock
                || block instanceof NoteBlock;
    }

    public void enable() {
        if (registered) return;
        Events.BUS.register(this);
        registered = true;
    }

    public void disable() {
        if (registered) {
            Events.BUS.unregister(this);
            registered = false;
        }
        reset();
        InventorySwap.INSTANCE.releaseHotbar(requester);
    }

    public void tick() {
        if (registered) {
            InventorySwap.INSTANCE.tick();
        }
    }

    public void update(Iterable<BlockPos> positions) {
        LinkedHashSet<BlockPos> next = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null) {
                next.add(pos.immutable());
            }
        }

        blocks.entrySet().removeIf(entry -> !next.contains(entry.getKey()));
        for (BlockPos pos : next) {
            blocks.putIfAbsent(pos, Boolean.FALSE);
        }

        if (currentPlacement != null && !next.contains(currentPlacement.pos())) {
            currentPlacement = null;
        }
    }

    public void clear() {
        blocks.clear();
        inaccessible.clear();
        currentPlacement = null;
    }

    public boolean isDone() {
        return blocks.isEmpty();
    }

    public List<BlockPos> getQueuedPositions() {
        return List.copyOf(blocks.keySet());
    }

    public BlockPos getCurrentPlacementPos() {
        return currentPlacement == null ? null : currentPlacement.pos();
    }

    @EventHandler(priority = -20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!registered || !module.isEnabled()) return;

        if (ticksToWait > 0) {
            ticksToWait--;
        } else if (ranAction) {
            ranAction = false;
            ticksToWait = randomBetween(cooldownMinSupplier.getAsInt(), cooldownMaxSupplier.getAsInt());
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            currentPlacement = null;
            return;
        }

        boolean inventoryOpen = !ignoreOpenInventorySupplier.getAsBoolean() && ClientScreen.current() instanceof AbstractContainerScreen<?>;
        boolean usingItem = !ignoreUsingItemSupplier.getAsBoolean() && player.isUsingItem();
        if (inventoryOpen || usingItem || blocks.isEmpty()) {
            currentPlacement = null;
            return;
        }

        PlacementSlot slot = slotFinder.find(null);
        if (slot == null || slot.stack().isEmpty()) {
            currentPlacement = null;
            return;
        }

        inaccessible.clear();
        currentPlacement = scheduleCurrentPlacement(player, slot.stack());
        if (currentPlacement == null || rotationModeSupplier.get() == RotationMode.NO_ROTATION) {
            return;
        }

        RotationTarget rotationTarget = new RotationTarget(
                currentPlacement.target().getRotation(),
                player,
                List.of(),
                1,
                4.0f,
                false,
                movementCorrectionSupplier.get(),
                new RestrictedSingleUseAction(() -> {
                    PlacementPlan plan = currentPlacement;
                    LocalPlayer currentPlayer = mc.player;
                    if (plan == null || currentPlayer == null || ticksToWait > 0) {
                        return;
                    }

                    Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
                    if (currentRotation == null) return;

                    BlockHitResult currentHit = traceTarget(
                            currentPlayer,
                            currentRotation,
                            Math.max(rangeSupplier.getAsDouble(), wallRangeSupplier.getAsDouble())
                    );
                    if (currentHit == null || !plan.target().getInteractedBlockPos().equals(currentHit.getBlockPos())) {
                        return;
                    }

                    doPlacement(currentPlayer, plan);
                })
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, rotationPriority, requester);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!registered || !module.isEnabled()) return;
        if (sneakTimes > 0) {
            sneakTimes--;
            event.setSneak(true);
        }
    }

    @EventHandler
    private void onPostPlayerUpdate(PostPlayerUpdateEvent event) {
        if (!registered || !module.isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || currentPlacement == null || ticksToWait > 0) {
            return;
        }

        if (rotationModeSupplier.get() != RotationMode.NORMAL) {
            doPlacement(player, currentPlacement);
        }
    }

    private PlacementPlan scheduleCurrentPlacement(LocalPlayer player, ItemStack stack) {
        for (Map.Entry<BlockPos, Boolean> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (inaccessible.contains(pos) || isBlocked(player, pos)) {
                continue;
            }

            ScaffoldPlacementTarget target = ScaffoldTargetFinder.findTarget(
                    player,
                    pos,
                    stack,
                    List.of(BlockPos.ZERO),
                    null,
                    wallRangeSupplier.getAsDouble() > 0.0
            );
            if (target == null) continue;

            if (!canReach(player, target.getInteractedBlockPos(), target.getRotation())) {
                inaccessible.add(pos);
                continue;
            }

            if (isInteractable(player.level().getBlockState(target.getInteractedBlockPos()))) {
                sneakTimes = Math.max(0, sneakTicksSupplier.getAsInt() - 1);
            }

            return new PlacementPlan(pos, target);
        }

        return null;
    }

    private boolean isBlocked(LocalPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        if (!state.canBeReplaced()) {
            inaccessible.add(pos);
            return true;
        }

        List<Entity> entities = player.level().getEntities(
                player,
                new AABB(pos),
                entity -> entity != null && !entity.isRemoved() && !entity.isSpectator()
        );
        if (!entities.isEmpty()) {
            inaccessible.add(pos);
            return true;
        }

        return false;
    }

    private void doPlacement(LocalPlayer player, PlacementPlan plan) {
        currentPlacement = null;
        blocks.remove(plan.pos());

        PlacementSlot slot = slotFinder.find(plan.pos());
        if (slot == null) return;

        Rotation verificationRotation = RotationManager.INSTANCE.getServerRotation();
        if (!canReach(player, plan.target().getInteractedBlockPos(), verificationRotation)) {
            return;
        }

        BlockHitResult hitResult = raytraceTarget(
                player,
                plan.target().getInteractedBlockPos(),
                verificationRotation,
                plan.target().getDirection()
        );
        if (hitResult == null) return;

        boolean leased = false;
        if (slot.hotbarSlot() >= 0 && slot.hotbarSlot() < 9) {
            leased = InventorySwap.INSTANCE.leaseHotbar(
                    requester,
                    slot.hotbarSlot(),
                    randomBetween(slotResetDelayMinSupplier.getAsInt(), slotResetDelayMaxSupplier.getAsInt())
            );
        }

        try {
            if (slot.stack().getItem() instanceof BlockItem && !player.level().getBlockState(plan.pos()).canBeReplaced()) {
                return;
            }

            InteractionResult result = mc.gameMode.useItemOn(player, slot.hand(), hitResult);

            if (result == null) {
                InteractionUtil.sendSequencedPacket(sequence ->
                        new ServerboundUseItemOnPacket(slot.hand(), hitResult, sequence)
                );
            }

            performSwing(player, slot.hand(), swingModeSupplier.get());
            if (result != null && result.consumesAction()) {
                ranAction = true;
            }
        } finally {
            if (leased && (slotResetDelayMaxSupplier.getAsInt() <= 0)) {
                InventorySwap.INSTANCE.releaseHotbar(requester);
            }
        }
    }

    private BlockHitResult raytraceTarget(LocalPlayer player, BlockPos interactedPos, Rotation rotation, Direction direction) {
        BlockHitResult hitResult = traceTarget(player, rotation, Math.max(rangeSupplier.getAsDouble(), wallRangeSupplier.getAsDouble()));
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK && interactedPos.equals(hitResult.getBlockPos())) {
            Vec3 strictLocation = calculateStrictHitVec(interactedPos, direction, player.getEyePosition());
            return new BlockHitResult(strictLocation, direction, interactedPos, false);
        }

        if (constructFailResultSupplier.getAsBoolean()) {
            Vec3 strictLocation = calculateStrictHitVec(interactedPos, direction, player.getEyePosition());
            return new BlockHitResult(strictLocation, direction, interactedPos, false);
        }

        return null;
    }

    private boolean canReach(LocalPlayer player, BlockPos interactedPos, Rotation rotation) {
        double wallRange = wallRangeSupplier.getAsDouble();
        if (Vec3.atCenterOf(interactedPos).distanceToSqr(player.getEyePosition()) <= wallRange * wallRange) {
            return true;
        }

        BlockHitResult hitResult = traceTarget(player, rotation, rangeSupplier.getAsDouble());
        return hitResult != null && interactedPos.equals(hitResult.getBlockPos());
    }

    private BlockHitResult traceTarget(LocalPlayer player, Rotation rotation, double range) {
        if (player == null || rotation == null || player.level() == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        return hitResult instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private void reset() {
        ticksToWait = 0;
        ranAction = false;
        sneakTimes = 0;
        clear();
    }

    public enum RotationMode {
        NORMAL,
        NO_ROTATION
    }

    public enum SwingMode {
        CLIENT,
        SERVER,
        CLIENT_AND_SERVER,
        NONE
    }

    @FunctionalInterface
    public interface SlotFinder {
        PlacementSlot find(BlockPos targetPos);
    }

    public record PlacementSlot(int hotbarSlot, InteractionHand hand, ItemStack stack) {
    }

    private record PlacementPlan(BlockPos pos, ScaffoldPlacementTarget target) {
    }
}
