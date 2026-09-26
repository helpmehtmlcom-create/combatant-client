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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "autoweb",
        displayName = "AutoWeb",
        aliases = {"FastWeb", "WebEnemy"},
        category = ModuleCategory.COMBAT,
        description = "Places cobwebs at the feet and head of nearby opponents to immobilize them."
)
public final class AutoWeb extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("autoWebRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 1.0, 6.0);

    private final NumberValue<Double> enemyRange =
            num("autoWebEnemyRange", "enemy_range", 5.0, 2.0, 10.0);

    private final BooleanValue placeAtHead =
            bool("autoWebPlaceAtHead", "place_at_head", true);

    private final BooleanValue autoDisable =
            bool("autoWebAutoDisable", "auto_disable", false);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("autoWebRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final NumberValue<Integer> delay =
            num("autoWebDelay", "delay", 0, 0, 10);

    private final BooleanValue render =
            bool("autoWebRender", "render", true);

    private final RGBAColorValue fillColor =
            color("autoWebFillColor", "fill_color", "#FFFFFF44");

    private final RGBAColorValue lineColor =
            color("autoWebLineColor", "line_color", "#CCCCCCFF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();

    public AutoWeb() {
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
        updateWebTargets();
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
        updateWebTargets();

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void updateWebTargets() {
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

        positions.add(enemyFeet);
        if (placeAtHead.get()) {
            positions.add(enemyFeet.above());
        }

        List<BlockPos> needsPlacement = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.canBeReplaced() && !state.is(Blocks.COBWEB)) {
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
        if (!offhand.isEmpty() && isWeb(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isWeb(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isWeb(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() == Blocks.COBWEB;
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
}
