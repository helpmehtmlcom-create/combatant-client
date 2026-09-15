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
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatically detects and prevents Cev-Breaker crystal attacks by attacking
 * crystals and/or placing defensive obsidian blocks to absorb explosive blast rays.
 */
@ModuleInfo(
        id = "anticev",
        displayName = "AntiCev",
        aliases = {"cevpreventer"},
        category = ModuleCategory.COMBAT,
        description = "Prevents Cev-Breaker attacks by destroying crystals and placing blast-absorbing obsidian."
)
public class AntiCev extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<AntiCevMode> mode =
            enumSetting("anticev_mode", "mode", AntiCevMode.BOTH, AntiCevMode.values());

    private final NumberValue<Integer> breakDelay =
            num("anticev_break_delay", "breakDelay", 0, 0, 5);

    private final BooleanValue packetBreak =
            bool("anticev_packet_break", "packetBreak", true);

    private int delayTimer = 0;

    @EventHandler(priority = 30)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (mc.player == null || mc.level == null) return;

        if (delayTimer > 0) {
            delayTimer--;
        }

        LocalPlayer player = mc.player;
        Level level = mc.level;

        List<BlockPos> basePositions = resolveBasePositions(player);
        List<EndCrystal> detectedCrystals = new ArrayList<>();
        List<BlockPos> threatenedHeads = new ArrayList<>();

        for (BlockPos base : basePositions) {
            BlockPos headPos = base.above(2);
            BlockPos topPos = base.above(3);

            List<EndCrystal> crystals = findCevCrystals(level, headPos, topPos);
            if (!crystals.isEmpty()) {
                threatenedHeads.add(headPos);
                for (EndCrystal crystal : crystals) {
                    if (!detectedCrystals.contains(crystal)) {
                        detectedCrystals.add(crystal);
                    }
                }
            }
        }

        if (detectedCrystals.isEmpty()) return;

        AntiCevMode currentMode = mode.get();

        // 1. BREAK mode or BOTH: attack/destroy the detected crystals
        if (currentMode == AntiCevMode.BREAK || currentMode == AntiCevMode.BOTH) {
            if (delayTimer <= 0) {
                for (EndCrystal crystal : detectedCrystals) {
                    if (crystal.isAlive() && !crystal.isRemoved()) {
                        attackCrystal(crystal);
                    }
                }
                delayTimer = breakDelay.get();
            }
        }

        // 2. BLOCK mode or BOTH: place obsidian in intermediate space or above to absorb blast rays
        if (currentMode == AntiCevMode.BLOCK || currentMode == AntiCevMode.BOTH) {
            for (BlockPos headPos : threatenedHeads) {
                blockCevRay(headPos, level, player);
            }
        }
    }

    private List<EndCrystal> findCevCrystals(Level level, BlockPos headPos, BlockPos topPos) {
        AABB searchBox = new AABB(headPos).minmax(new AABB(topPos)).inflate(1.5, 1.0, 1.5);
        List<EndCrystal> entities = level.getEntitiesOfClass(EndCrystal.class, searchBox, Entity::isAlive);
        List<EndCrystal> matched = new ArrayList<>();

        for (EndCrystal crystal : entities) {
            if (isCevCrystal(crystal, headPos, topPos)) {
                matched.add(crystal);
            }
        }
        return matched;
    }

    private boolean isCevCrystal(EndCrystal crystal, BlockPos headPos, BlockPos topPos) {
        BlockPos crystalBlock = crystal.blockPosition();

        if (crystalBlock.equals(headPos) || crystalBlock.equals(topPos)) {
            return true;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (crystalBlock.equals(headPos.relative(dir)) || crystalBlock.equals(topPos.relative(dir))) {
                return true;
            }
        }

        AABB crystalBox = crystal.getBoundingBox();
        AABB headBox = new AABB(headPos);
        AABB topBox = new AABB(topPos);

        return crystalBox.intersects(headBox.inflate(1.0, 0.5, 1.0))
                || crystalBox.intersects(topBox.inflate(1.0, 0.5, 1.0));
    }

    private void attackCrystal(EndCrystal crystal) {
        if (mc.player == null) return;

        if (packetBreak.get()) {
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundInteractPacket(
                        crystal.getId(),
                        null,
                        null,
                        mc.player.isShiftKeyDown()
                ));
            }
        } else {
            if (mc.gameMode != null) {
                mc.gameMode.attack(mc.player, crystal);
            }
        }

        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void blockCevRay(BlockPos headPos, Level level, LocalPlayer player) {
        // Priority 1: Intermediate space directly above head (headPos at y+2) if broken/replaceable
        if (isReplaceable(level, headPos) && !isPlayerIntersecting(player, headPos)) {
            if (placeObsidian(headPos)) {
                return;
            }
        }

        // Priority 2: Above the head block (pos.above(3) / ceiling)
        BlockPos ceiling = headPos.above();
        if (isReplaceable(level, ceiling) && !isEntityBlocked(level, ceiling)) {
            if (placeObsidian(ceiling)) {
                return;
            }
        }

        // Priority 3: Higher blast ray absorption block at pos.above(4)
        BlockPos highCeiling = ceiling.above();
        if (isReplaceable(level, highCeiling) && !isEntityBlocked(level, highCeiling)) {
            placeObsidian(highCeiling);
        }
    }

    private boolean placeObsidian(BlockPos target) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;

        BlockHitResult hit = getPlaceHitResult(mc.level, mc.player, target);
        if (hit == null) return false;

        // Check offhand for obsidian
        if (isObsidian(mc.player.getOffhandItem())) {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, hit);
            mc.player.swing(InteractionHand.OFF_HAND);
            return result != InteractionResult.FAIL;
        }

        // Hotbar leasing for obsidian
        int hotbarSlot = findObsidianHotbarSlot(mc.player);
        if (hotbarSlot == -1) return false;

        if (InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 1)) {
            try {
                InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return result != InteractionResult.FAIL;
            } finally {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }

        return false;
    }

    private BlockHitResult getPlaceHitResult(Level level, LocalPlayer player, BlockPos pos) {
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
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(clickFace.getUnitVec3i()).scale(0.5));
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > 36.0) continue; // 6 blocks reach

            if (distSq < bestDist) {
                bestDist = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private int findObsidianHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isObsidian(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isObsidian(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN));
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private boolean isPlayerIntersecting(LocalPlayer player, BlockPos pos) {
        return player.getBoundingBox().intersects(new AABB(pos));
    }

    private boolean isEntityBlocked(Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity instanceof EndCrystal) continue;
            if (entity.isAlive()) return true;
        }
        return false;
    }

    private List<BlockPos> resolveBasePositions(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        int y = Mth.floor(player.getY() + 0.1);

        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!positions.contains(p)) {
                    positions.add(p);
                }
            }
        }
        return positions.isEmpty() ? List.of(player.blockPosition()) : positions;
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        delayTimer = 0;
    }

    public enum AntiCevMode {
        BREAK,
        BLOCK,
        BOTH
    }
}
