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
import combatant.client.features.relations.PlayerRelations;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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
        id = "autotrap",
        displayName = "AutoTrap",
        aliases = {"Trap", "BoxEnemy"},
        category = ModuleCategory.COMBAT,
        description = "Traps the nearest opponent in a box of blast-resistant blocks."
)
public final class AutoTrap extends Module {

    private static final Set<Block> BLAST_RESISTANT_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("autoTrapRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 1.0, 6.0);

    private final NumberValue<Double> enemyRange =
            num("autoTrapEnemyRange", "enemy_range", 5.0, 2.0, 10.0);

    private final EnumValue<TrapMode> mode =
            enumSetting("autoTrapMode", "mode", TrapMode.FULL, TrapMode.values());

    private final BooleanValue autoDisable =
            bool("autoTrapAutoDisable", "auto_disable", false);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("autoTrapRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final NumberValue<Integer> delay =
            num("autoTrapDelay", "delay", 0, 0, 10);

    private final BooleanValue render =
            bool("autoTrapRender", "render", true);

    private final RGBAColorValue fillColor =
            color("autoTrapFillColor", "fill_color", "#285A9C44");

    private final RGBAColorValue lineColor =
            color("autoTrapLineColor", "line_color", "#55AAFFFF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();

    public AutoTrap() {
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
        updateTrapTargets();
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
        updateTrapTargets();

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void updateTrapTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            return;
        }

        Player targetEnemy = findTargetEnemy(player);
        if (targetEnemy == null) {
            currentTargets.clear();
            blockPlacer.update(List.of());
            return;
        }

        BlockPos enemyFeet = targetEnemy.blockPosition();
        List<BlockPos> positions = new ArrayList<>();

        switch (mode.get()) {
            case FULL -> {
                // Feet sides
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    positions.add(enemyFeet.relative(dir));
                }
                // Head sides
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    positions.add(enemyFeet.above(1).relative(dir));
                }
                // Top ceiling
                positions.add(enemyFeet.above(2));
            }
            case TOP -> {
                // Top ceiling + one support side if air
                positions.add(enemyFeet.above(2));
                positions.add(enemyFeet.above(1).north());
            }
            case SIDES -> {
                // Head sides
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    positions.add(enemyFeet.above(1).relative(dir));
                }
            }
        }

        List<BlockPos> needsPlacement = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.canBeReplaced()) {
                needsPlacement.add(pos);
            }
        }

        currentTargets.clear();
        currentTargets.addAll(needsPlacement);
        blockPlacer.update(needsPlacement);
    }

    private Player findTargetEnemy(LocalPlayer player) {
        if (mc.level == null) return null;

        Player closest = null;
        double closestDistSq = enemyRange.get() * enemyRange.get();

        for (Player p : mc.level.players()) {
            if (p.equals(player) || p.isSpectator() || p.isCreative()) continue;
            String name = p.getName().getString();
            if (PlayerRelations.get().getFriends().contains(name)) continue;

            double distSq = player.distanceToSqr(p);
            if (distSq < closestDistSq) {
                closest = p;
                closestDistSq = distSq;
            }
        }

        return closest;
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

    public enum TrapMode {
        FULL,
        TOP,
        SIDES
    }
}
