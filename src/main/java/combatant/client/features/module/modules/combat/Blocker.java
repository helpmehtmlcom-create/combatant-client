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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "blocker",
        displayName = "Blocker",
        aliases = {"BlastShield", "AntiCrystalTrap"},
        category = ModuleCategory.COMBAT,
        description = "Places blast-absorbing blocks between you and nearby crystals or anchors."
)
public final class Blocker extends Module {

    private static final Set<Block> SHIELD_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.COBWEB
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("blockerRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 2.0, 6.0);

    private final NumberValue<Double> crystalRange =
            num("blockerCrystalRange", "crystal_range", 5.0, 2.0, 8.0);

    private final BooleanValue blockCrystals =
            bool("blockerCrystals", "block_crystals", true);

    private final BooleanValue blockAnchors =
            bool("blockerAnchors", "block_anchors", true);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("blockerRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final BooleanValue render =
            bool("blockerRender", "render", true);

    private final RGBAColorValue fillColor =
            color("blockerFillColor", "fill_color", "#FF550044");

    private final RGBAColorValue lineColor =
            color("blockerLineColor", "line_color", "#FFAA00FF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();

    public Blocker() {
        this.blockPlacer = new BlockPlacer(
                this,
                this,
                10,
                this::findPlacementSlot,
                range::get,
                () -> 0.0,
                () -> 0,
                () -> 0,
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
        updateBlockerTargets();
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

        blockPlacer.tick();
        updateBlockerTargets();
    }

    private void updateBlockerTargets() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            currentTargets.clear();
            return;
        }

        List<Vec3> dangerSources = new ArrayList<>();
        double maxDistSq = crystalRange.get() * crystalRange.get();

        // 1. Collect nearby crystals
        if (blockCrystals.get()) {
            AABB area = player.getBoundingBox().inflate(crystalRange.get());
            for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, area, c -> c != null && c.isAlive())) {
                if (player.distanceToSqr(crystal) <= maxDistSq) {
                    dangerSources.add(crystal.position());
                }
            }
        }

        // 2. Collect nearby active respawn anchors
        if (blockAnchors.get()) {
            BlockPos playerPos = player.blockPosition();
            int r = (int) Math.ceil(crystalRange.get());
            for (int x = -r; x <= r; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos check = playerPos.offset(x, y, z);
                        if (level.getBlockState(check).is(Blocks.RESPAWN_ANCHOR)) {
                            dangerSources.add(Vec3.atCenterOf(check));
                        }
                    }
                }
            }
        }

        List<BlockPos> needsPlacement = new ArrayList<>();
        double placeRangeSq = range.get() * range.get();

        for (Vec3 source : dangerSources) {
            Vec3 playerEyes = player.getEyePosition();
            // Calculate midpoint between player and danger source
            Vec3 mid = playerEyes.add(source).scale(0.5);
            BlockPos midPos = BlockPos.containing(mid.x, mid.y, mid.z);

            if (player.distanceToSqr(midPos.getX() + 0.5, midPos.getY() + 0.5, midPos.getZ() + 0.5) <= placeRangeSq) {
                BlockState state = level.getBlockState(midPos);
                if (state.canBeReplaced()) {
                    needsPlacement.add(midPos);
                }
            }
        }

        currentTargets.clear();
        currentTargets.addAll(needsPlacement);
        blockPlacer.update(needsPlacement);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos targetPos) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && isShieldBlock(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isShieldBlock(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isShieldBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return SHIELD_BLOCKS.contains(blockItem.getBlock()) || blockItem.getBlock().defaultBlockState().isSolid();
        }
        return false;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null || currentTargets.isEmpty()) return;

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        for (BlockPos pos : currentTargets) {
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
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, minZ(box), r, g, b, a);
    }

    private static double minZ(AABB box) {
        return box.minZ;
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
