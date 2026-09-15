/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "autotunnel",
        displayName = "AutoTunnel",
        category = ModuleCategory.MOVEMENT,
        description = "Automates digging 1x2 or 2x3 tunnels through Netherrack on anarchy servers."
)
public final class AutoTunnel extends Module {

    private final NumberValue<Integer> width = num("width", "width", 1, 1, 3);
    private final NumberValue<Integer> height = num("height", "height", 2, 2, 3);
    private final NumberValue<Integer> delayTicks = num("delayTicks", "delay_ticks", 0, 0, 5);
    private final BooleanValue plugLiquids = bool("plugLiquids", "plug_liquids", true);
    private final BooleanValue autoWalk = bool("autoWalk", "auto_walk", true);
    private final BooleanValue autoTorch = bool("autoTorch", "auto_torch", false);

    private final Minecraft mc = Minecraft.getInstance();

    private BlockPos currentMiningPos = null;
    private int delayTimer = 0;
    private boolean hasLiquidHazard = false;
    private BlockPos lastTorchPos = null;

    @Override
    public void onEnable() {
        currentMiningPos = null;
        delayTimer = 0;
        hasLiquidHazard = false;
        lastTorchPos = null;
        alignPlayerDirection();
    }

    @Override
    public void onDisable() {
        if (mc.gameMode != null && currentMiningPos != null) {
            mc.gameMode.stopDestroyBlock();
        }
        InventorySwap.INSTANCE.releaseHotbar(this);
        currentMiningPos = null;
        delayTimer = 0;
        hasLiquidHazard = false;
        lastTorchPos = null;
    }

