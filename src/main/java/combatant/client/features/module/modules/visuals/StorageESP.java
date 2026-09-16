/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "storageesp",
        displayName = "StorageESP",
        category = ModuleCategory.VISUALS,
        aliases = {"chestesp"},
        description = "Highlights chests, shulker boxes, barrels, and hoppers with customizable color themes."
)
public class StorageESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("range", 64.0, 8.0, 128.0);

    private final BooleanValue chests =
            bool("chests", true);
    private final RGBAColorValue chestColor =
            visibleWhen(color("chestColor", "#FFFF9900"), chests::get);

    private final BooleanValue enderChests =
            bool("enderChests", true);
    private final RGBAColorValue enderChestColor =
            visibleWhen(color("enderChestColor", "#FF9900FF"), enderChests::get);

    private final BooleanValue shulkers =
            bool("shulkers", true);
    private final RGBAColorValue shulkerColor =
            visibleWhen(color("shulkerColor", "#FFFF0099"), shulkers::get);

    private final BooleanValue barrels =
            bool("barrels", true);
    private final RGBAColorValue barrelColor =
            visibleWhen(color("barrelColor", "#FF996633"), barrels::get);

    private final BooleanValue furnaces =
            bool("furnaces", true);
    private final RGBAColorValue furnaceColor =
            visibleWhen(color("furnaceColor", "#FF777777"), furnaces::get);

    private final BooleanValue hoppers =
            bool("hoppers", true);
    private final RGBAColorValue hopperColor =
            visibleWhen(color("hopperColor", "#FF555555"), hoppers::get);

    private final BooleanValue fill =
            bool("fill", true);
    private final NumberValue<Integer> fillAlpha =
            visibleWhen(num("fillAlpha", 40, 0, 255), fill::get);

    private final BooleanValue outline =
            bool("outline", true);

    private final BooleanValue tracers =
            bool("tracers", false);

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        double maxDist = range.get();
        double maxDistSq = maxDist * maxDist;
        Vec3 playerPos = mc.player.position();

        List<StorageEntry> targets = new ArrayList<>();

        // Collect BlockEntities in loaded chunks within range
        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDist / 16.0);

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity == null) {
                        continue;
                    }

                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z) > maxDistSq) {
                        continue;
                    }

                    Integer color = resolveBlockEntityColor(blockEntity);
                    if (color != null) {
                        BlockState state = mc.level.getBlockState(pos);
                        VoxelShape shape = state.getShape(mc.level, pos);
                        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
                        targets.add(new StorageEntry(box, color));
                    }
                }
            }
        }

        // Collect container entities (e.g. ChestMinecart, HopperMinecart)
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity.distanceToSqr(mc.player) > maxDistSq) {
                continue;
            }

            Integer color = resolveEntityColor(entity);
            if (color != null) {
                targets.add(new StorageEntry(entity.getBoundingBox(), color));
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        // Render Fill and Outline
        boolean doFill = fill.get();
        boolean doOutline = outline.get();
        int customFillAlpha = fillAlpha.get();

        for (StorageEntry entry : targets) {
            AABB box = entry.box();
            int baseColor = entry.argb();

            if (doFill) {
                int fillColor = (baseColor & 0x00FFFFFF) | ((customFillAlpha & 0xFF) << 24);
                drawFilledBox(renderer, box, fillColor);
            }

            if (doOutline) {
                drawOutlineBox(renderer, box, baseColor);
            }
        }

        // Render Tracers
        if (tracers.get()) {
            Vec3 camPos = mc.gameRenderer.mainCamera().position();
            for (StorageEntry entry : targets) {
                Vec3 center = entry.box().getCenter();
                int argb = entry.argb();
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                renderer.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, a);
            }
        }
    }

    private Integer resolveBlockEntityColor(BlockEntity be) {
        if (be instanceof ChestBlockEntity && chests.get()) {
            return chestColor.getArgb();
        }
        if (be instanceof EnderChestBlockEntity && enderChests.get()) {
            return enderChestColor.getArgb();
        }
        if (be instanceof ShulkerBoxBlockEntity && shulkers.get()) {
            return shulkerColor.getArgb();
        }
        if (be instanceof BarrelBlockEntity && barrels.get()) {
            return barrelColor.getArgb();
        }
        if (be instanceof AbstractFurnaceBlockEntity && furnaces.get()) {
            return furnaceColor.getArgb();
        }
        if (be instanceof HopperBlockEntity && hoppers.get()) {
            return hopperColor.getArgb();
        }
        return null;
    }

    private Integer resolveEntityColor(Entity entity) {
        if (entity instanceof MinecartChest && chests.get()) {
            return chestColor.getArgb();
        }
        if (entity instanceof MinecartHopper && hoppers.get()) {
            return hopperColor.getArgb();
        }
        return null;
    }

    private static void drawFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom & Top
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);

        // Sides
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void drawOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top
        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Verticals
        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private record StorageEntry(AABB box, int argb) {
    }
}
