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

import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance defensive surround system and offensive hole-punisher.
 * Automatically surrounds player feet with blast-resistant blocks (Obsidian, Crying Obsidian, Ender Chests),
 * reinforces against City attacks, clears obstructing crystals, and integrates CevBreaker attacks.
 */
@ModuleInfo(
        id = "surround",
        displayName = "Surround",
        category = ModuleCategory.COMBAT,
        aliases = {"feetplace", "feettrap", "autoobsidian", "autocage", "feetarmor", "cevbreaker", "autocev", "cev"},
        description = "Surrounds player feet with blast-resistant blocks, with anti-city reinforcement, crystal clearing, and integrated CevBreaker offensive attacks."
)
public class Surround extends Module {

    private static final int ROTATION_PRIORITY = 32;
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final Minecraft mc = Minecraft.getInstance();

    // Surround Modes & Patterns
    private final EnumValue<SurroundMode> surroundMode =
            enumSetting("surround_mode", "mode", SurroundMode.NORMAL, SurroundMode.values());
    private final BooleanValue floor =
            bool("floor", "floor", true);
    private final BooleanValue doubleHeight =
            bool("double_height", "double_height", false);
    private final BooleanValue helpingBlocks =
            bool("helping_blocks", "helping_blocks", true);
    private final BooleanValue antiCity =
            bool("anti_city", "anti_city", true);
    private final BooleanValue antiFaceplace =
            bool("anti_faceplace", "anti_faceplace", false);

    // Centering & Auto-Disable
    private final EnumValue<CenterMode> centerMode =
            enumMode("center_mode", CenterMode.INSTANT, CenterMode.values());
    private final BooleanValue onlyOnGround =
            bool("only_on_ground", "only_on_ground", true);
    private final BooleanValue disableOnJump =
            bool("disable_on_jump", "disable_on_jump", true);
    private final BooleanValue disableOnLeave =
            bool("disable_on_leave", "disable_on_leave", true);
    private final BooleanValue disableOnYChange =
            bool("disable_on_y_change", "disable_on_y_change", false);

    // Placement speed & constraints
    private final NumberValue<Integer> blocksPerTick =
            num("blocks_per_tick", "blocks_per_tick", 4, 1, 8);
    private final NumberValue<Integer> delayTicks =
            num("delay_ticks", "delay_ticks", 0, 0, 10);
    private final NumberValue<Float> placeRange =
            num("place_range", "range", 5.0f, 1.0f, 6.0f);
    private final NumberValue<Float> wallRange =
            num("wall_range", "wall_range", 3.5f, 0.0f, 6.0f);
    private final BooleanValue airPlace =
            bool("air_place", "air_place", false);
    private final BooleanValue clearCrystals =
            bool("clear_crystals", "clear_crystals", true);

    // Item & Swapping
    private final EnumValue<BlockFilter> blockFilter =
            enumMode("block_filter", BlockFilter.PREFER_OBSIDIAN, BlockFilter.values());
    private final EnumValue<InventorySearchScope> swapScope =
            enumSetting("swap_scope", "swap_scope", InventorySearchScope.FULL, InventorySearchScope.values());
    private final EnumValue<InventorySwapVisibility> swapVisibility =
            enumSetting("swap_visibility", "swap_visibility", InventorySwapVisibility.SILENT, InventorySwapVisibility.values());
    private final BooleanValue restoreItem =
            bool("restore_item", "restore_item", true);

    // Rotation
    private final EnumValue<RotationMode> rotationMode =
            enumSetting("rotation_mode", "rotation_mode", RotationMode.SILENT, RotationMode.values());

    // Visuals
    private final BooleanValue renderEnabled =
            bool("render", "render", true);
    private final EnumValue<RenderMode> renderMode =
            enumMode("render_mode", RenderMode.BOTH, RenderMode.values());
    private final RGBAColorValue fillColor =
            color("fill_color", "#4D800080");
    private final RGBAColorValue lineColor =
            color("line_color", "#FF8000FF");
    private final NumberValue<Float> lineWidth =
            num("line_width", "line_width", 1.5f, 0.5f, 5.0f);
    private final NumberValue<Integer> fadeTime =
            num("fade_time", "fade_time", 600, 50, 2000);

