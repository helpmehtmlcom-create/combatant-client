/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@ModuleInfo(
        id = "surround",
        displayName = "Surround",
        aliases = {"FeetTrap", "ObsidianFeet", "SelfSurround"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.surround.description")
public final class Surround extends Module {

    private static final Set<Block> BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL
    );

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumSetting("surroundMode", "mode", Mode.FEET, Mode.values());
    private final EnumValue<CenterMode> center = enumSetting("surroundCenter", "center", CenterMode.NONE, CenterMode.values());
    private final EnumValue<BlockPriority> blockPriority = enumSetting(
            "surroundBlockPriority", "block_priority", BlockPriority.ANY, BlockPriority.values());
    private final NumberValue<Integer> blocksPerTick = num(
            "surroundBlocksPerTick", "blocks_per_tick", 4, 1, 8);
    private final BooleanValue dynamicHitbox = bool(
            "surroundDynamicHitbox", "dynamic_hitbox", true);
    private final BooleanValue support = bool(
            "surroundSupport", "support", true);
    private final BooleanValue attackCrystals = bool(
            "surroundAttackCrystals", "attack_crystals", true);
    private final BooleanValue disableOnJump = boolCommon(
            "surroundDisableOnJump", "disable_on_jump", CommonSettingSchemas.PLAYER_FALL_CHECK, true);
    private final BooleanValue disableInAir = bool("surroundDisableInAir", "disable_in_air", false);
    private final BooleanValue autoDisable = bool("surroundAutoDisable", "auto_disable", false);
    private final EnumValue<BlockPlacer.RotationMode> rotation = enumSetting(
            "surroundRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "surroundMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Double> range = numCommon(
            "surroundRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "surroundWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Integer> delay = num("surroundDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("surroundRender", "render", true);
    private final EnumValue<RenderMode> renderMode = enumSetting(
            "surroundRenderMode", "render_mode", RenderMode.BOTH, RenderMode.values());
    private final NumberValue<Float> slabHeight = num(
            "surroundSlabHeight", "slab_height", 0.25F, 0.05F, 1.0F);
    private final RGBAColorValue fillColor = color("surroundFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("surroundLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "surroundLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, this, 10,
            this::findPlacementSlot,
            range::get,
            wallRange::get,
            delay::get,
            delay::get,
            () -> 0,
            () -> 0,
            () -> 1,
            () -> rotation.get() == BlockPlacer.RotationMode.NO_ROTATION,
            () -> true,
            () -> false,
            () -> false,
            blocksPerTick::get,
            rotation::get,
            movementCorrection::get
    );
    private final List<BlockPos> currentTargets = new ArrayList<>();

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        applyCentering(player);
        blockPlacer.enable();
        updateTargets();
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        currentTargets.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        if (disableOnJump.get() && (mc.options.keyJump.isDown() || player.getDeltaMovement().y > 0.15D)) {
            setEnabled(false);
            return;
        }
        if (disableInAir.get() && !player.onGround()) {
            setEnabled(false);
            return;
        }
        if (center.get() == CenterMode.MOTION) {
            moveTowardCenter(player, 0.4D);
        }

        updateTargets();

        if (attackCrystals.get()) {
            attackBlockingCrystals(player);
        }

        blockPlacer.tick();

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void applyCentering(LocalPlayer player) {
        CenterMode cm = center.get();
        if (cm == CenterMode.NONE) return;

        double x = Math.floor(player.getX()) + 0.5D;
        double z = Math.floor(player.getZ()) + 0.5D;

        if (cm == CenterMode.MOTION) {
            moveTowardCenter(player, 0.5D);
            return;
        }

        player.setPos(x, player.getY(), z);
        if (cm == CenterMode.STRICT_TELEPORT) {
            player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
        }
        if (player.connection != null) {
            player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    x, player.getY(), z, player.onGround(), player.horizontalCollision));
        }
    }

    private static void moveTowardCenter(LocalPlayer player, double strength) {
        double x = Math.floor(player.getX()) + 0.5D;
        double z = Math.floor(player.getZ()) + 0.5D;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        if (Math.abs(dx) <= 0.05D && Math.abs(dz) <= 0.05D) return;
        player.setDeltaMovement(dx * strength, player.getDeltaMovement().y, dz * strength);
    }

    private void attackBlockingCrystals(LocalPlayer player) {
        if (mc.level == null || mc.gameMode == null || currentTargets.isEmpty()) return;
        double maxRangeSqr = range.get() * range.get();
        // A crystal is two blocks wide, so it can overlap several targets; hit it once.
        Set<EndCrystal> blocking = new LinkedHashSet<>();
        for (BlockPos pos : currentTargets) {
            blocking.addAll(mc.level.getEntitiesOfClass(
                    EndCrystal.class,
                    new AABB(pos),
                    c -> c.isAlive() && player.distanceToSqr(c) <= maxRangeSqr));
        }
        for (EndCrystal crystal : blocking) {
            mc.gameMode.attack(player, crystal);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean hasSolidNeighbor(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!mc.level.getBlockState(pos.relative(direction)).canBeReplaced()) return true;
        }
        return false;
    }

    private void updateTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        Set<BlockPos> floorPositions = new LinkedHashSet<>();
        if (dynamicHitbox.get()) {
            AABB bb = player.getBoundingBox().deflate(0.05, 0.0, 0.05);
            int minX = Mth.floor(bb.minX);
            int maxX = Mth.floor(bb.maxX);
            int minZ = Mth.floor(bb.minZ);
            int maxZ = Mth.floor(bb.maxZ);
            int feetY = Mth.floor(player.getY() + 0.05);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    floorPositions.add(new BlockPos(x, feetY, z));
                }
            }
        } else {
            floorPositions.add(BlockPos.containing(player.getX(), player.getY(), player.getZ()));
        }

        Set<BlockPos> feetSides = new LinkedHashSet<>();
        for (BlockPos floor : floorPositions) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = floor.relative(direction);
                if (!floorPositions.contains(side)) {
                    feetSides.add(side);
                }
            }
        }

        Mode currentMode = mode.get();
        boolean floor = currentMode == Mode.FULL || currentMode == Mode.FULL_BOX;
        boolean head = currentMode == Mode.ANTI_FACE_PLACE || currentMode == Mode.FULL_BOX;
        boolean roof = currentMode == Mode.COVER || currentMode == Mode.FULL_BOX;

        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos side : feetSides) {
            // A floating side has nothing to place against without the block under it.
            if (floor || (support.get() && !hasSolidNeighbor(side))) candidates.add(side.below());
            candidates.add(side);
            if (head) candidates.add(side.above());
        }
        for (BlockPos pos : floorPositions) {
            if (floor) candidates.add(pos.below());
            if (roof) candidates.add(pos.above(2));
        }

        List<BlockPos> needed = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (mc.level.getBlockState(pos).canBeReplaced()) needed.add(pos);
        }
        // Bottom-up, so each layer has the one below it to place against.
        needed.sort(Comparator.comparingInt(BlockPos::getY));

        currentTargets.clear();
        currentTargets.addAll(needed);
        blockPlacer.update(needed);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        BlockPriority priority = blockPriority.get();
        BlockPlacer.PlacementSlot preferred = findSlot(player, priority::matches);
        if (preferred != null || !priority.fallsBack()) return preferred;
        return findSlot(player, BLOCKS::contains);
    }

    private static BlockPlacer.PlacementSlot findSlot(LocalPlayer player, Predicate<Block> accepts) {
        ItemStack offhand = player.getOffhandItem();
        if (isBlockOf(offhand, accepts)) return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isBlockOf(stack, accepts)) return new BlockPlacer.PlacementSlot(slot, InteractionHand.MAIN_HAND, stack);
        }
        return null;
    }

    private static boolean isBlockOf(ItemStack stack, Predicate<Block> accepts) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && accepts.test(blockItem.getBlock());
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || currentTargets.isEmpty()) return;
        RenderMode rm = renderMode.get();
        boolean fill = rm != RenderMode.OUTLINE;
        boolean outline = rm != RenderMode.BOX;
        double height = rm == RenderMode.SLAB ? slabHeight.get() : 1.0D;
        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();
        float width = lineWidth.get();

        for (BlockPos pos : currentTargets) {
            AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + height, pos.getZ() + 1.0D);
            if (fill) renderer.filledBox(box, fillArgb);
            if (outline) renderer.outlineBox(box, lineArgb, width);
        }
    }

    public enum Mode {
        FEET,
        FULL,
        ANTI_FACE_PLACE,
        COVER,
        FULL_BOX
    }

    public enum CenterMode {
        NONE,
        TELEPORT,
        STRICT_TELEPORT,
        MOTION
    }

    public enum BlockPriority {
        ANY,
        OBSIDIAN_ONLY,
        E_CHEST_ONLY,
        PREFER_OBSIDIAN,
        PREFER_E_CHEST;

        boolean matches(Block block) {
            return switch (this) {
                case ANY -> BLOCKS.contains(block);
                case OBSIDIAN_ONLY, PREFER_OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
                case E_CHEST_ONLY, PREFER_E_CHEST -> block == Blocks.ENDER_CHEST;
            };
        }

        boolean fallsBack() {
            return this == PREFER_OBSIDIAN || this == PREFER_E_CHEST;
        }
    }

    public enum RenderMode {
        BOX,
        OUTLINE,
        BOTH,
        SLAB
    }
}
