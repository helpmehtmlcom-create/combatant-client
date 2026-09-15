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
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically detects and destroys enemy shulker boxes and ender chests in range
 * to prevent opponent regearing during combat.
 */
@ModuleInfo(
        id = "antiregear",
        displayName = "AntiRegear",
        category = ModuleCategory.COMBAT,
        aliases = {"shulkerbreaker"},
        description = "Automatically destroys nearby enemy shulker boxes and ender chests."
)
public class AntiRegear extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    // Settings
    private final NumberValue<Double> range =
            num("range", "range", 4.5, 1.0, 6.0);
    private final BooleanValue shulkers =
            bool("shulkers", "shulkers", true);
    private final BooleanValue enderChests =
            bool("ender_chests", "enderChests", true);
    private final BooleanValue autoTool =
            bool("auto_tool", "autoTool", true);
    private final BooleanValue render =
            bool("render", "render", true);

    // Tracking state
    private final Map<BlockPos, Long> recentEnemyPlacements = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> brokenBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        recentEnemyPlacements.clear();
        brokenBlocks.clear();
        renderBlocks.clear();
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        recentEnemyPlacements.clear();
        brokenBlocks.clear();
        renderBlocks.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.level == null || mc.player == null) return;
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet) {
            BlockState state = packet.getBlockState();
            boolean isShulker = state.getBlock() instanceof ShulkerBoxBlock;
            boolean isEnderChest = state.getBlock() instanceof EnderChestBlock;

            if ((isShulker && shulkers.get()) || (isEnderChest && enderChests.get())) {
                BlockPos pos = packet.getPos();
                Vec3 posVec = Vec3.atCenterOf(pos);

                for (Player other : mc.level.players()) {
                    if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
                    if (CategoryService.isFriend(other)) continue;

                    if (other.position().distanceToSqr(posVec) <= 36.0) { // Placed within 6 blocks of an enemy
                        recentEnemyPlacements.put(pos.immutable(), System.currentTimeMillis());
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        long now = System.currentTimeMillis();

        // Prune expired tracked placements and recent attempts
        recentEnemyPlacements.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 5000L) return true;
            BlockState s = level.getBlockState(entry.getKey());
            return s.isAir();
        });
        brokenBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);

        List<Candidate> candidates = findCandidates(player, level);
        if (candidates.isEmpty()) return;

        // Prioritize:
        // 1. Explicitly placed recently by enemies
        // 2. Most recent placement time
        // 3. Proximity to enemies
        // 4. Proximity to local player
        candidates.sort((a, b) -> {
            if (a.recentEnemyPlacement != b.recentEnemyPlacement) {
                return a.recentEnemyPlacement ? -1 : 1;
            }
            if (a.recentEnemyPlacement && a.placementTime != b.placementTime) {
                return Long.compare(b.placementTime, a.placementTime);
            }
            int enemyDistCmp = Double.compare(a.distToEnemySq, b.distToEnemySq);
            if (enemyDistCmp != 0) {
                return enemyDistCmp;
            }
            return Double.compare(a.distToPlayerSq, b.distToPlayerSq);
        });

        Candidate target = candidates.get(0);
        breakBlock(target);
    }

    private void breakBlock(Candidate candidate) {
        if (mc.getConnection() == null || mc.player == null) return;
        BlockPos targetPos = candidate.pos;
        BlockState targetState = candidate.state;

        int toolSlot = -1;
        if (autoTool.get()) {
            toolSlot = findBestPickaxe(targetState);
            if (toolSlot >= 0) {
                InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
            }
        }

        try {
            // Dispatches start and stop destroy packets
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    targetPos,
                    Direction.UP
            ));

            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    targetPos,
                    Direction.UP
            ));

            if (mc.gameMode != null) {
                mc.gameMode.destroyBlock(targetPos);
            }

            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            if (toolSlot >= 0) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }

        long now = System.currentTimeMillis();
        brokenBlocks.put(targetPos, now);
        renderBlocks.put(targetPos, now);
        recentEnemyPlacements.remove(targetPos);
    }

    private int findBestPickaxe(BlockState state) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.is(ItemTags.PICKAXES)) continue;

            float speed = state != null ? stack.getDestroySpeed(state) : 1.0f;
            if (speed > bestSpeed || bestSlot == -1) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private List<Candidate> findCandidates(LocalPlayer player, Level level) {
        double r = range.get();
        double rSq = r * r;
        Vec3 eyePos = player.getEyePosition();

        int minX = Mth.floor(player.getX() - r);
        int maxX = Mth.floor(player.getX() + r);
        int minY = Mth.floor(player.getY() - r);
        int maxY = Mth.floor(player.getY() + r);
        int minZ = Mth.floor(player.getZ() - r);
        int maxZ = Mth.floor(player.getZ() + r);

        long now = System.currentTimeMillis();
        List<Candidate> candidates = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    double distToPlayerSq = eyePos.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distToPlayerSq > rSq) continue;

                    Long lastAttempt = brokenBlocks.get(pos);
                    if (lastAttempt != null && now - lastAttempt < 250L) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    boolean isShulker = state.getBlock() instanceof ShulkerBoxBlock;
                    boolean isEnderChest = state.getBlock() instanceof EnderChestBlock;

                    if (!((isShulker && shulkers.get()) || (isEnderChest && enderChests.get()))) {
                        continue;
                    }

                    boolean recentEnemy = recentEnemyPlacements.containsKey(pos);
                    long placedTime = recentEnemyPlacements.getOrDefault(pos, 0L);
                    double distToEnemySq = getMinDistanceToEnemySq(level, player, pos);

                    // Also consider near enemy within 6 blocks as enemy placement
                    if (!recentEnemy && distToEnemySq <= 36.0) {
                        recentEnemy = true;
                        placedTime = now;
                    }

                    candidates.add(new Candidate(pos, state, recentEnemy, placedTime, distToEnemySq, distToPlayerSq));
                }
            }
        }
        return candidates;
    }

    private double getMinDistanceToEnemySq(Level level, LocalPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        double minDistSq = Double.MAX_VALUE;

        // Check active TargetManager target
        LivingEntity managed = TargetManager.getTarget();
        if (managed != null && managed != player && managed.isAlive() && !managed.isSpectator()) {
            minDistSq = Math.min(minDistSq, managed.position().distanceToSqr(center));
        }

        // Check non-friendly players
        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (CategoryService.isFriend(other)) continue;

            minDistSq = Math.min(minDistSq, other.position().distanceToSqr(center));
        }

        return minDistSq;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        long now = System.currentTimeMillis();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = 1.0f - (float) (now - entry.getValue()) / 1000.0f;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);

            AABB box = new AABB(pos);
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x40FF3333, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xFFFF3333, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    private record Candidate(
            BlockPos pos,
            BlockState state,
            boolean recentEnemyPlacement,
            long placementTime,
            double distToEnemySq,
            double distToPlayerSq
    ) {}
}