    @EventHandler
    public void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || mc.player == null) return;
        if (ClientScreen.current() != null && !(ClientScreen.current() instanceof ChatScreen)) return;

        if (autoWalk.get()) {
            if (hasLiquidHazard) {
                event.setForward(false);
            } else {
                event.setForward(true);
                event.setSprint(false);

                // Prevent stepping off dangerous edges when the floor in front is missing
                Direction facing = mc.player.getDirection();
                BlockPos floorAhead = mc.player.blockPosition().relative(facing).below();
                if (mc.level != null && (mc.level.getBlockState(floorAhead).isAir() || isLiquid(mc.level, floorAhead))) {
                    event.setSneak(true);
                }
            }
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (ClientScreen.current() != null && !(ClientScreen.current() instanceof ChatScreen)) {
            return;
        }

        LocalPlayer player = mc.player;
        Level level = mc.level;

        // Keep direction aligned to cardinal axes
        alignPlayerDirection();

        // 1. Handle liquid plugging before proceeding with mining or walking
        if (plugLiquids.get()) {
            boolean plugged = handleLiquidPlugging(player, level);
            if (plugged) {
                hasLiquidHazard = true;
                return;
            }
        }

        // Check if there is still an unplugged liquid hazard in the profile
        hasLiquidHazard = checkLiquidHazard(player, level);

        // 2. Handle AutoTorch if enabled
        if (autoTorch.get()) {
            handleAutoTorch(player, level);
        }

        // 3. Handle delay timer between blocks
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // 4. Validate or update active mining position
        if (currentMiningPos != null) {
            if (!isBreakable(level, player, currentMiningPos)) {
                // Block has been destroyed or is no longer valid
                mc.gameMode.stopDestroyBlock();
                InventorySwap.INSTANCE.releaseHotbar(this);
                currentMiningPos = null;
                delayTimer = delayTicks.get();
                if (delayTimer > 0) {
                    return;
                }
            }
        }

        // 5. If not mining, scan for the next priority block in the tunnel profile
        if (currentMiningPos == null) {
            currentMiningPos = findNextTargetBlock(player, level);
        }

        // 6. Mine target block with silent tool selection
        if (currentMiningPos != null) {
            mineBlock(player, level, currentMiningPos);
        } else {
            InventorySwap.INSTANCE.releaseHotbar(this);
        }
    }

    private void alignPlayerDirection() {
        if (mc.player == null) return;
        float currentYaw = mc.player.getYRot();
        float targetYaw = Math.round(currentYaw / 90.0f) * 90.0f;
        mc.player.setYRot(targetYaw);
    }

    private boolean checkLiquidHazard(LocalPlayer player, Level level) {
        if (!plugLiquids.get()) return false;
        List<BlockPos> profile = getTunnelProfilePositions(player, 2);
        for (BlockPos pos : profile) {
            if (isLiquid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleLiquidPlugging(LocalPlayer player, Level level) {
        List<BlockPos> profile = getTunnelProfilePositions(player, 4);
        List<BlockPos> liquidPositions = new ArrayList<>();

        for (BlockPos pos : profile) {
            if (isLiquid(level, pos)) {
                liquidPositions.add(pos);
            }
            // Check ceiling directly above tunnel profile for dripping leaks
            BlockPos above = pos.above();
            if (isLiquid(level, above) && isReachable(player, above)) {
                liquidPositions.add(above);
            }
        }

        if (liquidPositions.isEmpty()) {
            return false;
        }

        int solidSlot = findSolidBlockSlot(player);
        if (solidSlot < 0) {
            return false;
        }

        for (BlockPos liquidPos : liquidPositions) {
            BlockHitResult hitResult = resolvePlacementHitResult(level, player, liquidPos);
            if (hitResult != null) {
                if (placeBlock(player, hitResult, solidSlot)) {
                    return true;
                }
            }
        }

        return false;
    }

    private BlockHitResult resolvePlacementHitResult(Level level, LocalPlayer player, BlockPos targetPos) {
        Vec3 eyes = player.getEyePosition();
        double bestDistSq = Double.MAX_VALUE;
        BlockHitResult best = null;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = targetPos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) continue;

            BlockState state = level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, neighbor).isEmpty()) {
                continue;
            }

            Direction clickFace = dir.getOpposite();
            Vec3 normal = Vec3.atLowerCornerOf(clickFace.getUnitVec3i());
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(normal.scale(0.5));
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > 36.0) continue;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private boolean placeBlock(LocalPlayer player, BlockHitResult hitResult, int slot) {
        if (mc.gameMode == null || hitResult == null) return false;

        boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);
        if (!leased) {
            InventorySwap.INSTANCE.selectHotbar(slot);
        }

        try {
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
            if (result != null && result.consumesAction()) {
                player.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        } finally {
            InventorySwap.INSTANCE.releaseHotbar(this);
        }
        return false;
    }

    private BlockPos findNextTargetBlock(LocalPlayer player, Level level) {
        List<BlockPos> profile = getTunnelProfilePositions(player, 4);
        for (BlockPos pos : profile) {
            if (isBreakable(level, player, pos)) {
                return pos;
            }
        }
        return null;
    }

    private void mineBlock(LocalPlayer player, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        int toolSlot = findBestToolSlot(state);
        if (toolSlot >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
        }

        Direction side = getMiningSide(player, pos);

        BlockPos currentBreaking = null;
        if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            currentBreaking = accessor.combatant$getCurrentBreakingPos();
        }

        if (pos.equals(currentBreaking) && mc.gameMode.isDestroying()) {
            mc.gameMode.continueDestroyBlock(pos, side);
        } else {
            mc.gameMode.startDestroyBlock(pos, side);
        }

        player.swing(InteractionHand.MAIN_HAND);
    }

    private Direction getMiningSide(LocalPlayer player, BlockPos pos) {
        return player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
    }

    private boolean isBreakable(Level level, LocalPlayer player, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (isLiquid(level, pos)) return false;
        return isReachable(player, pos);
    }

    private boolean isReachable(LocalPlayer player, BlockPos pos) {
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 36.0;
    }

    private boolean isLiquid(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LiquidBlock || !state.getFluidState().isEmpty();
    }

    /**
     * Generates tunnel profile positions prioritized by distance and direct obstruction to forward movement.
     */
    private List<BlockPos> getTunnelProfilePositions(LocalPlayer player, int maxDistance) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();
        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();

        int w = width.get();
        int h = height.get();

        int[] widthOffsets = (w == 1) ? new int[]{0} : ((w == 2) ? new int[]{0, 1} : new int[]{0, -1, 1});
        int[] heightOffsets = (h == 2) ? new int[]{1, 0} : new int[]{1, 0, 2};

        for (int d = 1; d <= maxDistance; d++) {
            BlockPos colBase = playerPos.relative(facing, d);

            // 1. Center column prioritized first (directly blocks forward walking)
            for (int yOff : heightOffsets) {
                list.add(colBase.above(yOff));
            }

            // 2. Additional width columns
            for (int wOff : widthOffsets) {
                if (wOff == 0) continue;
                for (int yOff : heightOffsets) {
                    list.add(colBase.relative(right, wOff).above(yOff));
                }
            }
        }

        return list;
    }

    private int findBestToolSlot(BlockState state) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        // Check hotbar
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        // If no efficient tool found in hotbar, search main inventory and swap
        if (bestSlot < 0) {
            int bestInvSlot = -1;
            float bestInvSpeed = 1.0f;
            for (int slot = 9; slot < 36; slot++) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (stack == null || stack.isEmpty()) continue;
                float speed = stack.getDestroySpeed(state);
                if (speed > bestInvSpeed) {
                    bestInvSpeed = speed;
                    bestInvSlot = slot;
                }
            }
            if (bestInvSlot >= 0) {
                int targetHotbar = InventorySwap.INSTANCE.clientSelectedSlot();
                if (targetHotbar < 0 || targetHotbar >= 9) targetHotbar = 0;
                InventorySwap.INSTANCE.swapInventoryToHotbar(bestInvSlot, targetHotbar);
                return targetHotbar;
            }
        }

        return bestSlot;
    }

    private int findSolidBlockSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSolidBlockItem(stack)) {
                return slot;
            }
        }

        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isSolidBlockItem(stack)) {
                int targetHotbar = -1;
                for (int h = 0; h < 9; h++) {
                    ItemStack hStack = player.getInventory().getItem(h);
                    if (hStack.isEmpty() || !hStack.is(ItemTags.PICKAXES)) {
                        targetHotbar = h;
                        break;
                    }
                }
                if (targetHotbar < 0) targetHotbar = 8;
                InventorySwap.INSTANCE.swapInventoryToHotbar(slot, targetHotbar);
                return targetHotbar;
            }
        }

        return -1;
    }

    private boolean isSolidBlockItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock) return false;
        if (block instanceof TorchBlock) return false;
        BlockState state = block.defaultBlockState();
        return state.isSolidRender() && !state.canBeReplaced();
    }

    private void handleAutoTorch(LocalPlayer player, Level level) {
        BlockPos currentPos = player.blockPosition();
        if (lastTorchPos != null && currentPos.distManhattan(lastTorchPos) < 10) {
            return;
        }

        int torchSlot = findTorchSlot(player);
        if (torchSlot < 0) return;

        BlockPos floorPos = currentPos.below();
        BlockState floorState = level.getBlockState(floorPos);
        BlockState feetState = level.getBlockState(currentPos);

        if (feetState.isAir() && !floorState.isAir() && !floorState.canBeReplaced() && !floorState.getCollisionShape(level, floorPos).isEmpty()) {
            Vec3 hitVec = Vec3.atCenterOf(floorPos).add(0, 0.5, 0);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, floorPos, false);
            if (placeBlock(player, hitResult, torchSlot)) {
                lastTorchPos = currentPos;
            }
        }
    }

    private int findTorchSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH))) {
                return slot;
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH))) {
                int targetHotbar = 7;
                InventorySwap.INSTANCE.swapInventoryToHotbar(slot, targetHotbar);
                return targetHotbar;
            }
        }
        return -1;
    }
}
