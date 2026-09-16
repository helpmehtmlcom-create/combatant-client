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
import combatant.client.util.block.placer.BlockPlacer;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

/**
 * Automatically places cobwebs at nearby enemies' feet and/or head to severely restrict their movement.
 */
@ModuleInfo(
        id = "autoweb",
        displayName = "AutoWeb",
        category = ModuleCategory.COMBAT,
        aliases = {"webber", "webauratrap"},
        description = "Traps nearby enemies in cobwebs to restrict their movement and attacks."
)
public class AutoWeb extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("range", "range", 4.5, 2.0, 6.0);
    private final EnumValue<WebMode> mode =
            enumSetting("mode", "mode", WebMode.FEET, WebMode.values());
    private final NumberValue<Integer> blocksPerTick =
            num("blocks_per_tick", "blocks_per_tick", 2, 1, 4);
    private final BooleanValue rotate =
            bool("rotate", "rotate", true);
    private final BooleanValue render =
            bool("render", "render", true);

    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();
    private Player currentTarget;

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        currentTarget = null;
        renderBlocks.clear();
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }

        tickAutoWeb(player, level);
    }

    private void tickAutoWeb(LocalPlayer player, Level level) {
        if (!player.isAlive() || player.isSpectator()) return;

        Player target = findTarget(player, level, range.get());
        currentTarget = target;
        if (target == null) {
            return;
        }

        List<BlockPos> targetPositions = getTargetPositions(target);
        if (targetPositions.isEmpty()) {
            return;
        }

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;
        Set<BlockPos> placedThisTick = new HashSet<>();
        boolean leased = false;
        int leasedSlot = -1;

        try {
            for (BlockPos pos : targetPositions) {
                if (placedCount >= maxBlocks) break;
                if (!isReplaceable(level, pos, placedThisTick)) continue;
                if (isSelfIntersecting(player, pos)) continue;

                BlockHitResult hit = getPlaceHitResult(level, player, pos, placedThisTick);
                if (hit == null) continue;

                InteractionHand hand;
                if (hasCobwebInOffhand(player)) {
                    hand = InteractionHand.OFF_HAND;
                } else {
                    int slot = findCobwebSlot(player);
                    if (slot == -1) break;

                    if (leasedSlot != slot) {
                        if (leased) {
                            InventorySwap.INSTANCE.releaseHotbar(this);
                        }
                        leased = InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);
                        if (!leased) break;
                        leasedSlot = slot;
                    }
                    hand = InteractionHand.MAIN_HAND;
                }

                if (performPlace(player, hit, hand)) {
                    placedCount++;
                    placedThisTick.add(pos);
                    renderBlocks.put(pos, System.currentTimeMillis());
                }
            }
        } finally {
            if (leased) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private List<BlockPos> getTargetPositions(Player target) {
        BlockPos feet = target.blockPosition();
        BlockPos head = feet.above();
        WebMode webMode = mode.get();

        return switch (webMode) {
            case FEET -> List.of(feet);
            case HEAD -> List.of(head);
            case BOTH -> List.of(feet, head);
        };
    }

    private BlockHitResult getPlaceHitResult(Level level, LocalPlayer player, BlockPos pos, Set<BlockPos> placedThisTick) {
        Vec3 eyes = player.getEyePosition();
        double bestDistSq = Double.MAX_VALUE;
        BlockHitResult best = null;
        double maxReach = range.get() + 1.5;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!isSolid(level, neighbor, placedThisTick)) {
                continue;
            }

            Direction clickFace = dir.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(clickFace.getUnitVec3i()).scale(0.5));
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > maxReach * maxReach) continue;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private boolean isSolid(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return true;
        }
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private boolean isReplaceable(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return false;
        }
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && !state.is(Blocks.COBWEB);
    }

    private boolean isSelfIntersecting(LocalPlayer player, BlockPos pos) {
        if (player == null) return false;
        return player.getBoundingBox().intersects(new AABB(pos));
    }

    private boolean performPlace(LocalPlayer player, BlockHitResult hit, InteractionHand hand) {
        if (hit == null) return false;
        return BlockPlacer.placeBlock(
                this,
                hit,
                hand,
                player.getInventory().getSelectedSlot(),
                rotate.get(),
                BlockPlacer.SwingMode.SERVER
        );
    }

    private Player findTarget(LocalPlayer player, Level level, double maxRange) {
        LivingEntity current = TargetManager.getTarget();
        if (current instanceof Player p && isValidTarget(player, p, maxRange)) {
            return p;
        }

        Player closest = null;
        double closestDistSq = maxRange * maxRange;

        for (Player other : level.players()) {
            if (!isValidTarget(player, other, maxRange)) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = other;
            }
        }

        return closest;
    }

    private boolean isValidTarget(LocalPlayer self, Player other, double maxRange) {
        if (other == null || other == self) return false;
        if (!other.isAlive() || other.isSpectator()) return false;
        if (self.distanceTo(other) > maxRange) return false;

        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.BEDWARS_SELF;
    }

    private boolean hasCobwebInOffhand(LocalPlayer player) {
        return player.getOffhandItem().is(Items.COBWEB);
    }

    private int findCobwebSlot(LocalPlayer player) {
        int selected = InventorySwap.INSTANCE.clientSelectedSlot();
        if (selected >= 0 && selected < 9) {
            ItemStack held = player.getInventory().getItem(selected);
            if (held.is(Items.COBWEB)) {
                return selected;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.COBWEB)) {
                return slot;
            }
        }

        return -1;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!render.get() || renderBlocks.isEmpty()) return;

        long now = System.currentTimeMillis();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = 1.0f - (float) (now - entry.getValue()) / 1000.0f;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);

            AABB box = new AABB(pos);
            int fillArgb = ExplosionRenderUtil.applyOpacity(0x40FFFFFF, alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(0xFFFFFFFF, alpha);

            ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    public enum WebMode {
        FEET,
        HEAD,
        BOTH
    }
}
