/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Automatically places Ender Chests and mines them with a non-Silk Touch pickaxe
 * to rapidly farm stacks of obsidian on anarchy servers.
 */
@ModuleInfo(
        id = "echestfarmer",
        displayName = "EChestFarmer",
        category = ModuleCategory.PLAYER,
        description = "Automatically places and mines Ender Chests to farm obsidian."
)
public final class EChestFarmer extends Module {

    private final NumberValue<Integer> delayTicks =
            num("echestFarmerDelayTicks", "delayTicks", 1, 0, 5);
    private final NumberValue<Integer> maxObsidianStacks =
            num("echestFarmerMaxObsidianStacks", "maxObsidianStacks", 5, 1, 20);
    private final BooleanValue silentSwitch =
            bool("echestFarmerSilentSwitch", "silentSwitch", true);
    private final BooleanValue autoPickaxe =
            bool("echestFarmerAutoPickaxe", "autoPickaxe", true);
    private final BooleanValue rotate =
            bool("echestFarmerRotate", "rotate", true);

    private final Minecraft mc = Minecraft.getInstance();

    private BlockPos miningPos = null;
    private Direction miningSide = Direction.UP;
    private boolean isBreaking = false;
    private int tickDelayTimer = 0;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        miningPos = null;
        miningSide = Direction.UP;
        isBreaking = false;
        tickDelayTimer = 0;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) {
            resetState();
            return;
        }

        LocalPlayer player = mc.player;
        Level level = mc.level;

        // 1. Check if player already reached obsidian limit
        if (countObsidian() >= maxObsidianStacks.get() * 64) {
            if (isBreaking && miningPos != null) {
                // Finish breaking current echest if already started
                continueMining(player, miningPos);
            } else {
                resetState();
            }
            return;
        }

        // Delay tick countdown
        if (tickDelayTimer > 0) {
            tickDelayTimer--;
            return;
        }

        // 2. If already mining an Ender Chest, continue mining it
        if (miningPos != null) {
            BlockState state = level.getBlockState(miningPos);
            if (state.is(Blocks.ENDER_CHEST)) {
                continueMining(player, miningPos);
                return;
            } else {
                // Chest was destroyed or disappeared
                miningPos = null;
                isBreaking = false;
                InventorySwap.INSTANCE.releaseHotbar(this);
                tickDelayTimer = delayTicks.get();
                return;
            }
        }

        // 3. No current mining position. Look for an existing placed Ender Chest nearby in front of player
        BlockPos existingEChest = findExistingEChest(player, level);
        if (existingEChest != null) {
            startMining(player, existingEChest);
            return;
        }

        // 4. No placed Ender Chest found. Check if player has Ender Chests to place
        int echestSlot = findEChestSlot(player);
        boolean hasOffhandEChest = isEChest(player.getOffhandItem());
        if (echestSlot < 0 && !hasOffhandEChest) {
            // Out of Ender Chests
            return;
        }

        // 5. Find a valid position in front of player to place Ender Chest
        BlockPos placePos = findPlacementPosition(player, level);
        if (placePos == null) {
            return;
        }

        BlockHitResult hitResult = resolveHitResult(level, player, placePos);
        if (hitResult == null) {
            return;
        }

        // Place Ender Chest
        if (placeEChest(player, hitResult, echestSlot, hasOffhandEChest)) {
            miningPos = placePos;
            tickDelayTimer = delayTicks.get();
        }
    }

    private void startMining(LocalPlayer player, BlockPos pos) {
        miningPos = pos;
        miningSide = getBestDirection(player, pos);

        if (rotate.get()) {
            rotateTo(player, Vec3.atCenterOf(pos));
        }

        if (autoPickaxe.get()) {
            equipBestPickaxe();
        }

        isBreaking = true;
        mc.gameMode.startDestroyBlock(miningPos, miningSide);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void continueMining(LocalPlayer player, BlockPos pos) {
        if (rotate.get()) {
            rotateTo(player, Vec3.atCenterOf(pos));
        }

        if (autoPickaxe.get()) {
            equipBestPickaxe();
        }

        if (!isBreaking) {
            isBreaking = true;
            mc.gameMode.startDestroyBlock(pos, miningSide);
        } else {
            mc.gameMode.continueDestroyBlock(pos, miningSide);
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean placeEChest(LocalPlayer player, BlockHitResult hitResult, int echestSlot, boolean offhand) {
        if (rotate.get()) {
            rotateTo(player, hitResult.getLocation());
        }

        InteractionHand hand;
        if (offhand) {
            hand = InteractionHand.OFF_HAND;
        } else {
            hand = InteractionHand.MAIN_HAND;
            boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, echestSlot, 1);
            if (!leased) {
                InventorySwap.INSTANCE.selectHotbar(echestSlot);
            }
        }

        try {
            mc.gameMode.useItemOn(player, hand, hitResult);
            player.swing(hand);
            return true;
        } finally {
            if (!offhand) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private void equipBestPickaxe() {
        int bestSlot = findBestPickaxeSlot();
        if (bestSlot >= 0) {
            if (silentSwitch.get()) {
                InventorySwap.INSTANCE.leaseHotbar(this, bestSlot, 2);
            } else {
                InventorySwap.INSTANCE.selectHotbar(bestSlot);
            }
        }
    }

    private int findBestPickaxeSlot() {
        if (mc.player == null) return -1;

        BlockState echestState = Blocks.ENDER_CHEST.defaultBlockState();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!isPickaxe(stack)) continue;
            if (hasSilkTouch(stack)) continue;

            float speed = stack.getDestroySpeed(echestState);
            if (speed > bestSpeed || bestSlot == -1) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot != -1) return bestSlot;

        // Check main inventory (9..35)
        int bestInvSlot = -1;
        float bestInvSpeed = 1.0f;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!isPickaxe(stack)) continue;
            if (hasSilkTouch(stack)) continue;

            float speed = stack.getDestroySpeed(echestState);
            if (speed > bestInvSpeed || bestInvSlot == -1) {
                bestInvSpeed = speed;
                bestInvSlot = i;
            }
        }

        if (bestInvSlot != -1) {
            int targetHotbar = InventorySwap.INSTANCE.clientSelectedSlot();
            if (targetHotbar < 0 || targetHotbar >= 9) targetHotbar = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack hStack = mc.player.getInventory().getItem(i);
                if (hStack.isEmpty() || !isEChest(hStack)) {
                    targetHotbar = i;
                    break;
                }
            }
            InventorySwap.INSTANCE.swapInventoryToHotbar(bestInvSlot, targetHotbar);
            return targetHotbar;
        }

        return -1;
    }

    private boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES);
    }

    private boolean hasSilkTouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (mc.level != null) {
            try {
                Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                Enchantment silkTouch = registry.getValue(Enchantments.SILK_TOUCH);
                if (silkTouch != null) {
                    Holder<Enchantment> holder = registry.wrapAsHolder(silkTouch);
                    if (EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.unwrapKey().isPresent()) {
                ResourceKey<Enchantment> key = holder.unwrapKey().get();
                if (key.equals(Enchantments.SILK_TOUCH) || key.identifier().getPath().equals("silk_touch")) {
                    return true;
                }
            }
        }
        return false;
    }

    private int findEChestSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isEChest(stack)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isEChest(stack)) {
                int targetHotbar = InventorySwap.INSTANCE.clientSelectedSlot();
                if (targetHotbar < 0 || targetHotbar >= 9) targetHotbar = 0;
                for (int h = 0; h < 9; h++) {
                    ItemStack hStack = player.getInventory().getItem(h);
                    if (hStack.isEmpty() || !isPickaxe(hStack)) {
                        targetHotbar = h;
                        break;
                    }
                }
                InventorySwap.INSTANCE.swapInventoryToHotbar(i, targetHotbar);
                return targetHotbar;
            }
        }

        return -1;
    }

    private boolean isEChest(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.ENDER_CHEST) || stack.is(Blocks.ENDER_CHEST.asItem());
    }

    private BlockPos findExistingEChest(LocalPlayer player, Level level) {
        BlockPos feet = player.blockPosition();
        Direction facing = player.getDirection();

        BlockPos[] candidates = new BlockPos[]{
                feet.relative(facing),
                feet.relative(facing).above(),
                feet.relative(facing, 2),
                feet.relative(facing).below(),
                feet.relative(facing.getClockWise()),
                feet.relative(facing.getCounterClockWise())
        };

        for (BlockPos pos : candidates) {
            if (level.getBlockState(pos).is(Blocks.ENDER_CHEST)) {
                if (player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= 4.5) {
                    return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findPlacementPosition(LocalPlayer player, Level level) {
        BlockPos feet = player.blockPosition();
        Direction facing = player.getDirection();

        BlockPos[] candidates = new BlockPos[]{
                feet.relative(facing),
                feet.relative(facing, 2),
                feet.relative(facing).below(),
                feet.relative(facing).above(),
                feet.relative(facing.getClockWise()),
                feet.relative(facing.getCounterClockWise())
        };

        for (BlockPos pos : candidates) {
            if (isValidPlacementPosition(player, level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isValidPlacementPosition(LocalPlayer player, Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) return false;

        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;

        AABB box = new AABB(pos);
        if (player.getBoundingBox().intersects(box)) return false;

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box, Entity::isAlive);
        if (!entities.isEmpty()) return false;

        return resolveHitResult(level, player, pos) != null;
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

    private Direction getBestDirection(LocalPlayer player, BlockPos pos) {
        return player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
    }

    private void rotateTo(LocalPlayer player, Vec3 target) {
        Rotation rot = Rotation.lookingAt(target, player.getEyePosition());
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    rot.yaw(),
                    rot.pitch(),
                    player.onGround(),
                    player.horizontalCollision
            ));
        }
    }

    private int countObsidian() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && stack.is(Items.OBSIDIAN)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
