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
        id = "selftrap",
        displayName = "SelfTrap",
        aliases = {"HeadTrap", "TopTrap"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.selftrap.description")
public final class SelfTrap extends Module {

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
    private final EnumValue<Mode> mode = enumSetting("selfTrapMode", "mode", Mode.HEAD, Mode.values());
    private final BooleanValue disableOnJump = bool("selfTrapDisableOnJump", "disable_on_jump", true);
    private final BooleanValue autoDisable = bool("selfTrapAutoDisable", "auto_disable", false);
    private final EnumValue<BlockPlacer.RotationMode> rotation = enumSetting(
            "selfTrapRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "selfTrapMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Double> range = numCommon(
            "selfTrapRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "selfTrapWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Integer> delay = num("selfTrapDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("selfTrapRender", "render", true);
    private final RGBAColorValue fillColor = color("selfTrapFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("selfTrapLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "selfTrapLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

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
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
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
        blockPlacer.tick();
        updateTargets();
        if (autoDisable.get() && currentTargets.isEmpty()) setEnabled(false);
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
        candidates.add(feet.above(2));
        if (mode.get() == Mode.FULL) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(feet.above().relative(direction));
                candidates.add(feet.above(2).relative(direction));
            }
        }

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
        HEAD,
        FULL
    }
}
