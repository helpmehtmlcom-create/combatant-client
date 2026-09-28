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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            () -> false,
            () -> true,
            () -> false,
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
        if (center.get() == CenterMode.MOTION) moveTowardCenter(player, 0.4D);
        blockPlacer.tick();
        updateTargets();
        if (autoDisable.get() && currentTargets.isEmpty()) setEnabled(false);
    }

    private void applyCentering(LocalPlayer player) {
        if (center.get() == CenterMode.NONE) return;
        double x = Math.floor(player.getX()) + 0.5D;
        double z = Math.floor(player.getZ()) + 0.5D;
        if (center.get() == CenterMode.MOTION) {
            moveTowardCenter(player, 0.5D);
            return;
        }
        player.setPos(x, player.getY(), z);
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

    private void updateTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            candidates.add(side);
            if (mode.get() == Mode.FULL) candidates.add(side.below());
            if (mode.get() == Mode.ANTI_FACE_PLACE) candidates.add(side.above());
        }
        if (mode.get() == Mode.FULL) candidates.add(feet.below());

        List<BlockPos> needed = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (mc.level.getBlockState(pos).canBeReplaced()) needed.add(pos);
        }
        currentTargets.clear();
        currentTargets.addAll(needed);
        blockPlacer.update(needed);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack offhand = player.getOffhandItem();
        if (isTargetBlock(offhand)) return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isTargetBlock(stack)) return new BlockPlacer.PlacementSlot(slot, InteractionHand.MAIN_HAND, stack);
        }
        return null;
    }

    private static boolean isTargetBlock(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && BLOCKS.contains(blockItem.getBlock());
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || currentTargets.isEmpty()) return;
        for (BlockPos pos : currentTargets) {
            AABB box = new AABB(pos);
            renderer.filledBox(box, fillColor.getArgb());
            renderer.outlineBox(box, lineColor.getArgb(), lineWidth.get());
        }
    }

    public enum Mode {
        FEET,
        FULL,
        ANTI_FACE_PLACE
    }

    public enum CenterMode {
        NONE,
        TELEPORT,
        MOTION
    }
}
