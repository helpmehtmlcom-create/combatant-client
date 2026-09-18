/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "holeesp",
        displayName = "Hole ESP",
        description = "Detects artificial 1x1 shafts and 3x1 stairs dug down by players to hide underground bases.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"shaftesp", "mineshaftfinder", "dropesp"}
)
public class HoleESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final ModeValue detectionMode =
            modeSetting("hole_mode", "detection_mode", "All", "All", "Holes", "3x1 Holes");
    private final NumberValue<Integer> chunksPerTick =
            num("hole_chunks_per_tick", "chunks_per_tick", 10, 1, 100);
    private final NumberValue<Integer> minHoleDepth =
            num("hole_min_depth", "min_depth", 4, 2, 30);
    private final NumberValue<Integer> minHeight =
            num("hole_min_y", "min_y_offset", -64, -64, 319);
    private final NumberValue<Integer> maxHeight =
            num("hole_max_y", "max_y_offset", 128, -64, 319);

    private final BooleanValue renderFill =
            bool("hole_fill", "fill", true);
    private final BooleanValue renderOutline =
            bool("hole_outline", "outline", true);

    private final RGBAColorValue hole1x1Color =
            color("hole_1x1_color", "#FFFF0000"); // Red
    private final RGBAColorValue hole3x1Color =
            color("hole_3x1_color", "#FFFFA500"); // Orange

    public record DiscoveredHole(BlockPos topPos, int depth, boolean is3x1, AABB box) {
    }

    private final Map<BlockPos, DiscoveredHole> detectedHoles = new ConcurrentHashMap<>();
    private final List<ChunkCoord> chunkQueue = new ArrayList<>();
    private int chunkCursor = 0;

    public record ChunkCoord(int x, int z) {
    }

    @Override
    public void onEnable() {
        detectedHoles.clear();
        chunkQueue.clear();
        chunkCursor = 0;
    }

    @Override
    public void onDisable() {
        detectedHoles.clear();
        chunkQueue.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 6;

        if (chunkQueue.isEmpty() || chunkCursor >= chunkQueue.size()) {
            chunkQueue.clear();
            for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    chunkQueue.add(new ChunkCoord(cx, cz));
                }
            }
            chunkCursor = 0;
        }

        int budget = chunksPerTick.get();
        int minY = minHeight.get();
        int maxY = maxHeight.get();
        int requiredDepth = minHoleDepth.get();
        String mode = detectionMode.get();

        while (budget > 0 && chunkCursor < chunkQueue.size()) {
            ChunkCoord coord = chunkQueue.get(chunkCursor++);
            budget--;

            LevelChunk chunk = mc.level.getChunkSource().getChunk(coord.x, coord.z, false);
            if (chunk == null || chunk.isEmpty()) continue;

            int baseX = coord.x << 4;
            int baseZ = coord.z << 4;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = baseX + x;
                    int worldZ = baseZ + z;

                    // 1x1 Hole check
                    if (mode.equals("All") || mode.equals("Holes")) {
                        check1x1Column(worldX, worldZ, minY, maxY, requiredDepth);
                    }

                    // 3x1 Hole check (only every 3 blocks along X or Z to avoid overlapping)
                    if ((mode.equals("All") || mode.equals("3x1 Holes")) && (x % 3 == 0 || z % 3 == 0)) {
                        check3x1Area(worldX, worldZ, minY, maxY, requiredDepth);
                    }
                }
            }
        }
    }

    private void check1x1Column(int x, int z, int minY, int maxY, int requiredDepth) {
        int currentAirRun = 0;
        int runTopY = maxY;

        for (int y = maxY; y >= minY; y--) {
            BlockState state = mc.level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) {
                if (currentAirRun == 0) {
                    runTopY = y;
                }
                currentAirRun++;
            } else {
                if (currentAirRun >= requiredDepth && isSurroundedBySolid(x, z, runTopY, currentAirRun)) {
                    BlockPos top = new BlockPos(x, runTopY, z);
                    AABB box = new AABB(x, runTopY - currentAirRun + 1, z, x + 1, runTopY + 1, z + 1);
                    detectedHoles.put(top, new DiscoveredHole(top, currentAirRun, false, box));
                }
                currentAirRun = 0;
            }
        }

        if (currentAirRun >= requiredDepth && isSurroundedBySolid(x, z, runTopY, currentAirRun)) {
            BlockPos top = new BlockPos(x, runTopY, z);
            AABB box = new AABB(x, runTopY - currentAirRun + 1, z, x + 1, runTopY + 1, z + 1);
            detectedHoles.put(top, new DiscoveredHole(top, currentAirRun, false, box));
        }
    }

    private boolean isSurroundedBySolid(int x, int z, int topY, int depth) {
        // Sample middle of the run to verify walls are solid
        int midY = topY - (depth / 2);
        return !mc.level.getBlockState(new BlockPos(x + 1, midY, z)).isAir()
                && !mc.level.getBlockState(new BlockPos(x - 1, midY, z)).isAir()
                && !mc.level.getBlockState(new BlockPos(x, midY, z + 1)).isAir()
                && !mc.level.getBlockState(new BlockPos(x, midY, z - 1)).isAir();
    }

    private void check3x1Area(int x, int z, int minY, int maxY, int requiredDepth) {
        // Check 3x1 along X axis
        boolean air3x1 = true;
        for (int dx = 0; dx < 3; dx++) {
            if (!mc.level.getBlockState(new BlockPos(x + dx, maxY, z)).isAir()) {
                air3x1 = false;
                break;
            }
        }
        if (air3x1) {
            int depth = 0;
            for (int y = maxY; y >= minY; y--) {
                boolean layerAir = true;
                for (int dx = 0; dx < 3; dx++) {
                    if (!mc.level.getBlockState(new BlockPos(x + dx, y, z)).isAir()) {
                        layerAir = false;
                        break;
                    }
                }
                if (layerAir) {
                    depth++;
                } else {
                    break;
                }
            }
            if (depth >= requiredDepth) {
                BlockPos top = new BlockPos(x, maxY, z);
                AABB box = new AABB(x, maxY - depth + 1, z, x + 3, maxY + 1, z + 1);
                detectedHoles.put(top, new DiscoveredHole(top, depth, true, box));
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || detectedHoles.isEmpty()) return;

        for (DiscoveredHole hole : detectedHoles.values()) {
            AABB box = hole.box();
            if (box == null) continue;

            int argb = hole.is3x1() ? hole3x1Color.getArgb() : hole1x1Color.getArgb();
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;

            if (renderFill.get()) {
                int fillA = 35;
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);
            }

            if (renderOutline.get()) {
                int lineA = 220;
                renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, lineA);

                renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, lineA);

                renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, lineA);
                renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, lineA);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, lineA);
            }
        }
    }
}