    // ==========================================
    //   Integrated CevBreaker Offensive Settings
    // ==========================================
    private final BooleanValue cevBreaker = description(
            bool("cev_breaker", "cev_breaker", false),
            "Executes CevBreaker attacks on enemies in holes or safe positions by placing obsidian and crystals above their heads"
    );
    private final NumberValue<Double> cevRange = visibleWhen(
            num("cev_range", "cev_range", 4.5, 2.0, 6.0),
            cevBreaker::get
    );
    private final NumberValue<Integer> cevBreakDelay = visibleWhen(
            num("cev_break_delay", "cev_break_delay", 0, 0, 5),
            cevBreaker::get
    );
    private final BooleanValue cevAntiSuicide = visibleWhen(
            bool("cev_anti_suicide", "cev_anti_suicide", true),
            cevBreaker::get
    );

    // Runtime state - Surround
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> miningBlocks = new ConcurrentHashMap<>();
    private BlockPos startPos;
    private int startY;
    private int delayCounter;

    // Runtime state - CevBreaker
    public enum CevStage {
        PLACE_OBSIDIAN,
        PLACE_CRYSTAL,
        MINE_OBSIDIAN,
        DETONATE
    }

    private CevStage cevStage = CevStage.PLACE_OBSIDIAN;
    private Player cevTarget = null;
    private BlockPos cevTargetCeiling = null;
    private float cevMiningProgress = 0.0f;
    private boolean cevIsMining = false;
    private int cevBreakDelayTimer = 0;

