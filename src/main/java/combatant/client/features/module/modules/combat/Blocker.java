/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Automatically places defensive blast-resistant blocks to protect against
 * Crystal PvP attacks (Cev, Civ, City / Surround-breaking), following Mio Client standards.
 */
@ModuleInfo(
        id = "blocker",
        displayName = "Blocker",
        aliases = {"AntiCev", "AntiCity", "CevBlocker"},
        category = ModuleCategory.COMBAT,
        description = "Blocks certain offsets to prevent crystal damage."
)
public class Blocker extends Module {
    private static final int ROTATION_PRIORITY = 33;
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final Minecraft mc = Minecraft.getInstance();

    // Mode & Pattern
    private final EnumValue<OffsetMode> offsets =
            enumSetting("blocker_offsets", "offsets", OffsetMode.FULL, OffsetMode.values());
    private final BooleanValue corners =
            bool("blocker_corners", "corners", true);
    private final NumberValue<Float> blockDamage =
            num("blocker_block_damage", "block_damage", 0.5f, 0.0f, 1.0f);
    private final BooleanValue antiTnt =
            bool("blocker_anti_tnt", "anti_tnt", false);
    private final BooleanValue antiAnchor =
            bool("blocker_anti_anchor", "anti_anchor", false);
    private final BooleanValue expand =
            bool("blocker_expand", "expand", true);
    private final BooleanValue clearCrystals =
            bool("blocker_clear_crystals", "clear_crystals", true);
    private final BooleanValue helpingBlocks =
            bool("blocker_helping_blocks", "helping_blocks", true);

    // Placement speed & constraints
    private final NumberValue<Integer> blocksPerTick =
            num("blocker_blocks_per_tick", "blocks_per_tick", 4, 1, 8);
    private final NumberValue<Integer> delayTicks =
            num("blocker_delay_ticks", "delay_ticks", 0, 0, 10);
    private final NumberValue<Float> placeRange =
            numCommon("blocker_range", "range", CommonSettingSchemas.PLACEMENT_RANGE, 5.0f, 1.0f, 6.0f);
    private final NumberValue<Float> wallRange =
            numCommon("blocker_wall_range", "wall_range", CommonSettingSchemas.PLACEMENT_WALL_RANGE, 3.5f, 0.0f, 6.0f);
    private final BooleanValue airPlace =
            boolCommon("blocker_air_place", CommonSettingSchemas.AIR_PLACE, false);

    // Item & Swapping
    private final EnumValue<BlockFilter> blockFilter =
            enumSetting("blocker_block_filter", "block_filter", BlockFilter.PREFER_OBSIDIAN, BlockFilter.values());
    private final EnumValue<InventorySearchScope> swapScope =
            enumCommon("blocker_swap_scope", "swap_scope", CommonSettingSchemas.INVENTORY_SEARCH_SCOPE,
                    InventorySearchScope.FULL, InventorySearchScope.values());
    private final EnumValue<InventorySwapVisibility> swapVisibility =
            enumCommon("blocker_swap_visibility", "swap_visibility", CommonSettingSchemas.INVENTORY_SWAP_VISIBILITY,
                    InventorySwapVisibility.SILENT, InventorySwapVisibility.values());
    private final BooleanValue restoreItem =
            boolCommon("blocker_restore_item", CommonSettingSchemas.INVENTORY_RESTORE_ITEM, true);

    // Rotation
    private final EnumValue<RotationMode> rotationMode =
            enumCommon("blocker_rotation_mode", "rotation_mode", CommonSettingSchemas.ROTATION_MODE,
                    RotationMode.SILENT, RotationMode.values());

    // Visuals
    private final BooleanValue renderEnabled =
            bool("blocker_render", "render", true);
    private final EnumValue<RenderMode> renderMode =
            enumSetting("blocker_render_mode", "render_mode", RenderMode.BOTH, RenderMode.values());
    private final RGBAColorValue fillColor =
            common(color("blocker_fill_color", "#8000FF40"), CommonSettingSchemas.FILL_COLOR);
    private final RGBAColorValue lineColor =
            common(color("blocker_line_color", "#8000FFFF"), CommonSettingSchemas.LINE_COLOR);
    private final NumberValue<Float> lineWidth =
            numCommon("blocker_line_width", CommonSettingSchemas.LINE_WIDTH, 1.5f, 0.5f, 5.0f);
    private final NumberValue<Integer> fadeTime =
            numCommon("blocker_fade_time", CommonSettingSchemas.FADE_TIME, 600, 50, 2000);

    // Runtime state
    private final Set<BlockPos> placePositions = new LinkedHashSet<>();
    private final Map<BlockPos, Float> miningProgress = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> miningTimes = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();
    private int delayCounter;

