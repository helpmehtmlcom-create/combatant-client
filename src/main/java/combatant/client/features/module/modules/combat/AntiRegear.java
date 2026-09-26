/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

@ModuleInfo(
        id = "antiregear",
        displayName = "AntiRegear",
        aliases = {"ShulkerNuker", "AntiShulker"},
        category = ModuleCategory.COMBAT,
        description = "Automatically breaks enemy shulker boxes to prevent them from regearing."
)
public final class AntiRegear extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("antiRegearRange", "range", CommonSettingSchemas.COMBAT_RANGE, 5.0, 2.0, 7.0);

    private final BooleanValue silentTool =
            bool("antiRegearSilentTool", "silent_tool", true);

    private final BooleanValue autoDisable =
            bool("antiRegearAutoDisable", "auto_disable", false);

    private final BooleanValue render =
            bool("antiRegearRender", "render", true);

    private final RGBAColorValue fillColor =
            color("antiRegearFillColor", "fill_color", "#FF00AA44");

    private final RGBAColorValue lineColor =
            color("antiRegearLineColor", "line_color", "#FF33CCFF");

    private BlockPos targetShulker = null;
    private float breakProgress = 0.0f;
    private boolean isMining = false;

    @Override
    public void onDisable() {
        if (isMining && targetShulker != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    targetShulker,
                    Direction.UP
            ));
        }
        targetShulker = null;
        breakProgress = 0.0f;
        isMining = false;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.getConnection() == null) {
            targetShulker = null;
            return;
        }

        if (targetShulker == null || !(level.getBlockState(targetShulker).getBlock() instanceof ShulkerBoxBlock)) {
            targetShulker = findNearestShulker(player, level);
            breakProgress = 0.0f;
            isMining = false;

            if (targetShulker == null) {
                if (autoDisable.get()) {
                    setEnabled(false);
                }
                return;
            }
        }

        BlockState state = level.getBlockState(targetShulker);
        if (!(state.getBlock() instanceof ShulkerBoxBlock)) {
            targetShulker = null;
            breakProgress = 0.0f;
            isMining = false;
            return;
        }

        if (!isMining) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    targetShulker,
                    Direction.UP
            ));
            isMining = true;
        }

        float delta = state.getDestroyProgress(player, level, targetShulker);
        breakProgress += delta;

        if (breakProgress >= 1.0f) {
            if (silentTool.get()) {
                int toolSlot = findBestTool(state, player);
                if (toolSlot != -1) {
                    InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                }
            }

            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    targetShulker,
                    Direction.UP
            ));
            player.swing(InteractionHand.MAIN_HAND);

            targetShulker = null;
            breakProgress = 0.0f;
            isMining = false;

            if (autoDisable.get()) {
                setEnabled(false);
            }
        }
    }

    private BlockPos findNearestShulker(LocalPlayer player, ClientLevel level) {
        BlockPos playerPos = player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double maxDistSq = range.get() * range.get();

        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos check = playerPos.offset(x, y, z);
                    double distSq = player.distanceToSqr(check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5);
                    if (distSq > maxDistSq) continue;

                    if (level.getBlockState(check).getBlock() instanceof ShulkerBoxBlock) {
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestPos = check;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private int findBestTool(BlockState state, LocalPlayer player) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || targetShulker == null) return;

        AABB box = new AABB(targetShulker);
        addFilledBox(renderer, box, fillColor.getArgb());
        addOutlineBox(renderer, box, lineColor.getArgb());
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
