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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically traps nearby enemy players in blast-resistant obsidian cages,
 * placing bottom support, side walls, and roof head traps to prevent movement and counterattacks.
 */
@ModuleInfo(
        id = "autotrap",
        displayName = "AutoTrap",
        category = ModuleCategory.COMBAT,
        aliases = {"trap", "cage"},
        description = "Traps nearby enemies in obsidian cages to prevent movement and attacks."
)
public class AutoTrap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("range", "range", 4.5, 2.0, 6.0);
    private final EnumValue<AutoTrapMode> mode =
            enumSetting("mode", "mode", AutoTrapMode.FULL, AutoTrapMode.values());
    private final NumberValue<Integer> blocksPerTick =
            num("blocks_per_tick", "blocks_per_tick", 4, 1, 6);
    private final BooleanValue rotate =
            bool("rotate", "rotate", true);
    private final BooleanValue antiStep =
            bool("anti_step", "anti_step", true);
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

        tickAutoTrap(player, level);
    }

    private void tickAutoTrap(LocalPlayer player, Level level) {
        if (!player.isAlive() || player.isSpectator()) return;

        Player target = findTarget(player, level, range.get());
        currentTarget = target;
        if (target == null) {
            return;
        }

        List<BlockPos> basePositions = resolveBasePositions(target);
        List<BlockPos> requiredPositions = getRequiredTrapPositions(target, basePositions, level);
        if (requiredPositions.isEmpty()) {
            return;
        }

        // Filter out already placed or solid blocks
        requiredPositions.removeIf(pos -> isSolid(level, pos, null) || !isReplaceable(level, pos, null));
        if (requiredPositions.isEmpty()) {
            return;
        }

        // Sort placement order: bottom support -> side walls -> top head trap
        requiredPositions.sort(Comparator.comparingInt((BlockPos p) -> getPlacementPriority(p, basePositions))
                .thenComparingDouble(p -> player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;
        Set<BlockPos> placedThisTick = new HashSet<>();
        boolean leased = false;

        try {
            for (BlockPos pos : requiredPositions) {
                if (placedCount >= maxBlocks) break;
                if (!isReplaceable(level, pos, placedThisTick) || isEntityBlocked(level, pos)) continue;

                PlacementTarget placeTarget = resolvePlacementTarget(level, player, pos, placedThisTick);
                if (placeTarget == null) continue;

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

                if (performPlace(player, placeTarget, hand)) {
                    placedCount++;
                    placedThisTick.add(placeTarget.pos());
                    renderBlocks.put(placeTarget.pos(), System.currentTimeMillis());

                    // If a helper block was placed, immediately try to place the main block if budget allows
                    if (placeTarget.isHelper() && placedCount < maxBlocks) {
                        PlacementTarget nextTarget = resolvePlacementTarget(level, player, pos, placedThisTick);
                        if (nextTarget != null && performPlace(player, nextTarget, hand)) {
                            placedCount++;
                            placedThisTick.add(nextTarget.pos());
                            renderBlocks.put(nextTarget.pos(), System.currentTimeMillis());
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

    private int getPlacementPriority(BlockPos pos, List<BlockPos> basePositions) {
        for (BlockPos base : basePositions) {
            if (pos.equals(base.above(2))) {
                return 4; // Top head trap
            }
            if (pos.getY() == base.getY() + 2) {
                return 3; // Roof side helper
            }
            if (pos.getY() == base.getY() + 1) {
                return 2; // Upper side walls (body level)
            }
            if (pos.getY() == base.getY()) {
                return 1; // Lower side walls (feet level)
            }
            if (pos.getY() < base.getY()) {
                return 0; // Bottom support
            }
        }
        return 1;
    }

    private List<BlockPos> getRequiredTrapPositions(Player target, List<BlockPos> basePositions, Level level) {
        AutoTrapMode m = mode.get();
        List<BlockPos> required = new ArrayList<>();

        for (BlockPos base : basePositions) {
            // Head/top trap at y+2
            BlockPos headPos = base.above(2);
            addUnique(required, headPos);

            if (m == AutoTrapMode.HEAD_ONLY) {
                // In HEAD_ONLY mode, only head block is strictly requested.
                // Helper blocks will be dynamically resolved if no adjacent solid blocks exist.
                continue;
            }

            // Side walls around feet (y) and body (y+1)
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos feetSide = base.relative(dir);
                BlockPos bodySide = base.above().relative(dir);
                BlockPos roofSide = base.above(2).relative(dir);

                // Bottom support if ground under feet side is air
                BlockPos underFeet = feetSide.below();
                if (isReplaceable(level, underFeet, null)) {
                    addUnique(required, underFeet);
                }

                if (m == AutoTrapMode.FULL) {
                    addUnique(required, feetSide);
                    if (antiStep.get()) {
                        addUnique(required, bodySide);
                    }
                    addUnique(required, roofSide);
                } else if (m == AutoTrapMode.SMART) {
                    // SMART mode: prioritize open escape routes
                    boolean feetOpen = isReplaceable(level, feetSide, null);
                    boolean bodyOpen = isReplaceable(level, bodySide, null);

                    if (feetOpen) {
                        addUnique(required, feetSide);
                    }
                    if (antiStep.get() || bodyOpen) {
                        addUnique(required, bodySide);
                    }
                    addUnique(required, roofSide);
                }
            }
        }

        return required;
    }

    private void addUnique(List<BlockPos> list, BlockPos pos) {
        if (!list.contains(pos)) {
            list.add(pos);
        }
    }

    private PlacementTarget resolvePlacementTarget(Level level, LocalPlayer player, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (!isReplaceable(level, pos, placedThisTick) || isEntityBlocked(level, pos)) {
            return null;
        }

        BlockHitResult directHit = getPlaceHitResult(level, player, pos, placedThisTick);
        if (directHit != null) {
            return new PlacementTarget(pos, directHit, false);
        }

        // Helper check: below position (for ledges / floating side blocks)
        BlockPos below = pos.below();
        if (isReplaceable(level, below, placedThisTick) && !isEntityBlocked(level, below)) {
            BlockHitResult belowHit = getPlaceHitResult(level, player, below, placedThisTick);
            if (belowHit != null) {
                return new PlacementTarget(below, belowHit, true);
            }
        }

        // Helper check: adjacent horizontal columns (roof helpers, side pillars)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sideTop = pos.relative(dir);
            if (isReplaceable(level, sideTop, placedThisTick) && !isEntityBlocked(level, sideTop)) {
                BlockHitResult sideTopHit = getPlaceHitResult(level, player, sideTop, placedThisTick);
                if (sideTopHit != null) {
                    return new PlacementTarget(sideTop, sideTopHit, true);
                }

                BlockPos sideMid = sideTop.below();
                if (isReplaceable(level, sideMid, placedThisTick) && !isEntityBlocked(level, sideMid)) {
                    BlockHitResult sideMidHit = getPlaceHitResult(level, player, sideMid, placedThisTick);
                    if (sideMidHit != null) {
                        return new PlacementTarget(sideMid, sideMidHit, true);
                    }

                    BlockPos sideBottom = sideMid.below();
                    if (isReplaceable(level, sideBottom, placedThisTick) && !isEntityBlocked(level, sideBottom)) {
                        BlockHitResult sideBottomHit = getPlaceHitResult(level, player, sideBottom, placedThisTick);
                        if (sideBottomHit != null) {
                            return new PlacementTarget(sideBottom, sideBottomHit, true);
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockHitResult getPlaceHitResult(Level level, LocalPlayer player, BlockPos pos, Set<BlockPos> placedThisTick) {
        Vec3 eyes = player.getEyePosition();
        double bestDist = Double.MAX_VALUE;
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

            if (distSq < bestDist) {
                bestDist = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private boolean performPlace(LocalPlayer player, PlacementTarget target, InteractionHand hand) {
        BlockHitResult hit = target.hit();
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

    private List<BlockPos> resolveBasePositions(Player target) {
        AABB box = target.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        int y = Mth.floor(target.getY() + 0.1);

        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!positions.contains(pos)) {
                    positions.add(pos);
                }
            }
        }
        return positions.isEmpty() ? List.of(target.blockPosition()) : positions;
    }

    private boolean isSolid(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return true;
        }
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isReplaceable(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return false;
        }
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        return level.getBlockState(pos).canBeReplaced();
    }

    private boolean isEntityBlocked(Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        if (mc.player != null && mc.player.getBoundingBox().intersects(box)) {
            return true;
        }
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity.isAlive() && !entity.isSpectator()) {
                if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
                    return true;
                }
            }
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

    public enum AutoTrapMode {
        FULL,
        HEAD_ONLY,
        SMART
    }

    private record PlacementTarget(BlockPos pos, BlockHitResult hit, boolean isHelper) {}
}
