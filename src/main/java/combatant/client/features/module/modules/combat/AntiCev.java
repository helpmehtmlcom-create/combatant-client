/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "anticev",
        displayName = "AntiCev",
        aliases = {"CevDefense", "AntiCeiling"},
        category = ModuleCategory.COMBAT,
        description = "Defends against CEV breaker crystal attacks on your ceiling."
)
public final class AntiCev extends Module {

    private static final Set<Block> BLAST_RESISTANT_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<DefenseMode> mode =
            enumSetting("antiCevMode", "mode", DefenseMode.BOTH, DefenseMode.values());

    private final BooleanValue breakCrystal =
            bool("antiCevBreakCrystal", "break_crystal", true);

    private final BooleanValue placeAbove =
            bool("antiCevPlaceAbove", "place_above", true);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("antiCevRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final BooleanValue render =
            bool("antiCevRender", "render", true);

    private final RGBAColorValue fillColor =
            color("antiCevFillColor", "fill_color", "#FF005544");

    private final RGBAColorValue lineColor =
            color("antiCevLineColor", "line_color", "#FF3300FF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();

    public AntiCev() {
        this.blockPlacer = new BlockPlacer(
                this,
                this,
                10,
                this::findPlacementSlot,
                () -> 4.5,
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
        updateAntiCev();
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
        if (player == null || mc.level == null || mc.gameMode == null) return;

        // 1. Check for crystal above head and attack it
        if (breakCrystal.get() && (mode.get() == DefenseMode.ATTACK || mode.get() == DefenseMode.BOTH)) {
            AABB dangerArea = new AABB(
                    player.getX() - 1.2,
                    player.getY() + 1.8,
                    player.getZ() - 1.2,
                    player.getX() + 1.2,
                    player.getY() + 4.5,
                    player.getZ() + 1.2
            );

            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, dangerArea, c -> c != null && c.isAlive())) {
                mc.gameMode.attack(player, crystal);
                player.swing(InteractionHand.MAIN_HAND);
                break;
            }
        }

        // 2. Defensive block placement
        if (placeAbove.get() && (mode.get() == DefenseMode.COVER || mode.get() == DefenseMode.BOTH)) {
            blockPlacer.tick();
            updateAntiCev();
        }
    }

    private void updateAntiCev() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            return;
        }

        BlockPos feetPos = player.blockPosition();
        BlockPos headPos = feetPos.above(2);
        BlockPos aboveHead = feetPos.above(3);

        List<BlockPos> needsPlacement = new ArrayList<>();

        // If block directly above head is replaceable, cover it
        BlockState headState = mc.level.getBlockState(headPos);
        if (headState.canBeReplaced()) {
            needsPlacement.add(headPos);
        } else if (headState.is(Blocks.OBSIDIAN) || headState.is(Blocks.CRYING_OBSIDIAN)) {
            // Obsidian already above head! Place cover on top of it so they cannot crystal it
            BlockState aboveState = mc.level.getBlockState(aboveHead);
            if (aboveState.canBeReplaced()) {
                needsPlacement.add(aboveHead);
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
        if (!offhand.isEmpty() && isBlastResistant(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isBlastResistant(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isBlastResistant(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return BLAST_RESISTANT_BLOCKS.contains(blockItem.getBlock());
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

    public enum DefenseMode {
        COVER,
        ATTACK,
        BOTH
    }
}
