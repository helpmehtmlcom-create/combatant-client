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
 * Automatically surrounds your feet with blast-resistant blocks (Obsidian, Crying Obsidian, etc.)
 * to protect against End Crystal explosions, following Mio Client and anarchy combat standards.
 */
@ModuleInfo(
        id = "feetplace",
        displayName = "FeetPlace",
        aliases = {"Surround", "FeetTrap", "AutoObsidian"},
        category = ModuleCategory.COMBAT,
        description = "Surrounds your feet with blast-resistant blocks to protect against crystal explosions."
)
public class FeetPlace extends Module {
    private static final int ROTATION_PRIORITY = 32;
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final Minecraft mc = Minecraft.getInstance();

    // Mode & Pattern
    private final EnumValue<SurroundMode> surroundMode =
            enumSetting("feetplace_mode", "mode", SurroundMode.NORMAL, SurroundMode.values());
    private final BooleanValue floor =
            bool("feetplace_floor", "floor", true);
    private final BooleanValue doubleHeight =
            bool("feetplace_double_height", "double_height", false);
    private final BooleanValue helpingBlocks =
            bool("feetplace_helping_blocks", "helping_blocks", true);

    // Centering & Auto-Disable
    private final EnumValue<CenterMode> centerMode =
            enumMode("feetplace_center", CenterMode.INSTANT, CenterMode.values());
    private final BooleanValue onlyOnGround =
            bool("feetplace_only_on_ground", "only_on_ground", true);
    private final BooleanValue disableOnJump =
            bool("feetplace_disable_on_jump", "disable_on_jump", true);
    private final BooleanValue disableOnLeave =
            bool("feetplace_disable_on_leave", "disable_on_leave", true);
    private final BooleanValue disableOnYChange =
            bool("feetplace_disable_on_y_change", "disable_on_y_change", false);

    // Placement speed & constraints
    private final NumberValue<Integer> blocksPerTick =
            num("feetplace_blocks_per_tick", "blocks_per_tick", 4, 1, 8);
    private final NumberValue<Integer> delayTicks =
            num("feetplace_delay_ticks", "delay_ticks", 0, 0, 10);
    private final NumberValue<Float> placeRange =
            num("feetplace_range", "range", 5.0f, 1.0f, 6.0f);
    private final NumberValue<Float> wallRange =
            num("feetplace_wall_range", "wall_range", 3.5f, 0.0f, 6.0f);
    private final BooleanValue airPlace =
            bool("feetplace_air_place", "air_place", false);
    private final BooleanValue clearCrystals =
            bool("feetplace_clear_crystals", "clear_crystals", true);
    // Item & Swapping
    private final EnumValue<BlockFilter> blockFilter =
            enumMode("feetplace_block_filter", BlockFilter.PREFER_OBSIDIAN, BlockFilter.values());
    private final EnumValue<InventorySearchScope> swapScope =
            enumSetting("feetplace_swap_scope", "swap_scope", InventorySearchScope.FULL, InventorySearchScope.values());
    private final EnumValue<InventorySwapVisibility> swapVisibility =
            enumSetting("feetplace_swap_visibility", "swap_visibility", InventorySwapVisibility.SILENT, InventorySwapVisibility.values());
    private final BooleanValue restoreItem =
            bool("feetplace_restore_item", "restore_item", true);

    // Rotation
    private final EnumValue<RotationMode> rotationMode =
            enumSetting("feetplace_rotation_mode", "rotation_mode", RotationMode.SILENT, RotationMode.values());
    // Visuals
    private final BooleanValue renderEnabled =
            bool("feetplace_render", "render", true);
    private final EnumValue<RenderMode> renderMode =
            enumMode("feetplace_render_mode", RenderMode.BOTH, RenderMode.values());
    private final RGBAColorValue fillColor =
            color("feetplace_fill_color", "#4D800080");
    private final RGBAColorValue lineColor =
            color("feetplace_line_color", "#FF8000FF");
    private final NumberValue<Float> lineWidth =
            num("feetplace_line_width", "line_width", 1.5f, 0.5f, 5.0f);
    private final NumberValue<Integer> fadeTime =
            num("feetplace_fade_time", "fade_time", 600, 50, 2000);
    // Runtime state
    private final Map<BlockPos, Long> renderBlocks = new ConcurrentHashMap<>();
    private BlockPos startPos;
    private int startY;
    private int delayCounter;

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

        if (centerMode.get() != CenterMode.NONE) {
            applyCenter(player, centerMode.get());
        }
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        renderBlocks.clear();
        startPos = null;
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

        // Delay handling
        if (delayTicks.get() > 0 && delayCounter > 0) {
            delayCounter--;
            return;
        }

        // Execute placement cycle
        executePlacementCycle(player, level);
        delayCounter = delayTicks.get();
    }

    private void executePlacementCycle(LocalPlayer player, Level level) {
        List<BlockPos> basePositions = resolveBasePositions(player);
        Set<BlockPos> candidates = new LinkedHashSet<>();

        // 1. Floor block
        if (floor.get() || surroundMode.get() == SurroundMode.FULL) {
            for (BlockPos base : basePositions) {
                candidates.add(base.below());
            }
        }

        // 2. Horizontal surround blocks
        for (BlockPos base : basePositions) {
            for (Direction dir : HORIZONTALS) {
                BlockPos offset = base.relative(dir);
                if (!basePositions.contains(offset)) {
                    candidates.add(offset);
                }
            }
        }

        // 3. Double height (anti-city)
        if (doubleHeight.get()) {
            for (BlockPos base : basePositions) {
                for (Direction dir : HORIZONTALS) {
                    BlockPos offset = base.relative(dir).above();
                    if (!basePositions.contains(offset)) {
                        candidates.add(offset);
                    }
                }
            }
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

            // Entity collision check: cannot place if an entity intersects
            if (isEntityBlocked(player, level, pos)) continue;

            // Check direct hit result via BlockPlacer
            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, rangeVal);
            if (hit != null) {
                targets.add(new PlacementTarget(pos, hit, false));
            } else if (helpingBlocks.get()) {
                // If air is below placement target, check support block directly below
                BlockPos support = pos.below();
                if (isReplaceable(level, support) && !isSafeBlock(level, support) && !isEntityBlocked(player, level, support)) {
                    BlockHitResult supportHit = BlockPlacer.findOptimalPlacementHit(level, player, support, rangeVal);
                    if (supportHit != null) {
                        targets.add(new PlacementTarget(support, supportHit, true));
                    } else {
                        // Support directly below also has no adjacent attachable block (air below)!
                        // Automatically handle diagonal support blocks to anchor the structure
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
            RotationManager.INSTANCE.clear(this);
            return;
        }

        // Sort targets: support blocks first (priority 0), then closest surround blocks
        Vec3 eyes = player.getEyePosition();
        targets.sort(Comparator.comparingInt(PlacementTarget::priority)
                .thenComparingDouble(t -> eyes.distanceToSqr(Vec3.atCenterOf(t.pos()))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;

        for (PlacementTarget target : targets) {
            if (placedCount >= maxBlocks) break;
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
            // Player's feet: if pos is strictly below the player's min Y, floor is not blocked
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
                double speed = Math.min(0.2, dist);
                double angle = Math.atan2(dz, dx);
                player.setDeltaMovement(Math.cos(angle) * speed, player.getDeltaMovement().y, Math.sin(angle) * speed);
            } else {
                player.setPos(centerX, player.getY(), centerZ);
                player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            }
        }
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
            if (isHelping) return 0;
            return 1;
        }
    }
}
