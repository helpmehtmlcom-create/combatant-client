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
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "bedrockholeesp",
        displayName = "Bedrock Hole ESP",
        description = "Detects and highlights holes in Nether roof and bedrock floor for finding secret bases and void portals.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"bedrockesp", "roofholes", "voidholes"}
)
public class BedrockHoleESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> chunksPerTick =
            num("bedrock_chunks_tick", "chunks_per_tick", 10, 1, 100);
    private final NumberValue<Integer> minSize =
            num("bedrock_min_size", "min_size", 1, 1, 50);
    private final NumberValue<Integer> maxSize =
            num("bedrock_max_size", "max_size", 200, 1, 512);

    private final BooleanValue includeNetherRoof =
            bool("bedrock_nether_roof", "include_nether_roof", true);
    private final BooleanValue includeFloor =
            bool("bedrock_floor", "include_floor", true);

    private final BooleanValue fill =
            bool("bedrock_fill", "fill", true);
    private final BooleanValue outline =
            bool("bedrock_outline", "outline", true);

    private final RGBAColorValue lineColor =
            color("bedrock_line_color", "#FFFFFF00"); // Yellow
    private final RGBAColorValue sideColor =
            color("bedrock_side_color", "#55FFFF00"); // Translucent Yellow

    private final Map<BlockPos, AABB> detectedHoles = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = Collections.synchronizedSet(new HashSet<>());
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    private void clear() {
        detectedHoles.clear();
        scannedChunks.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 5;
        int processedChunks = 0;
        int maxChunks = chunksPerTick.get();

        boolean isNether = mc.level.dimensionType().hasCeiling();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                if (processedChunks >= maxChunks) return;

                ChunkPos cPos = new ChunkPos(cx, cz);
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                processedChunks++;
                scanChunkBedrock(chunk, cPos, isNether);
            }
        }
    }

    private void scanChunkBedrock(LevelChunk chunk, ChunkPos cPos, boolean isNether) {
        int baseX = SectionPos.sectionToBlockCoord(cPos.x());
        int baseZ = SectionPos.sectionToBlockCoord(cPos.z());

        // 1. Nether Roof Bedrock Check (Y = 124 to 127)
        if (isNether && includeNetherRoof.get()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = baseX + x;
                    int worldZ = baseZ + z;
                    BlockPos p = new BlockPos(worldX, 127, worldZ);
                    BlockState s = chunk.getBlockState(p);

                    // If bedrock at Y=127 is missing, check if it's a hole through the roof
                    if (!s.is(Blocks.BEDROCK)) {
                        int holeDepth = 0;
                        for (int y = 127; y >= 123; y--) {
                            if (!chunk.getBlockState(new BlockPos(worldX, y, worldZ)).is(Blocks.BEDROCK)) {
                                holeDepth++;
                            }
                        }
                        if (holeDepth >= minSize.get() && holeDepth <= maxSize.get()) {
                            detectedHoles.put(p, new AABB(worldX, 127 - holeDepth + 1, worldZ, worldX + 1, 128, worldZ + 1));
                        }
                    }
                }
            }
        }

        // 2. Bedrock Floor Check (Y = 0 to 4 in Nether, or -64 to -60 in Overworld)
        if (includeFloor.get()) {
            int floorBaseY = isNether ? 0 : -64;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = baseX + x;
                    int worldZ = baseZ + z;
                    BlockPos p = new BlockPos(worldX, floorBaseY, worldZ);
                    BlockState s = chunk.getBlockState(p);

                    // If the lowest bedrock layer is missing, hole directly into the void!
                    if (!s.is(Blocks.BEDROCK)) {
                        detectedHoles.put(p, new AABB(worldX, floorBaseY, worldZ, worldX + 1, floorBaseY + 1, worldZ + 1));
                    }
                }
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || detectedHoles.isEmpty()) return;

        int fillArgb = sideColor.getArgb();
        int fr = (fillArgb >>> 16) & 0xFF;
        int fg = (fillArgb >>> 8) & 0xFF;
        int fb = fillArgb & 0xFF;
        int fa = (fillArgb >>> 24) & 0xFF;

        int lineArgb = lineColor.getArgb();
        int lr = (lineArgb >>> 16) & 0xFF;
        int lg = (lineArgb >>> 8) & 0xFF;
        int lb = lineArgb & 0xFF;
        int la = (lineArgb >>> 24) & 0xFF;

        for (AABB box : detectedHoles.values()) {
            if (box == null) continue;

            if (fill.get()) {
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, fr, fg, fb, fa);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, fr, fg, fb, fa);
            }

            if (outline.get()) {
                renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, lr, lg, lb, la);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, lr, lg, lb, la);

                renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, lr, lg, lb, la);
                renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, lr, lg, lb, la);

                renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, lr, lg, lb, la);
                renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, lr, lg, lb, la);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, lr, lg, lb, la);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
