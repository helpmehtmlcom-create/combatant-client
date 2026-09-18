/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawner nametags, through-wall bounding boxes, and 16-block activation range rings for DonutSMP.
 * Ported and enhanced from 67Client's SpawnerNametagsModule.
 */
@ModuleInfo(
        id = "spawnernametags",
        displayName = "SpawnerNametags",
        description = "Shows 3D bounding boxes, tracers, and 16-block activation range rings for mob spawners on DonutSMP.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"spawnerinfo", "spawnerring", "spawneresp", "spawners"}
)
public class SpawnerNametags extends Module {

    private static final int RING_SEGMENTS = 36;
    private static final double ACTIVATION_RADIUS = 16.0;

    private final NumberValue<Double> scanRange =
            num("spawnernametags_range", "scan_range", 64.0, 16.0, 128.0);
    private final BooleanValue box =
            bool("spawnernametags_box", "bounding_box", true);
    private final BooleanValue rangeRing =
            bool("spawnernametags_ring", "activation_ring", true);
    private final BooleanValue tracers =
            bool("spawnernametags_tracers", "tracers", false);

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || mc.level == null) return;

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        double maxDist = scanRange.get();
        double maxDistSq = maxDist * maxDist;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDist / 16.0);

        List<BlockPos> spawners = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) {
                        BlockPos pos = be.getBlockPos();
                        if (pos.distToCenterSqr(camera.x, camera.y, camera.z) <= maxDistSq) {
                            spawners.add(pos);
                        }
                    }
                }
            }
        }

        for (BlockPos pos : spawners) {
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();

            // 1. Box rendering (neon violet)
            if (box.get()) {
                drawBox(renderer, x, y, z, 224, 64, 251, 40, 240);
            }

            // 2. Activation Range Ring (16-block radius circle)
            if (rangeRing.get()) {
                drawActivationRing(renderer, x + 0.5, y + 0.5, z + 0.5, ACTIVATION_RADIUS, 255, 170, 0, 200);
            }

            // 3. Tracers
            if (tracers.get()) {
                renderer.line(camera.x, camera.y, camera.z, x + 0.5, y + 0.5, z + 0.5, 224, 64, 251, 180);
            }
        }
    }

    private void drawBox(Renderer3D renderer, double minX, double minY, double minZ, int r, int g, int b, int fillA, int lineA) {
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        // Quads
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, fillA);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, fillA);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, fillA);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, fillA);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, fillA);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, fillA);

        // Lines
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineA);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineA);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, lineA);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, lineA);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, lineA);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, lineA);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, lineA);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, lineA);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, lineA);
    }

    private void drawActivationRing(Renderer3D renderer, double cx, double cy, double cz, double radius, int r, int g, int b, int a) {
        double step = (2.0 * Math.PI) / RING_SEGMENTS;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle1 = i * step;
            double angle2 = (i + 1) * step;

            double x1 = cx + radius * Math.cos(angle1);
            double z1 = cz + radius * Math.sin(angle1);

            double x2 = cx + radius * Math.cos(angle2);
            double z2 = cz + radius * Math.sin(angle2);

            renderer.line(x1, cy, z1, x2, cy, z2, r, g, b, a);
        }
    }
}
