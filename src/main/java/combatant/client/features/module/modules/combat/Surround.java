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
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically surrounds player feet with blast-resistant blocks (Obsidian, Crying Obsidian, Ender Chest)
 * to protect against crystal explosions and anchor detonations.
 */
@ModuleInfo(
        id = "surround",
        displayName = "Surround",
        category = ModuleCategory.COMBAT,
        aliases = {"autocage", "feetarmor"},
        description = "Surrounds player feet with blast-resistant blocks."
)
public class Surround extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private final BooleanValue antiCity =
            bool("anti_city", "anti_city", true);
    private final BooleanValue antiFaceplace =
            bool("anti_faceplace", "anti_faceplace", false);
    private final BooleanValue russian =
            bool("russian", "russian", false);
    private final BooleanValue center =
            bool("center", "center", true);
    private final NumberValue<Integer> blocksPerTick =
            num("blocks_per_tick", "blocks_per_tick", 4, 1, 8);
    private final NumberValue<Integer> delayTicks =
            num("delay_ticks", "delay_ticks", 0, 0, 5);
    private final BooleanValue rotate =
            bool("rotate", "rotate", true);
    private final BooleanValue render =
            bool("render", "render", true);

    // Runtime state
    private int delayCounter = 0;
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> miningBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        delayCounter = 0;
        renderBlocks.clear();
        miningBlocks.clear();

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (center.get()) {
            centerPlayer(player);
        }
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        renderBlocks.clear();
        miningBlocks.clear();
    }

    private void centerPlayer(LocalPlayer player) {
        double centerX = Mth.floor(player.getX()) + 0.5;
        double centerZ = Mth.floor(player.getZ()) + 0.5;
        double dx = Math.abs(centerX - player.getX());
        double dz = Math.abs(centerZ - player.getZ());

        if (dx > 0.05 || dz > 0.05) {
            player.setPos(centerX, player.getY(), centerZ);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        centerX, player.getY(), centerZ, player.onGround(), player.horizontalCollision
                ));
            }
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            BlockPos pos = packet.getPos();
            int progress = packet.getProgress();
            if (progress >= 0) {
                miningBlocks.put(pos, System.currentTimeMillis());
            } else {
                miningBlocks.remove(pos);
            }
        }
    }

    @EventHandler
    private void onBreakBlock(EventBreakBlock event) {
        if (!isEnabled()) return;
        if (event.getPos() != null) {
            miningBlocks.put(event.getPos(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;
        if (player.isSpectator() || !player.isAlive()) return;

        // Check if player is on ground or stationary
        boolean stationary = Math.abs(player.getDeltaMovement().x) < 0.05
                && Math.abs(player.getDeltaMovement().z) < 0.05;
        if (!player.onGround() && !stationary) {
            return;
        }

        // Clean up mining entries older than 2 seconds
        long now = System.currentTimeMillis();
        miningBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);

        // Delay tick handling
        if (delayTicks.get() > 0 && delayCounter > 0) {
            delayCounter--;
            return;
        }

        executePlacement(player, level);
        delayCounter = delayTicks.get();
    }

    private void executePlacement(LocalPlayer player, Level level) {
        int px = Mth.floor(player.getX());
        int py = Mth.floor(player.getY());
        int pz = Mth.floor(player.getZ());
        BlockPos playerPos = new BlockPos(px, py, pz);

        LinkedHashSet<BlockPos> targetPositions = new LinkedHashSet<>();

        // 1. Primary surround positions around feet
        BlockPos east = playerPos.east();
        BlockPos west = playerPos.west();
        BlockPos south = playerPos.south();
        BlockPos north = playerPos.north();

        targetPositions.add(east);
        targetPositions.add(west);
        targetPositions.add(south);
        targetPositions.add(north);

        // 2. AntiCity reinforcement: extra reinforcement blocks when an adjacent block is being mined or broken
        if (antiCity.get()) {
            for (BlockPos feetPos : List.of(east, west, south, north)) {
                if (miningBlocks.containsKey(feetPos) || isReplaceable(level, feetPos)) {
                    targetPositions.add(feetPos.above());
                    targetPositions.add(new BlockPos(feetPos.getX() + (feetPos.getX() - px), py, feetPos.getZ() + (feetPos.getZ() - pz)));
                }
            }
        }

        // 3. Russian double-layer surround (2 blocks out in all directions)
        if (russian.get()) {
            targetPositions.add(playerPos.offset(2, 0, 0));
            targetPositions.add(playerPos.offset(-2, 0, 0));
            targetPositions.add(playerPos.offset(0, 0, 2));
            targetPositions.add(playerPos.offset(0, 0, -2));
        }

        // 4. AntiFaceplace: if enemy within 4 blocks, also add (x±1, y+1, z±1)
        if (antiFaceplace.get() && isEnemyWithinRange(player, level, 4.0)) {
            targetPositions.add(new BlockPos(px + 1, py + 1, pz));
            targetPositions.add(new BlockPos(px - 1, py + 1, pz));
            targetPositions.add(new BlockPos(px, py + 1, pz + 1));
            targetPositions.add(new BlockPos(px, py + 1, pz - 1));
            targetPositions.add(new BlockPos(px + 1, py + 1, pz + 1));
            targetPositions.add(new BlockPos(px + 1, py + 1, pz - 1));
            targetPositions.add(new BlockPos(px - 1, py + 1, pz + 1));
            targetPositions.add(new BlockPos(px - 1, py + 1, pz - 1));
        }

        // Find missing blocks (air or replaceable)
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos pos : targetPositions) {
            if (isReplaceable(level, pos) && !isEntityColliding(player, level, pos)) {
                missing.add(pos);
            }
        }

        if (missing.isEmpty()) return;

        // Find obsidian / crying obsidian / ender chest in hotbar or inventory
        int slot = findSurroundBlockSlot(player);
        if (slot < 0) return;

        int placed = 0;
        int maxBlocks = blocksPerTick.get();

        for (BlockPos pos : missing) {
            if (placed >= maxBlocks) break;

            BlockHitResult hitResult = BlockPlacer.findOptimalPlacementHit(level, player, pos, 6.0);
            if (hitResult == null) {
                // If no neighbor to click against, check if we need a helper floor block
                BlockPos floorPos = pos.below();
                if (isReplaceable(level, floorPos) && !isEntityColliding(player, level, floorPos)) {
                    BlockHitResult floorHit = BlockPlacer.findOptimalPlacementHit(level, player, floorPos, 6.0);
                    if (floorHit != null) {
                        if (BlockPlacer.placeBlock(this, floorHit, InteractionHand.MAIN_HAND, slot, rotate.get(), BlockPlacer.SwingMode.CLIENT_AND_SERVER)) {
                            renderBlocks.put(floorPos, System.currentTimeMillis());
                            placed++;
                            if (placed >= maxBlocks) break;
                        }
                    }
                }
                hitResult = BlockPlacer.findOptimalPlacementHit(level, player, pos, 6.0);
            }

            if (hitResult != null) {
                if (BlockPlacer.placeBlock(this, hitResult, InteractionHand.MAIN_HAND, slot, rotate.get(), BlockPlacer.SwingMode.CLIENT_AND_SERVER)) {
                    renderBlocks.put(pos, System.currentTimeMillis());
                    placed++;
                }
            }
        }
    }

    private boolean isEnemyWithinRange(LocalPlayer player, Level level, double range) {
        double rangeSq = range * range;
        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (player.distanceToSqr(other) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private int findSurroundBlockSlot(LocalPlayer player) {
        // First check hotbar (0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSurroundItem(stack)) {
                return i;
            }
        }

        // If not found in hotbar, search main inventory (9..35) and swap to current hotbar slot
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSurroundItem(stack)) {
                int targetHotbarSlot = InventorySwap.INSTANCE.clientSelectedSlot();
                if (targetHotbarSlot < 0 || targetHotbarSlot >= 9) targetHotbarSlot = 0;
                InventorySwap.INSTANCE.swapInventoryToHotbar(i, targetHotbarSlot);
                return targetHotbarSlot;
            }
        }

        return -1;
    }

    private boolean isSurroundItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.OBSIDIAN)
                || stack.is(Items.CRYING_OBSIDIAN)
                || stack.is(Items.ENDER_CHEST)
                || stack.is(Blocks.OBSIDIAN.asItem())
                || stack.is(Blocks.CRYING_OBSIDIAN.asItem())
                || stack.is(Blocks.ENDER_CHEST.asItem());
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private boolean isEntityColliding(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        // Do not self-collide feet
        if (player.getBoundingBox().intersects(box)) {
            return true;
        }

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity.isAlive()) return true;
        }
        return false;
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
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x4000FFCC, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xFF00FFCC, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }
}
