/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;
import combatant.client.features.module.WorldPhase;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
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
        description = "Intelligently highlights chests, ender chests, shulker boxes, and furnaces/barrels."
)
public class StorageESP extends Module {

    // Intelligent container color constants
    private static final int COLOR_CHEST = 0xFFFFB300;           // Warm Gold
    private static final int COLOR_ENDER_CHEST = 0xFFAA00FF;     // Vibrant Purple
    private static final int COLOR_SHULKER = 0xFFFF0099;         // Distinct Rose/Magenta
    private static final int COLOR_FURNACE_BARREL = 0xFFCD853F;  // Warm Amber / Bronze

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("range", 64.0, 8.0, 128.0);
    private final BooleanValue chests =
            bool("chests", true);
    private final BooleanValue enderChests =
            bool("ender_chests", true);
    private final BooleanValue shulkers =
            bool("shulkers", true);
    private final BooleanValue furnacesBarrels =
            bool("furnaces_barrels", true);
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

        Renderer3D target = depthRenderer != null ? depthRenderer : renderer;

        // Render Fill and Outline with frustum culling
        for (StorageEntry entry : targets) {
            AABB box = entry.box();
            if (!Renderer3D.Culling.isInFrustum(box)) {
                continue;
            }
            drawStorageBox(target, box, entry.argb());
        }

        // Render Tracers
        if (tracers.get()) {
            Vec3 camPos = mc.gameRenderer.mainCamera().position();
            for (StorageEntry entry : targets) {
                Vec3 center = entry.box().getCenter();
                int argb = entry.argb();
                int a = 180;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                target.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, a);
            }
        }
    }

    private Integer resolveBlockEntityColor(BlockEntity be) {
        if (be instanceof ChestBlockEntity && chests.get()) {
            return COLOR_CHEST;
        }
        if (be instanceof EnderChestBlockEntity && enderChests.get()) {
            return COLOR_ENDER_CHEST;
        }
        if (be instanceof ShulkerBoxBlockEntity && shulkers.get()) {
            return COLOR_SHULKER;
        }
        if ((be instanceof AbstractFurnaceBlockEntity || be instanceof BarrelBlockEntity || be instanceof HopperBlockEntity)
                && furnacesBarrels.get()) {
            return COLOR_FURNACE_BARREL;
        }
        return null;
    }

    private Integer resolveEntityColor(Entity entity) {
        if (entity instanceof MinecartChest && chests.get()) {
            return COLOR_CHEST;
        }
        if (entity instanceof MinecartHopper && furnacesBarrels.get()) {
            return COLOR_FURNACE_BARREL;
        }
        return null;
    }

    private static void drawStorageBox(Renderer3D renderer, AABB box, int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int fillA = 45;
        int lineA = 220;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Bottom & Top faces
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, fillA);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, fillA);

        // Side faces
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, fillA);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, fillA);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, fillA);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, fillA);

        // Bottom outline
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineA);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineA);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, lineA);

        // Top outline
        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, lineA);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, lineA);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, lineA);

        // Verticals
        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, lineA);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, lineA);
    }

    private record StorageEntry(AABB box, int argb) {
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
