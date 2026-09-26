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
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "antibed",
        displayName = "AntiBed",
        aliases = {"BedShield", "AntiBedBomb"},
        category = ModuleCategory.COMBAT,
        description = "Prevents and mitigates bed bomb explosions in Nether and End dimensions."
)
public final class AntiBed extends Module {

    private static final Set<Block> COVER_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.COBBLESTONE,
            Blocks.STONE,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("antiBedRange", "range", CommonSettingSchemas.COMBAT_RANGE, 5.0, 2.0, 8.0);

    private final BooleanValue placeAbove =
            bool("antiBedPlaceAbove", "place_above", true);

    private final BooleanValue autoDisable =
            bool("antiBedAutoDisable", "auto_disable", false);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("antiBedRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final NumberValue<Integer> delay =
            num("antiBedDelay", "delay", 0, 0, 10);

    private final BooleanValue render =
            bool("antiBedRender", "render", true);

    private final RGBAColorValue fillColor =
            color("antiBedFillColor", "fill_color", "#FF005544");

    private final RGBAColorValue lineColor =
            color("antiBedLineColor", "line_color", "#FF3377FF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private final List<BlockPos> detectedBeds = new ArrayList<>();

    public AntiBed() {
        this.blockPlacer = new BlockPlacer(
                this,
                this,
                10,
                this::findPlacementSlot,
                range::get,
                () -> 0.0,
                delay::get,
                delay::get,
                () -> 0,
                () -> 0,
                () -> 1,
                () -> false,
                () -> true,
                () -> false,
                rotation::get,
                () -> MovementCorrection.SILENT
        );
    }

    @Override
    public void onEnable() {
        blockPlacer.enable();
        updateAntiBedTargets();
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        currentTargets.clear();
        detectedBeds.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        if (!isExplosiveBedDimension(mc.level)) {
            currentTargets.clear();
            detectedBeds.clear();
            blockPlacer.clear();
            return;
        }

        blockPlacer.tick();
        updateAntiBedTargets();

        if (autoDisable.get() && currentTargets.isEmpty() && !detectedBeds.isEmpty()) {
            setEnabled(false);
        }
    }

    private void updateAntiBedTargets() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            currentTargets.clear();
            detectedBeds.clear();
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double maxDistSq = range.get() * range.get();

        List<BlockPos> beds = new ArrayList<>();
        List<BlockPos> coverPositions = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos candidate = playerPos.offset(x, y, z);
                    if (player.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5) > maxDistSq) {
                        continue;
                    }

                    BlockState state = level.getBlockState(candidate);
                    if (state.getBlock() instanceof BedBlock) {
                        beds.add(candidate);

                        if (placeAbove.get()) {
                            BlockPos above = candidate.above();
                            if (level.getBlockState(above).canBeReplaced()) {
                                coverPositions.add(above);
                            }
                        }
                    }
                }
            }
        }

        detectedBeds.clear();
        detectedBeds.addAll(beds);

        currentTargets.clear();
        currentTargets.addAll(coverPositions);
        blockPlacer.update(coverPositions);
    }

    private boolean isExplosiveBedDimension(ClientLevel level) {
        return level != null && (level.dimension() == Level.NETHER || level.dimension() == Level.END);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos targetPos) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && isCoverBlock(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isCoverBlock(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isCoverBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return COVER_BLOCKS.contains(blockItem.getBlock()) || blockItem.getBlock().defaultBlockState().isSolid();
        }
        return false;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null || detectedBeds.isEmpty()) return;

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        for (BlockPos pos : detectedBeds) {
            AABB box = new AABB(pos);
            addFilledBox(renderer, box, fillArgb);
            addOutlineBox(renderer, box, lineArgb);
        }
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }
}
