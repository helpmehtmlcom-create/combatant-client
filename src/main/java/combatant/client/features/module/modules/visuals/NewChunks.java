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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ModuleInfo(
        id = "newchunks",
        displayName = "NewChunks",
        aliases = {"chunkdetect", "basefinder"},
        category = ModuleCategory.VISUALS,
        description = "Detects and highlights newly generated world chunks"
)
public final class NewChunks extends Module {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double MAX_RENDER_DISTANCE_SQ = 512.0 * 512.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> renderY =
            num("render_y", "render_y", 0.0, -64.0, 320.0);
    private final RGBAColorValue newChunkColor =
            color("new_chunk_color", "new_chunk_color", "#88FF0000");
    private final RGBAColorValue oldChunkColor =
            color("old_chunk_color", "old_chunk_color", "#880000FF");
    private final BooleanValue renderOld =
            bool("render_old", "render_old", false);
    private final NumberValue<Double> gridHeight =
            num("grid_height", "grid_height", 1.0, 0.0, 16.0);

    private final Set<ChunkPos> newChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<ChunkPos> oldChunks = Collections.synchronizedSet(new HashSet<>());

    public Set<ChunkPos> getNewChunks() {
        return newChunks;
    }

    public Set<ChunkPos> getOldChunks() {
        return oldChunks;
    }

    @Override
    public void onDisable() {
        newChunks.clear();
        oldChunks.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) {
            return;
        }

        Object packet = event.getPacket();

        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            sectionPacket.runUpdates((pos, state) -> handleBlockUpdate(pos, state));
        } else if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            handleBlockUpdate(blockPacket.getPos(), blockPacket.getBlockState());
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            ChunkPos chunkPos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
            if (!newChunks.contains(chunkPos)) {
                oldChunks.add(chunkPos);
            }
        }
    }

    private void handleBlockUpdate(BlockPos pos, BlockState state) {
        if (pos == null || state == null) {
            return;
        }

        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.isSource()) {
            // Unflowing/flowing liquid update occurs immediately upon generation when liquids calculate flow
            ChunkPos chunkPos = ChunkPos.containing(pos);
            newChunks.add(chunkPos);
            oldChunks.remove(chunkPos);
            return;
        }

        // Check if adjacent blocks have fluid source indicating liquid flow calculation
        if (!fluid.isEmpty() && mc.level != null) {
            for (Direction dir : DIRECTIONS) {
                try {
                    FluidState adjacentFluid = mc.level.getBlockState(pos.relative(dir)).getFluidState();
                    if (!adjacentFluid.isEmpty() && adjacentFluid.isSource()) {
                        ChunkPos chunkPos = ChunkPos.containing(pos);
                        newChunks.add(chunkPos);
                        oldChunks.remove(chunkPos);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null) {
            return;
        }

        double playerX = mc.player != null ? mc.player.getX() : 0.0;
        double playerZ = mc.player != null ? mc.player.getZ() : 0.0;

        int newColor = newChunkColor.getArgb();
        synchronized (newChunks) {
            for (ChunkPos chunk : newChunks) {
                if (isWithinRenderDistance(chunk, playerX, playerZ)) {
                    renderChunk(renderer, chunk, newColor);
                }
            }
        }

        if (renderOld.get()) {
            int oldColor = oldChunkColor.getArgb();
            synchronized (oldChunks) {
                for (ChunkPos chunk : oldChunks) {
                    if (!newChunks.contains(chunk) && isWithinRenderDistance(chunk, playerX, playerZ)) {
                        renderChunk(renderer, chunk, oldColor);
                    }
                }
            }
        }
    }

    private boolean isWithinRenderDistance(ChunkPos chunk, double playerX, double playerZ) {
        double centerX = chunk.getMiddleBlockX();
        double centerZ = chunk.getMiddleBlockZ();
        double dx = centerX - playerX;
        double dz = centerZ - playerZ;
        return (dx * dx + dz * dz) <= MAX_RENDER_DISTANCE_SQ;
    }

    private void renderChunk(Renderer3D renderer, ChunkPos chunk, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) {
            return;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = chunk.getMinBlockX();
        double minZ = chunk.getMinBlockZ();
        double maxX = minX + 16.0;
        double maxZ = minZ + 16.0;

        double minY = renderY.get();
        double height = Math.max(0.0, gridHeight.get());
        double maxY = minY + height;

        // Bottom horizontal quad (two-sided)
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);

        if (height > 0.001) {
            // Top horizontal quad (two-sided)
            renderer.quad(minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
            renderer.quad(minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);

            // Side quads with lower alpha
            int sideAlpha = Math.min(a, 40);
            if (sideAlpha > 0) {
                renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, sideAlpha);
                renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, sideAlpha);
                renderer.quad(maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, sideAlpha);
                renderer.quad(minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, sideAlpha);
            }

            // Outline box lines
            int lineAlpha = Math.min(255, a + 40);
            // Bottom outline
            renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, lineAlpha);
            // Top outline
            renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, lineAlpha);
            // Vertical corner pillars
            renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, lineAlpha);
        } else {
            // Flat horizontal outline
            int lineAlpha = Math.min(255, a + 40);
            renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, lineAlpha);
        }
    }
}