    @Override
    public void onEnable() {
        placePositions.clear();
        miningProgress.clear();
        miningTimes.clear();
        renderBlocks.clear();
        delayCounter = 0;
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        placePositions.clear();
        miningProgress.clear();
        miningTimes.clear();
        renderBlocks.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            BlockPos pos = packet.getPos();
            int prog = packet.getProgress();
            if (prog < 0) {
                miningProgress.remove(pos);
                miningTimes.remove(pos);
                return;
            }
            float progress = (prog + 1) / 10.0f;
            miningProgress.put(pos, progress);
            miningTimes.put(pos, System.currentTimeMillis());

            if (progress >= blockDamage.get()) {
                checkAndQueueDefenses(pos);
            }
        }
    }

    @EventHandler
    private void onBreakBlock(EventBreakBlock event) {
        if (!isEnabled()) return;
        if (event.getPos() != null) {
            checkAndQueueDefenses(event.getPos());
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

        // Clean up stale mining entries older than 2 seconds
        long now = System.currentTimeMillis();
        miningTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 2000L) {
                miningProgress.remove(entry.getKey());
                return true;
            }
            return false;
        });

        // Check overhead threats (Anti-TNT and Anti-Anchor)
        checkOverheadThreats(player, level);

        // Check if any monitored position was broken into air
        checkMonitoredOffsets(player, level);

        // Delay handling
        if (delayTicks.get() > 0 && delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Execute placement cycle
        executePlacementCycle(player, level);
        delayCounter = delayTicks.get();
    }

    private void checkOverheadThreats(LocalPlayer player, Level level) {
        List<BlockPos> basePositions = resolveBasePositions(player);
        for (BlockPos base : basePositions) {
            BlockPos overhead = base.above(2);
            BlockState state = level.getBlockState(overhead);
            if (antiTnt.get() && state.is(Blocks.TNT)) {
                queuePlacement(base.above(3));
            }
            if (antiAnchor.get() && state.is(Blocks.RESPAWN_ANCHOR)) {
                queuePlacement(base.above(3));
            }

            // Incoming crystal detection above player's head
            AABB headBox = new AABB(base.above(2)).minmax(new AABB(base.above(3))).inflate(1.5, 1.0, 1.5);
            List<EndCrystal> overheadCrystals = level.getEntitiesOfClass(EndCrystal.class, headBox, Entity::isAlive);
            if (!overheadCrystals.isEmpty()) {
                if (clearCrystals.get()) {
                    for (EndCrystal crystal : overheadCrystals) {
                        clearCrystalsAt(player, level, crystal.blockPosition());
                    }
                }
                queuePlacement(base.above(3));
                queuePlacement(base.above(4));
            }
        }
    }

    private void checkMonitoredOffsets(LocalPlayer player, Level level) {
        List<BlockPos> basePositions = resolveBasePositions(player);
        OffsetMode mode = offsets.get();

        for (BlockPos base : basePositions) {
            // Surround feet offsets
            if (mode == OffsetMode.FULL || mode == OffsetMode.SURROUND) {
                for (Direction dir : HORIZONTALS) {
                    BlockPos surround = base.relative(dir);
                    Float prog = miningProgress.get(surround);
                    if ((prog != null && prog >= blockDamage.get()) || isReplaceable(level, surround)) {
                        checkAndQueueDefenses(surround);
                    }
                }
            }

            // Civ upper surround offsets
            if (mode == OffsetMode.FULL || mode == OffsetMode.CIV) {
                for (Direction dir : HORIZONTALS) {
                    BlockPos upper = base.relative(dir).above();
                    Float prog = miningProgress.get(upper);
                    if ((prog != null && prog >= blockDamage.get()) || isReplaceable(level, upper)) {
                        checkAndQueueDefenses(upper);
                    }
                }
            }

            // Cev ceiling offsets
            if (mode == OffsetMode.FULL || mode == OffsetMode.CEV) {
                BlockPos cevPos = base.above(2);
                Float prog = miningProgress.get(cevPos);
                if ((prog != null && prog >= blockDamage.get()) || isReplaceable(level, cevPos)) {
                    checkAndQueueDefenses(cevPos);
                }
            }
        }
    }

    private void checkAndQueueDefenses(BlockPos threatenedPos) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        List<BlockPos> basePositions = resolveBasePositions(player);
        OffsetMode mode = offsets.get();

        for (BlockPos base : basePositions) {
            // 1. CEV check (head at y+2, ceiling at y+3)
            if (mode == OffsetMode.FULL || mode == OffsetMode.CEV) {
                BlockPos cevPos = base.above(2);
                BlockPos cevCeiling = base.above(3);

                if (threatenedPos.equals(cevPos)) {
                    // Block above the crystal spot (y+3) to block the crystal/explosion
                    queuePlacement(cevCeiling);
                    // Re-place the head block if broken
                    if (isReplaceable(level, cevPos)) {
                        queuePlacement(cevPos);
                    }
                } else if (threatenedPos.equals(cevCeiling)) {
                    queuePlacement(base.above(4));
                }
            }

            // 2. CIV check (upper surround at y+1)
            if (mode == OffsetMode.FULL || mode == OffsetMode.CIV) {
                for (Direction dir : HORIZONTALS) {
                    BlockPos upper = base.relative(dir).above();
                    if (threatenedPos.equals(upper)) {
                        queuePlacement(upper.above());
                        if (isReplaceable(level, upper)) {
                            queuePlacement(upper);
                        }
                    }
                }
            }

            // 3. Surround check (feet level at y)
            if (mode == OffsetMode.FULL || mode == OffsetMode.SURROUND) {
                for (Direction dir : HORIZONTALS) {
                    BlockPos surroundPos = base.relative(dir);
                    if (threatenedPos.equals(surroundPos)) {
                        // Primary: block above the surround block
                        queuePlacement(surroundPos.above());

                        // Re-place surround block itself if already broken
                        if (isReplaceable(level, surroundPos)) {
                            queuePlacement(surroundPos);
                        }

                        // Expand: place outer block at distance 2 in that direction
                        if (expand.get()) {
                            BlockPos outer = surroundPos.relative(dir);
                            queuePlacement(outer);
                        }

                        // Corners: also cover the two adjacent corner positions
                        if (corners.get()) {
                            Direction clock = dir.getClockWise();
                            Direction counter = dir.getCounterClockWise();
                            queuePlacement(surroundPos.relative(clock));
                            queuePlacement(surroundPos.relative(counter));
                        }
                    }
                }
            }
        }
    }

    private void queuePlacement(BlockPos pos) {
        if (pos == null || mc.level == null || mc.player == null) return;
        if (!mc.level.isInWorldBounds(pos)) return;
        if (isSafeBlock(mc.level, pos)) return;
        double distSq = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
        float r = placeRange.get() + 1.0f;
        if (distSq > r * r) return;

        placePositions.add(pos);
    }

    private void executePlacementCycle(LocalPlayer player, Level level) {
        if (placePositions.isEmpty()) return;

        Vec3 eyes = player.getEyePosition();
        float rangeVal = placeRange.get();
        float wallRangeVal = wallRange.get();
        boolean air = airPlace.get();
        boolean help = helpingBlocks.get();

        placePositions.removeIf(pos -> isSafeBlock(level, pos)
                || eyes.distanceToSqr(Vec3.atCenterOf(pos)) > rangeVal * rangeVal);

        if (placePositions.isEmpty()) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        List<PlacementTarget> targets = new ArrayList<>();
        for (BlockPos pos : placePositions) {
            if (!isReplaceable(level, pos)) continue;

            if (clearCrystals.get()) {
                clearCrystalsAt(player, level, pos);
            }

            if (isEntityBlocked(player, level, pos)) continue;

            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, rangeVal);
            if (hit != null) {
                targets.add(new PlacementTarget(pos, hit, false));
            } else if (help && !air) {
                BlockPos support = pos.below();
                if (isReplaceable(level, support) && !isSafeBlock(level, support) && !isEntityBlocked(player, level, support)) {
                    BlockHitResult supportHit = BlockPlacer.findOptimalPlacementHit(level, player, support, rangeVal);
                    if (supportHit != null) {
                        targets.add(new PlacementTarget(support, supportHit, true));
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        targets.sort(Comparator.comparingInt(PlacementTarget::priority)
                .thenComparingDouble(t -> eyes.distanceToSqr(Vec3.atCenterOf(t.pos()))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;

        for (PlacementTarget target : targets) {
            if (placedCount >= maxBlocks) break;
            if (placeTarget(player, target)) {
                placedCount++;
                placePositions.remove(target.pos());
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
        return -1;
    }

    private void clearCrystalsAt(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, box);
        for (EndCrystal crystal : crystals) {
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

    private boolean isResistantBlock(ItemStack stack, BlockFilter filter) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        if (filter == BlockFilter.OBSIDIAN_ONLY) {
            return block == Blocks.OBSIDIAN;
        }
        return block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.ENDER_CHEST
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL;
    }

    public static boolean isSafeBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.ENDER_CHEST
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL;
    }

    public static boolean isReplaceable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !renderEnabled.get() || mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        int fadeMs = fadeTime.get();
        renderBlocks.entrySet().removeIf(entry -> now - entry.getValue() > fadeMs);

        for (Map.Entry<BlockPos, Long> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            float alpha = 1.0f - (float) (now - entry.getValue()) / (float) fadeMs;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);
            AABB box = new AABB(pos);

            int fillArgb = ExplosionRenderUtil.applyOpacity(fillColor.getArgb(), alpha);
            int lineArgb = ExplosionRenderUtil.applyOpacity(lineColor.getArgb(), alpha);

            if (renderMode.get() == RenderMode.BOX || renderMode.get() == RenderMode.BOTH) {
                ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);
            }
            if (renderMode.get() == RenderMode.OUTLINE || renderMode.get() == RenderMode.BOTH) {
                float prevWidth = RenderState.lineWidth;
                RenderState.lineWidth = Math.max(0.5f, lineWidth.get());
                try {
                    ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
                } finally {
                    RenderState.lineWidth = prevWidth;
                }
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    public enum OffsetMode implements EnumValue.IdProvider {
        FULL("full"),
        SURROUND("surround"),
        CEV("cev"),
        CIV("civ");

        private final String id;

        OffsetMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String getId() {
            return id;
        }
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
            if (isHelping) return 1;
            return 0;
        }
    }
}