    {
        setDefaultBind("X");
    }

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null) return;

        startPos = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
        startY = Mth.floor(player.getY());
        delayCounter = 0;
        renderBlocks.clear();
        miningBlocks.clear();

        if (centerMode.get() != CenterMode.NONE) {
            applyCenter(player, centerMode.get());
        }

        resetCevState();
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        renderBlocks.clear();
        miningBlocks.clear();
        startPos = null;

        resetCevState();
    }

    private void resetCevState() {
        if (cevIsMining && cevTargetCeiling != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    cevTargetCeiling,
                    Direction.UP
            ));
        }
        cevStage = CevStage.PLACE_OBSIDIAN;
        cevTarget = null;
        cevTargetCeiling = null;
        cevMiningProgress = 0.0f;
        cevIsMining = false;
        cevBreakDelayTimer = 0;
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
        if (player == null || level == null || mc.gameMode == null) {
            return;
        }

        // Clean up mining entries older than 2 seconds
        long now = System.currentTimeMillis();
        miningBlocks.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);

        // Auto-disable and validation checks
        if (onlyOnGround.get() && !player.onGround()) {
            if (disableOnJump.get() && (player.getDeltaMovement().y > 0.1 || mc.options.keyJump.isDown())) {
                setEnabled(false);
                return;
            }
            return;
        }

        if (disableOnLeave.get() && startPos != null) {
            BlockPos currentPos = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
            if (!currentPos.equals(startPos)) {
                setEnabled(false);
                return;
            }
        }

        if (disableOnYChange.get() && Mth.floor(player.getY()) != startY) {
            setEnabled(false);
            return;
        }

        // Smooth centering if active
        if (centerMode.get() == CenterMode.SMOOTH) {
            applyCenter(player, CenterMode.SMOOTH);
        }

        // Execute CevBreaker offensive pipeline if enabled
        if (cevBreaker.get()) {
            updateCevBreaker(player, level);
        }

        // Delay handling for surround placement
        if (delayTicks.get() > 0 && delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Execute surround placement cycle
        executePlacementCycle(player, level);
        delayCounter = delayTicks.get();
    }

    private void executePlacementCycle(LocalPlayer player, Level level) {
        List<BlockPos> basePositions = resolveBasePositions(player);
        Set<BlockPos> candidates = new LinkedHashSet<>();

        // 1. Floor block
        if (floor.get() || surroundMode.get() == SurroundMode.FULL) {
            for (int i = 0; i < basePositions.size(); i++) {
                candidates.add(basePositions.get(i).below());
            }
        }

        // 2. Horizontal surround blocks
        for (int i = 0; i < basePositions.size(); i++) {
            BlockPos base = basePositions.get(i);
            for (Direction dir : HORIZONTALS) {
                BlockPos offset = base.relative(dir);
                if (!basePositions.contains(offset)) {
                    candidates.add(offset);
                }
            }
        }

        // 3. Double height / Anti-City reinforcement
        if (doubleHeight.get()) {
            for (int i = 0; i < basePositions.size(); i++) {
                BlockPos base = basePositions.get(i);
                for (Direction dir : HORIZONTALS) {
                    BlockPos offset = base.relative(dir).above();
                    if (!basePositions.contains(offset)) {
                        candidates.add(offset);
                    }
                }
            }
        }

        // 4. AntiCity: reinforce adjacent blocks being mined or broken
        if (antiCity.get()) {
            int px = Mth.floor(player.getX());
            int py = Mth.floor(player.getY());
            int pz = Mth.floor(player.getZ());
            for (int i = 0; i < basePositions.size(); i++) {
                BlockPos base = basePositions.get(i);
                for (Direction dir : HORIZONTALS) {
                    BlockPos feetPos = base.relative(dir);
                    if (miningBlocks.containsKey(feetPos) || isReplaceable(level, feetPos)) {
                        candidates.add(feetPos.above());
                        candidates.add(new BlockPos(feetPos.getX() + (feetPos.getX() - px), py, feetPos.getZ() + (feetPos.getZ() - pz)));
                    }
                }
            }
        }

        // 5. AntiFaceplace: if enemy within 4 blocks, reinforce upper layer
        if (antiFaceplace.get() && isEnemyWithinRange(player, level, 4.0)) {
            int px = Mth.floor(player.getX());
            int py = Mth.floor(player.getY());
            int pz = Mth.floor(player.getZ());
            candidates.add(new BlockPos(px + 1, py + 1, pz));
            candidates.add(new BlockPos(px - 1, py + 1, pz));
            candidates.add(new BlockPos(px, py + 1, pz + 1));
            candidates.add(new BlockPos(px, py + 1, pz - 1));
        }

        // Collect and prioritize positions that actually need placement
        List<PlacementTarget> targets = new ArrayList<>();
        float rangeVal = placeRange.get();

        for (BlockPos pos : candidates) {
            if (isSafeBlock(level, pos)) continue;
            if (!isReplaceable(level, pos)) continue;

            // Handle crystal clearing
            if (clearCrystals.get()) {
                clearCrystalsAt(player, level, pos);
            }

            // Entity collision check
            if (isEntityBlocked(player, level, pos)) continue;

            // Direct hit result via BlockPlacer
            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, rangeVal);
            if (hit != null) {
                targets.add(new PlacementTarget(pos, hit, false));
            } else if (helpingBlocks.get()) {
                BlockPos support = pos.below();
                if (isReplaceable(level, support) && !isSafeBlock(level, support) && !isEntityBlocked(player, level, support)) {
                    BlockHitResult supportHit = BlockPlacer.findOptimalPlacementHit(level, player, support, rangeVal);
                    if (supportHit != null) {
                        targets.add(new PlacementTarget(support, supportHit, true));
                    } else {
                        for (Direction dir : HORIZONTALS) {
                            BlockPos diag = support.relative(dir);
                            if (isReplaceable(level, diag) && !isSafeBlock(level, diag) && !isEntityBlocked(player, level, diag)) {
                                BlockHitResult diagHit = BlockPlacer.findOptimalPlacementHit(level, player, diag, rangeVal);
                                if (diagHit != null) {
                                    targets.add(new PlacementTarget(diag, diagHit, true));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            if (!cevBreaker.get() || cevTarget == null) {
                RotationManager.INSTANCE.clear(this);
            }
            return;
        }

        // Sort targets: support blocks first (priority 0), then closest surround blocks
        Vec3 eyes = player.getEyePosition();
        targets.sort(Comparator.comparingInt(PlacementTarget::priority)
                .thenComparingDouble(t -> eyes.distanceToSqr(Vec3.atCenterOf(t.pos()))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;

        for (int i = 0; i < targets.size(); i++) {
            if (placedCount >= maxBlocks) break;
            PlacementTarget target = targets.get(i);
            if (placeTarget(player, target)) {
                placedCount++;
                renderBlocks.put(target.pos(), System.currentTimeMillis());
            }
        }
    }

    private boolean placeTarget(LocalPlayer player, PlacementTarget target) {
        BlockHitResult hit = target.hit();
        if (hit == null) return false;

        InteractionHand hand = null;
        int hotbarSlot = -1;

        if (isResistantBlock(player.getOffhandItem(), blockFilter.get())) {
            hand = InteractionHand.OFF_HAND;
        } else {
            hotbarSlot = findResistantHotbarSlot(player, blockFilter.get());
            if (hotbarSlot != -1) {
                hand = InteractionHand.MAIN_HAND;
            }
        }

        if (hand == null) return false;

        boolean rotateVal = rotationMode.get() != RotationMode.NONE;
        return BlockPlacer.placeBlock(
                this,
                hit,
                hand,
                hotbarSlot,
                rotateVal,
                BlockPlacer.SwingMode.CLIENT_AND_SERVER
        );
    }

    private int findResistantHotbarSlot(LocalPlayer player, BlockFilter filter) {
        if (filter == BlockFilter.PREFER_OBSIDIAN) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.OBSIDIAN)) return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isResistantBlock(stack, filter)) return i;
        }

        // If not in hotbar and swapScope is FULL, search inventory and swap
        if (swapScope.get() == InventorySearchScope.FULL) {
            for (int i = 9; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isResistantBlock(stack, filter)) {
                    int clientSlot = InventorySwap.INSTANCE.clientSelectedSlot();
                    if (clientSlot < 0 || clientSlot >= 9) clientSlot = 0;
                    InventorySwap.INSTANCE.swapInventoryToHotbar(i, clientSlot);
                    return clientSlot;
                }
            }
        }

        return -1;
    }

    private boolean isResistantBlock(ItemStack stack, BlockFilter filter) {
        if (stack == null || stack.isEmpty()) return false;
        if (filter == BlockFilter.OBSIDIAN_ONLY) {
            return stack.is(Items.OBSIDIAN);
        }
        if (filter == BlockFilter.PREFER_OBSIDIAN || filter == BlockFilter.ANY_RESISTANT) {
            return stack.is(Items.OBSIDIAN)
                    || stack.is(Items.CRYING_OBSIDIAN)
                    || stack.is(Items.ENDER_CHEST)
                    || stack.is(Items.ANVIL)
                    || stack.is(Items.CHIPPED_ANVIL)
                    || stack.is(Items.DAMAGED_ANVIL);
        }
        return false;
    }

    private boolean isSafeBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        float resistance = state.getBlock().getExplosionResistance();
        return resistance >= 600.0f;
    }

    private boolean isReplaceable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private void clearCrystalsAt(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box);
        for (int i = 0; i < crystals.size(); i++) {
            EndCrystal crystal = crystals.get(i);
            if (crystal.isAlive()) {
                mc.gameMode.attack(player, crystal);
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private boolean isEntityBlocked(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        if (player.getBoundingBox().intersects(box)) {
            return !(pos.getY() < Mth.floor(player.getY()));
        }

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (entity instanceof EndCrystal) continue;
            if (entity.isAlive()) return true;
        }
        return false;
    }

    private List<BlockPos> resolveBasePositions(LocalPlayer player) {
        List<BlockPos> positions = new ArrayList<>();
        if (surroundMode.get() == SurroundMode.RUSSIAN) {
            AABB box = player.getBoundingBox();
            int minX = Mth.floor(box.minX);
            int maxX = Mth.floor(box.maxX);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.floor(box.maxZ);
            int y = Mth.floor(player.getY() + 0.1);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        } else {
            positions.add(BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ()));
        }
        return positions;
    }

    private boolean isEnemyWithinRange(LocalPlayer player, Level level, double range) {
        double rangeSq = range * range;
        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (CategoryRules.determine(other.getGameProfile().name()) == CategoryType.FRIEND) continue;
            if (player.distanceToSqr(other) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private void applyCenter(LocalPlayer player, CenterMode mode) {
        double centerX = Mth.floor(player.getX()) + 0.5;
        double centerZ = Mth.floor(player.getZ()) + 0.5;
        double dx = centerX - player.getX();
        double dz = centerZ - player.getZ();

        if (mode == CenterMode.INSTANT) {
            if (Math.abs(dx) > 0.05 || Math.abs(dz) > 0.05) {
                player.setPos(centerX, player.getY(), centerZ);
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                            centerX, player.getY(), centerZ, player.onGround(), player.horizontalCollision
                    ));
                }
                player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            }
        } else if (mode == CenterMode.SMOOTH) {
            double dist = Math.hypot(dx, dz);
            if (dist > 0.05) {
                double speed = Math.min(dist, 0.2);
                player.setDeltaMovement(
                        (dx / dist) * speed,
                        player.getDeltaMovement().y,
                        (dz / dist) * speed
                );
            } else {
                player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            }
        }
    }

    // ==========================================
    //   CevBreaker Offensive Execution Pipeline
    // ==========================================

    private void updateCevBreaker(LocalPlayer player, Level level) {
        if (cevBreakDelayTimer > 0) {
            cevBreakDelayTimer--;
        }

        if (cevTarget == null || !isValidCevTarget(player, cevTarget, cevRange.get()) || !isTargetInHoleOrSafe(level, cevTarget)) {
            cevTarget = findCevTarget(player, level, cevRange.get());
            if (cevTarget == null) {
                resetCevState();
                return;
            }
            cevStage = CevStage.PLACE_OBSIDIAN;
            cevTargetCeiling = cevTarget.blockPosition().above(2);
            cevMiningProgress = 0.0f;
            cevIsMining = false;
        } else {
            cevTargetCeiling = cevTarget.blockPosition().above(2);
        }

        if (cevTargetCeiling == null) return;

        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(cevTargetCeiling)) > cevRange.get() + 1.5) {
            resetCevState();
            return;
        }

        switch (cevStage) {
            case PLACE_OBSIDIAN -> handleCevPlaceObsidian(player, level);
            case PLACE_CRYSTAL -> handleCevPlaceCrystal(player, level);
            case MINE_OBSIDIAN -> handleCevMineObsidian(player, level);
            case DETONATE -> handleCevDetonate(player, level);
        }
    }

    private void handleCevPlaceObsidian(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (ceilingState.is(Blocks.OBSIDIAN) || ceilingState.is(Blocks.CRYING_OBSIDIAN)) {
            cevStage = CevStage.PLACE_CRYSTAL;
            handleCevPlaceCrystal(player, level);
            return;
        }

        if (ceilingState.is(Blocks.BEDROCK)) {
            cevTarget = null;
            return;
        }

        if (isReplaceable(level, cevTargetCeiling)) {
            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, cevTargetCeiling, cevRange.get().floatValue() + 1.5f);
            if (hit != null) {
                int obbySlot = findObsidianSlot(player);
                if (obbySlot != -1) {
                    if (BlockPlacer.placeBlock(this, hit, InteractionHand.MAIN_HAND, obbySlot, true, BlockPlacer.SwingMode.CLIENT_AND_SERVER)) {
                        cevStage = CevStage.PLACE_CRYSTAL;
                    }
                }
            }
        }
    }

    private void handleCevPlaceCrystal(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (!ceilingState.is(Blocks.OBSIDIAN) && !ceilingState.is(Blocks.BEDROCK)) {
            cevStage = CevStage.PLACE_OBSIDIAN;
            return;
        }

        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal existingCrystal = findExistingCrystal(level, crystalAir);
        if (existingCrystal != null) {
            cevStage = CevStage.MINE_OBSIDIAN;
            handleCevMineObsidian(player, level);
            return;
        }

        if (!level.isInWorldBounds(crystalAir)) return;

        Vec3 clickVec = new Vec3(cevTargetCeiling.getX() + 0.5, cevTargetCeiling.getY() + 1.0, cevTargetCeiling.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(clickVec, Direction.UP, cevTargetCeiling, false);

        int crystalSlot = findCrystalSlot(player);
        if (crystalSlot != -1) {
            InteractionHand hand = player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (hand == InteractionHand.OFF_HAND || InventorySwap.INSTANCE.leaseHotbar(this, crystalSlot, 2)) {
                mc.gameMode.useItemOn(player, hand, hit);
                player.swing(hand);
                cevStage = CevStage.MINE_OBSIDIAN;
            }
        }
    }

    private void handleCevMineObsidian(LocalPlayer player, Level level) {
        BlockState ceilingState = level.getBlockState(cevTargetCeiling);
        if (ceilingState.isAir() || !ceilingState.is(Blocks.OBSIDIAN)) {
            finishCevMining();
            cevStage = CevStage.DETONATE;
            handleCevDetonate(player, level);
            return;
        }

        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);
        if (crystal == null) {
            cevStage = CevStage.PLACE_CRYSTAL;
            return;
        }

        mineCevObsidian(player, level, cevTargetCeiling, ceilingState);
    }

    private void handleCevDetonate(LocalPlayer player, Level level) {
        BlockPos crystalAir = cevTargetCeiling.above();
        EndCrystal crystal = findExistingCrystal(level, crystalAir);

        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) {
            cevStage = CevStage.PLACE_OBSIDIAN;
            cevBreakDelayTimer = 0;
            return;
        }

        if (cevBreakDelayTimer > 0) return;

        if (cevAntiSuicide.get()) {
            Vec3 explosionPos = crystal.position();
            float selfDamage = ExplosionDamageUtil.getCrystalDamage(player, explosionPos, 0, false);
            if (!ExplosionDamageRules.isSafe(player, selfDamage, false)) {
                return;
            }
        }

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundInteractPacket(
                    crystal.getId(),
                    null,
                    null,
                    player.isShiftKeyDown()
            ));
        }
        mc.gameMode.attack(player, crystal);
        player.swing(InteractionHand.MAIN_HAND);
        cevBreakDelayTimer = cevBreakDelay.get();
        cevStage = CevStage.PLACE_OBSIDIAN;
    }

    private void mineCevObsidian(LocalPlayer player, Level level, BlockPos pos, BlockState state) {
        int bestToolSlot = findBestPickaxe(player);

        if (!cevIsMining) {
            cevIsMining = true;
            cevMiningProgress = 0.0f;

            Rotation rot = Rotation.lookingAt(Vec3.atCenterOf(pos), player.getEyePosition()).normalize();
            RotationManager.INSTANCE.snapServerRotation(rot, ROTATION_PRIORITY, this, 2);

            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        pos,
                        Direction.UP
                ));
            }
            player.swing(InteractionHand.MAIN_HAND);
        }

        if (bestToolSlot >= 0 && !InventorySwap.INSTANCE.isHotbarLeasedBy(this)) {
            InventorySwap.INSTANCE.leaseHotbar(this, bestToolSlot, 2);
        }

        float delta = state.getDestroyProgress(player, level, pos);
        cevMiningProgress += delta;

        if (cevMiningProgress >= 1.0f) {
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        pos,
                        Direction.UP
                ));
            }
            player.swing(InteractionHand.MAIN_HAND);
            finishCevMining();
            cevStage = CevStage.DETONATE;
        }
    }

    private void finishCevMining() {
        cevIsMining = false;
        cevMiningProgress = 0.0f;
    }

    private EndCrystal findExistingCrystal(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.5);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive);
        return crystals.isEmpty() ? null : crystals.get(0);
    }

    private Player findCevTarget(LocalPlayer player, Level level, double maxRange) {
        double maxDistSq = maxRange * maxRange;
        Player best = null;
        double bestDistSq = maxDistSq;

        for (Player other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (CategoryRules.determine(other.getGameProfile().name()) == CategoryType.FRIEND) continue;

            double distSq = player.distanceToSqr(other);
            if (distSq <= bestDistSq && isTargetInHoleOrSafe(level, other)) {
                bestDistSq = distSq;
                best = other;
            }
        }
        return best;
    }

    private boolean isValidCevTarget(LocalPlayer player, Player target, double maxRange) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved()) return false;
        if (player.distanceTo(target) > maxRange) return false;
        return CategoryRules.determine(target.getGameProfile().name()) != CategoryType.FRIEND;
    }

    private boolean isTargetInHoleOrSafe(Level level, Player target) {
        BlockPos pos = target.blockPosition();
        int safeSides = 0;
        for (Direction dir : HORIZONTALS) {
            BlockPos side = pos.relative(dir);
            BlockState sideState = level.getBlockState(side);
            if (sideState.is(Blocks.BEDROCK) || sideState.is(Blocks.OBSIDIAN) || sideState.is(Blocks.CRYING_OBSIDIAN)) {
                safeSides++;
            }
        }
        return safeSides >= 3;
    }

    private int findObsidianSlot(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.OBSIDIAN)) return 40;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private int findCrystalSlot(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.END_CRYSTAL)) return 40;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }

    private int findBestPickaxe(LocalPlayer player) {
        int current = InventorySwap.INSTANCE.clientSelectedSlot();
        if (current >= 0 && current < 9) {
            if (player.getInventory().getItem(current).is(ItemTags.PICKAXES)) return current;
        }
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(ItemTags.PICKAXES)) return i;
        }
        return -1;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !renderEnabled.get() || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > fadeTime.get());

        float baseAlpha = ((fillColor.getArgb() >> 24) & 0xFF) / 255.0f;

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float progress = 1.0f - (float) (now - entry.getValue()) / fadeTime.get();
            progress = Mth.clamp(progress, 0.0f, 1.0f);

            AABB box = new AABB(pos);

            int fillArgb = ExplosionRenderUtil.applyOpacity(fillColor.getArgb(), progress * baseAlpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(lineColor.getArgb(), progress);

            if (renderMode.get() == RenderMode.BOX || renderMode.get() == RenderMode.BOTH) {
                ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            }

            if (renderMode.get() == RenderMode.OUTLINE || renderMode.get() == RenderMode.BOTH) {
                float prevWidth = RenderState.lineWidth;
                RenderState.lineWidth = lineWidth.get();
                try {
                    ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
                } finally {
                    RenderState.lineWidth = prevWidth;
                }
            }
        }

        // Render CevBreaker ceiling mining if active
        if (cevBreaker.get() && cevTargetCeiling != null && cevIsMining) {
            AABB ceilingBox = new AABB(cevTargetCeiling);
            int cevFill = ExplosionRenderUtil.applyOpacity(0xFFFF5500, Mth.clamp(cevMiningProgress, 0.1f, 0.8f));
            int cevLine = 0xFFFF5500;
            ExplosionRenderUtil.addFilledBox(renderer, ceilingBox, cevFill);
            ExplosionRenderUtil.addOutlineBox(renderer, ceilingBox, cevLine);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    public enum SurroundMode {
        NORMAL,
        RUSSIAN,
        FULL
    }

    public enum CenterMode {
        NONE,
        INSTANT,
        SMOOTH
    }

    public enum RotationMode {
        SILENT,
        PACKET,
        NONE
    }

    public enum BlockFilter {
        PREFER_OBSIDIAN,
        OBSIDIAN_ONLY,
        ANY_RESISTANT
    }

    public enum RenderMode {
        BOTH,
        BOX,
        OUTLINE
    }

    private record PlacementTarget(BlockPos pos, BlockHitResult hit, boolean isHelping) {
        public int priority() {
            return isHelping ? 0 : 1;
        }
    }
}
