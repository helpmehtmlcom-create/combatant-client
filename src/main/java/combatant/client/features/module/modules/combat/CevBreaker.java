/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatically executes Cev-Breaker attacks on trapped/safe enemy players by placing
 * obsidian above their heads, placing an End Crystal, mining the obsidian, and detonating
 * the crystal downwards for high explosive damage.
 */
@ModuleInfo(
        id = "cevbreaker",
        displayName = "CevBreaker",
        category = ModuleCategory.COMBAT,
        aliases = {"autocev", "cev"},
        description = "Executes Cev-Breaker attacks on enemies in holes or safe positions."
)
public class CevBreaker extends Module {

    public enum Stage {
        PLACE_OBSIDIAN,
        PLACE_CRYSTAL,
        MINE_OBSIDIAN,
        DETONATE
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("range", "range", 4.5, 2.0, 6.0);

    private final BooleanValue rotate =
            bool("rotate", "rotate", true);

    private final NumberValue<Integer> breakDelay =
            num("break_delay", "breakDelay", 0, 0, 5);

    private final BooleanValue antiSuicide =
            bool("anti_suicide", "antiSuicide", true);

    private Stage stage = Stage.PLACE_OBSIDIAN;
    private Player currentTarget = null;
    private BlockPos targetCeiling = null;
    private float miningProgress = 0.0f;
    private boolean isMining = false;
    private int breakDelayTimer = 0;

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        if (isMining && targetCeiling != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    targetCeiling,
                    Direction.UP
            ));
        }
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        stage = Stage.PLACE_OBSIDIAN;
        currentTarget = null;
        targetCeiling = null;
        miningProgress = 0.0f;
        isMining = false;
        breakDelayTimer = 0;
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            reset();
            return;
        }

        if (breakDelayTimer > 0) {
            breakDelayTimer--;
        }

        // Validate or find target
        if (currentTarget == null || !isValidTarget(player, currentTarget, range.get()) || !isInHoleOrSafe(level, currentTarget)) {
            currentTarget = findTarget(player, level, range.get());
            if (currentTarget == null) {
                reset();
                return;
            }
            stage = Stage.PLACE_OBSIDIAN;
            targetCeiling = currentTarget.blockPosition().above(2);
            miningProgress = 0.0f;
            isMining = false;
        } else {
            targetCeiling = currentTarget.blockPosition().above(2);
        }

        if (targetCeiling == null) return;

        // Reach check to ceiling
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(targetCeiling)) > range.get() + 1.5) {
            reset();
            return;
        }

        executeStateMachine(player, level);
    }

    private void executeStateMachine(LocalPlayer player, Level level) {
        switch (stage) {
            case PLACE_OBSIDIAN -> handlePlaceObsidian(player, level);
            case PLACE_CRYSTAL -> handlePlaceCrystal(player, level);
            case MINE_OBSIDIAN -> handleMineObsidian(player, level);
            case DETONATE -> handleDetonate(player, level);
        }
    }

    private void handlePlaceObsidian(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(targetCeiling);

        // If ceiling already has obsidian, move to PLACE_CRYSTAL
        if (isObsidian(ceilingState)) {
            stage = Stage.PLACE_CRYSTAL;
            handlePlaceCrystal(player, level);
            return;
        }

        // If ceiling is bedrock, cev breaker is impossible at this location
        if (ceilingState.is(Blocks.BEDROCK)) {
            currentTarget = null;
            return;
        }

        // Place obsidian at targetCeiling
        if (isReplaceable(level, targetCeiling)) {
            PlacementTarget target = resolvePlacementTarget(level, player, targetCeiling);
            if (target != null) {
                if (placeObsidian(player, target)) {
                    if (!target.isHelper) {
                        stage = Stage.PLACE_CRYSTAL;
                    }
                }
            }
        }
    }

    private void handlePlaceCrystal(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(targetCeiling);
        if (!isObsidian(ceilingState) && !ceilingState.is(Blocks.BEDROCK)) {
            stage = Stage.PLACE_OBSIDIAN;
            return;
        }

        BlockPos crystalBase = targetCeiling;
        BlockPos crystalAir = targetCeiling.above();

        // Check if crystal already placed
        EndCrystal existingCrystal = findExistingCrystal(level, crystalAir);
        if (existingCrystal != null) {
            stage = Stage.MINE_OBSIDIAN;
            handleMineObsidian(player, level);
            return;
        }

        if (!level.isInWorldBounds(crystalAir)) return;

        // Ensure crystal location is not entity-blocked (other than crystals)
        if (isEntityBlocked(level, crystalAir)) {
            return;
        }

        Vec3 clickVec = new Vec3(crystalBase.getX() + 0.5, crystalBase.getY() + 1.0, crystalBase.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(clickVec, Direction.UP, crystalBase, false);

        if (placeCrystal(player, hit)) {
            stage = Stage.MINE_OBSIDIAN;
        }
    }

    private void handleMineObsidian(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(targetCeiling);

        // If obsidian is already broken, transition to DETONATE immediately
        if (ceilingState.isAir() || !isObsidian(ceilingState)) {
            finishMining();
            stage = Stage.DETONATE;
            handleDetonate(player, level);
            return;
        }

        // Verify crystal is still alive above ceiling
        BlockPos crystalAir = targetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);
        if (crystal == null) {
            // Crystal disappeared or got destroyed; replug crystal
            stage = Stage.PLACE_CRYSTAL;
            return;
        }

        mineObsidian(player, level, targetCeiling, ceilingState);
    }

    private void handleDetonate(LocalPlayer player, Level level) {
        BlockPos crystalAir = targetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);

        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            // Done with this cycle, restart loop
            stage = Stage.PLACE_OBSIDIAN;
            breakDelayTimer = 0;
            return;
        }

        if (breakDelayTimer > 0) {
            return;
        }

        // Anti-suicide check
        if (antiSuicide.get()) {
            Vec3 explosionPos = crystal.position();
            float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, explosionPos, 0, false);
            if (!ExplosionDamageRules.isSafe(player, selfDamage, false)) {
                return;
            }
        }

        attackCrystal(player, crystal);
        breakDelayTimer = breakDelay.get();
        stage = Stage.PLACE_OBSIDIAN;
    }

    private void mineObsidian(LocalPlayer player, Level level, BlockPos pos, BlockState state) {
        int bestToolSlot = findBestPickaxe(player, state);

        if (!isMining) {
            isMining = true;
            miningProgress = 0.0f;

            if (rotate.get()) {
                applyRotation(player, Vec3.atCenterOf(pos));
            }

            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        pos,
                        Direction.UP
                ));
            }
            player.swing(InteractionHand.MAIN_HAND);
        }

        // Lease pickaxe to calculate destroy progress and ensure active tool efficiency
        boolean leased = false;
        if (bestToolSlot >= 0 && !InventorySwap.INSTANCE.isHotbarLeasedBy(this)) {
            leased = InventorySwap.INSTANCE.leaseHotbar(this, bestToolSlot, 2);
        }

        try {
            float delta = state.getDestroyProgress(player, level, pos);
            miningProgress += delta;

            if (rotate.get()) {
                applyRotation(player, Vec3.atCenterOf(pos));
            }

            if (miningProgress >= 1.0f) {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            pos,
                            Direction.UP
                    ));
                }
                player.swing(InteractionHand.MAIN_HAND);
                finishMining();
                stage = Stage.DETONATE;
                handleDetonate(player, level);
            }
        } finally {
            if (leased) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private void finishMining() {
        isMining = false;
        miningProgress = 0.0f;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void attackCrystal(LocalPlayer player, EndCrystal crystal) {
        if (rotate.get()) {
            applyRotation(player, crystal.getBoundingBox().getCenter());
        }

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundInteractPacket(
                    crystal.getId(),
                    null,
                    null,
                    player.isShiftKeyDown()
            ));
        } else if (mc.gameMode != null) {
            mc.gameMode.attack(player, crystal);
        }

        player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean placeObsidian(LocalPlayer player, PlacementTarget target) {
        InteractionHand hand = isObsidian(player.getOffhandItem()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        int slot = hand == InteractionHand.MAIN_HAND ? findObsidianHotbarSlot(player) : -1;
        if (hand == InteractionHand.MAIN_HAND && slot == -1) return false;

        return BlockPlacer.placeBlock(
                this,
                target.hit,
                hand,
                slot,
                rotate.get(),
                BlockPlacer.SwingMode.SERVER
        );
    }

    private boolean placeCrystal(LocalPlayer player, BlockHitResult hit) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        boolean leased = false;

        if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            int slot = findCrystalHotbarSlot(player);
            if (slot == -1) return false;

            leased = InventorySwap.INSTANCE.leaseHotbar(this, slot, 2);
            if (!leased) return false;
        }

        try {
            if (rotate.get()) {
                applyRotation(player, hit.getLocation());
            }

            InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
            if (result != null && result.consumesAction()) {
                player.swing(hand);
                return true;
            }
            return false;
        } finally {
            if (leased) {
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private void applyRotation(LocalPlayer player, Vec3 targetVec) {
        Rotation rot = Rotation.lookingAt(targetVec, player.getEyePosition()).normalize();
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

    private EndCrystal findExistingCrystal(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.5, 1.5, 1.5);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive);
        EndCrystal closest = null;
        double bestDistSq = Double.MAX_VALUE;

        Vec3 center = Vec3.atCenterOf(pos);
        for (EndCrystal c : crystals) {
            double d = c.distanceToSqr(center);
            if (d < bestDistSq) {
                bestDistSq = d;
                closest = c;
            }
        }
        return closest;
    }

    private PlacementTarget resolvePlacementTarget(Level level, LocalPlayer player, BlockPos pos) {
        if (!isReplaceable(level, pos) || isEntityBlocked(level, pos)) {
            return null;
        }

        BlockHitResult directHit = getPlaceHitResult(level, player, pos);
        if (directHit != null) {
            return new PlacementTarget(pos, directHit, false);
        }

        // Helper check: below position
        BlockPos below = pos.below();
        if (isReplaceable(level, below) && !isEntityBlocked(level, below)) {
            BlockHitResult belowHit = getPlaceHitResult(level, player, below);
            if (belowHit != null) {
                return new PlacementTarget(below, belowHit, true);
            }
        }

        // Helper check: adjacent horizontal columns
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (isReplaceable(level, side) && !isEntityBlocked(level, side)) {
                BlockHitResult sideHit = getPlaceHitResult(level, player, side);
                if (sideHit != null) {
                    return new PlacementTarget(side, sideHit, true);
                }
            }
        }

        return null;
    }

    private BlockHitResult getPlaceHitResult(Level level, LocalPlayer player, BlockPos pos) {
        Vec3 eyes = player.getEyePosition();
        double bestDist = Double.MAX_VALUE;
        BlockHitResult best = null;
        double reachSq = (range.get() + 1.5) * (range.get() + 1.5);

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
            if (distSq > reachSq) continue;

            BlockHitResult ray = level.clip(new ClipContext(
                    eyes,
                    hitVec,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (ray != null && ray.getType() == HitResult.Type.BLOCK && !neighbor.equals(ray.getBlockPos())) {
                continue;
            }

            if (distSq < bestDist) {
                bestDist = distSq;
                best = new BlockHitResult(hitVec, clickFace, neighbor, false);
            }
        }

        return best;
    }

    private Player findTarget(LocalPlayer player, Level level, double maxRange) {
        Player closest = null;
        double closestDistSq = maxRange * maxRange;

        for (Player other : level.players()) {
            if (!isValidTarget(player, other, maxRange)) continue;
            if (!isInHoleOrSafe(level, other)) continue;

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

    private boolean isInHoleOrSafe(Level level, Player player) {
        BlockPos feet = player.blockPosition();
        int safeSides = 0;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (isSafeBlock(level.getBlockState(side))) {
                safeSides++;
            }
        }

        if (safeSides >= 3) return true;

        // Check head level surround
        BlockPos head = feet.above();
        int headSafeSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = head.relative(dir);
            if (isSafeBlock(level.getBlockState(side))) {
                headSafeSides++;
            }
        }

        return headSafeSides >= 3;
    }

    private boolean isSafeBlock(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        return b == Blocks.BEDROCK
                || b == Blocks.OBSIDIAN
                || b == Blocks.CRYING_OBSIDIAN
                || b == Blocks.RESPAWN_ANCHOR
                || b == Blocks.ENDER_CHEST
                || b == Blocks.ANVIL
                || b == Blocks.CHIPPED_ANVIL
                || b == Blocks.DAMAGED_ANVIL
                || b.getExplosionResistance() >= 600.0f;
    }

    private boolean isObsidian(BlockState state) {
        return state != null && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN));
    }

    private boolean isObsidian(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN));
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

    private int findCrystalHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && stack.is(Items.END_CRYSTAL)) {
                return i;
            }
        }
        return -1;
    }

    private int findBestPickaxe(LocalPlayer player, BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.is(Items.NETHERITE_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.IRON_PICKAXE)) {
                float s = stack.getDestroySpeed(state);
                if (s > bestSpeed) {
                    bestSpeed = s;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
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

    public Stage getStage() {
        return stage;
    }

    public Player getCurrentTarget() {
        return currentTarget;
    }

    public BlockPos getTargetCeiling() {
        return targetCeiling;
    }

    public float getMiningProgress() {
        return miningProgress;
    }

    private record PlacementTarget(BlockPos pos, BlockHitResult hit, boolean isHelper) {}
}
