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
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Automatically places blast-resistant blocks above player's head and upper sides
 * to prevent cev breaker, crystal attacks, and anvil/anchor placements.
 */
@ModuleInfo(
        id = "selftrap",
        displayName = "SelfTrap",
        aliases = {"AutoSelfTrap", "TrapSelf"},
        category = ModuleCategory.COMBAT,
        description = "Places blast-resistant blocks above your head and sides to protect against crystal attacks."
)
public class SelfTrap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<SelfTrapMode> mode =
            enumSetting("selftrap_mode", "mode", SelfTrapMode.HEAD, SelfTrapMode.values());
    private final NumberValue<Integer> blocksPerTick =
            num("selftrap_blocks_per_tick", "blocks_per_tick", 2, 1, 4);
    private final BooleanValue rotate =
            bool("selftrap_rotate", "rotate", true);
    private final BooleanValue airPlace =
            bool("selftrap_air_place", "air_place", false);

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
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

        tickSelfTrap(player, level);
    }

    private void tickSelfTrap(LocalPlayer player, Level level) {
        if (!player.isAlive() || player.isSpectator()) return;

        List<BlockPos> requiredPositions = getRequiredTrapPositions(player, level);
        if (requiredPositions.isEmpty()) {
            return;
        }

        // Sort lower Y blocks first so base/side supports are placed before roof blocks
        requiredPositions.sort(Comparator.comparingInt((BlockPos p) -> p.getY())
                .thenComparingDouble(p -> player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;
        Set<BlockPos> placedThisTick = new HashSet<>();
        boolean leased = false;

        try {
            for (BlockPos pos : requiredPositions) {
                if (placedCount >= maxBlocks) break;
                if (!isReplaceable(level, pos, placedThisTick)) continue;

                PlacementTarget target = resolvePlacementTarget(level, player, pos, placedThisTick);
                if (target == null) continue;

                InteractionHand hand;
                if (hasObsidianInOffhand(player)) {
                    hand = InteractionHand.OFF_HAND;
                } else {
                    int slot = findObsidianSlot(player);
                    if (slot == -1) break;

                    if (!InventorySwap.INSTANCE.isHotbarLeasedBy(this)) {
                        leased = InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);
                        if (!leased) break;
                    }
                    hand = InteractionHand.MAIN_HAND;
                }

                if (performPlace(player, target, hand)) {
                    placedCount++;
                    placedThisTick.add(target.pos());

                    // If a helper block was placed, immediately try to place the main block if budget allows
                    if (target.isHelper() && placedCount < maxBlocks) {
                        PlacementTarget nextTarget = resolvePlacementTarget(level, player, pos, placedThisTick);
                        if (nextTarget != null && performPlace(player, nextTarget, hand)) {
                            placedCount++;
                            placedThisTick.add(nextTarget.pos());
                        }
                    }
                }
            }
        } finally {
            if (leased) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private boolean performPlace(LocalPlayer player, PlacementTarget target, InteractionHand hand) {
        if (mc.gameMode == null) return false;

        BlockHitResult hit = target.hit();
        if (rotate.get()) {
            Rotation rot = Rotation.lookingAt(hit.getLocation(), player.getEyePosition()).normalize();
            RotationTarget rotTarget = new RotationTarget(
                    rot,
                    player,
                    List.of(),
                    1,
                    4.0f,
                    true,
                    MovementCorrection.SILENT,
                    null
            );
            RotationManager.INSTANCE.setRotationTarget(rotTarget, 30, this);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        rot.yaw(),
                        rot.pitch(),
                        player.onGround(),
                        player.horizontalCollision
                ));
            }
        }

        InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
        if (result != null && result.consumesAction()) {
            player.swing(hand);
            return true;
        }
        return false;
    }

    private PlacementTarget resolvePlacementTarget(Level level, LocalPlayer player, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (!isReplaceable(level, pos, placedThisTick) || isEntityBlocked(player, level, pos)) {
            return null;
        }

        BlockHitResult directHit = getPlaceHitResult(level, player, pos, airPlace.get(), placedThisTick);
        if (directHit != null) {
            return new PlacementTarget(pos, directHit, false);
        }

        if (airPlace.get()) {
            return null;
        }

        // AirPlace is false: search for adjacent support helper blocks
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sideTop = pos.relative(dir);
            if (isReplaceable(level, sideTop, placedThisTick) && !isEntityBlocked(player, level, sideTop)) {
                BlockHitResult sideTopHit = getPlaceHitResult(level, player, sideTop, false, placedThisTick);
                if (sideTopHit != null) {
                    return new PlacementTarget(sideTop, sideTopHit, true);
                }

                BlockPos sideMid = sideTop.below();
                if (isReplaceable(level, sideMid, placedThisTick) && !isEntityBlocked(player, level, sideMid)) {
                    BlockHitResult sideMidHit = getPlaceHitResult(level, player, sideMid, false, placedThisTick);
                    if (sideMidHit != null) {
                        return new PlacementTarget(sideMid, sideMidHit, true);
                    }

                    BlockPos sideBottom = sideMid.below();
                    if (isReplaceable(level, sideBottom, placedThisTick) && !isEntityBlocked(player, level, sideBottom)) {
                        BlockHitResult sideBottomHit = getPlaceHitResult(level, player, sideBottom, false, placedThisTick);
                        if (sideBottomHit != null) {
                            return new PlacementTarget(sideBottom, sideBottomHit, true);
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockHitResult getPlaceHitResult(Level level, LocalPlayer player, BlockPos pos, boolean allowAir, Set<BlockPos> placedThisTick) {
        Vec3 eyes = player.getEyePosition();
        double bestDist = Double.MAX_VALUE;
        BlockHitResult best = null;
        double range = 5.0;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!isSolid(level, neighbor, placedThisTick)) {
                continue;
            }

            Direction clickFace = dir.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(clickFace.getUnitVec3i()).scale(0.5));
            double distSq = eyes.distanceToSqr(hitVec);
            if (distSq > range * range) continue;

            if (distSq < bestDist) {
                bestDist = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        if (best == null && allowAir) {
            Vec3 hitVec = Vec3.atCenterOf(pos);
            if (eyes.distanceToSqr(hitVec) <= range * range) {
                best = new BlockHitResult(hitVec, Direction.UP, pos, false);
            }
        }

        return best;
    }

    private List<BlockPos> getRequiredTrapPositions(LocalPlayer player, Level level) {
        SelfTrapMode m = mode.get();
        if (m == SelfTrapMode.SMART && !isEnemyThreatPresent(player, level)) {
            return List.of();
        }

        List<BlockPos> basePositions = resolveBasePositions(player);
        List<BlockPos> required = new ArrayList<>();

        for (BlockPos base : basePositions) {
            // Head block at (x, y+2, z)
            BlockPos headPos = base.above(2);
            if (!required.contains(headPos)) {
                required.add(headPos);
            }

            // FULL or SMART (when triggered): head block plus 4 upper side blocks (x±1, y+1, z), (x, y+1, z±1)
            if (m == SelfTrapMode.FULL || m == SelfTrapMode.SMART) {
                BlockPos upper = base.above();
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = upper.relative(dir);
                    if (!required.contains(side)) {
                        required.add(side);
                    }
                }
            }
        }
        return required;
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
                BlockPos pos = new BlockPos(x, y, z);
                if (!positions.contains(pos)) {
                    positions.add(pos);
                }
            }
        }
        return positions.isEmpty() ? List.of(player.blockPosition()) : positions;
    }

    private boolean isEnemyThreatPresent(LocalPlayer player, Level level) {
        for (Player other : level.players()) {
            if (!isEnemyPlayer(other)) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq <= 5.0 * 5.0) {
                return true;
            }

            if (other.getY() > player.getY()) {
                double dx = other.getX() - player.getX();
                double dz = other.getZ() - player.getZ();
                if (dx * dx + dz * dz <= 5.0 * 5.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEnemyPlayer(Player other) {
        if (other == null || other == mc.player || !other.isAlive() || other.isSpectator()) {
            return false;
        }
        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.BEDWARS_SELF;
    }

    private boolean isSolid(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return true;
        }
        if (!level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isReplaceable(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return false;
        }
        if (!level.isInWorldBounds(pos)) return false;
        return level.getBlockState(pos).canBeReplaced();
    }

    private boolean isEntityBlocked(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        if (player.getBoundingBox().intersects(box)) {
            return true;
        }
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity.isAlive()) return true;
        }
        return false;
    }

    private boolean hasObsidianInOffhand(LocalPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(Items.OBSIDIAN) || offhand.is(Items.CRYING_OBSIDIAN);
    }

    private int findObsidianSlot(LocalPlayer player) {
        int selected = InventorySwap.INSTANCE.clientSelectedSlot();
        if (selected >= 0 && selected < 9) {
            ItemStack held = player.getInventory().getItem(selected);
            if (held.is(Items.OBSIDIAN)) {
                return selected;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.OBSIDIAN)) {
                return slot;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.CRYING_OBSIDIAN)) {
                return slot;
            }
        }

        return -1;
    }

    public enum SelfTrapMode {
        HEAD,
        FULL,
        SMART
    }

    private record PlacementTarget(BlockPos pos, BlockHitResult hit, boolean isHelper) {}
}
