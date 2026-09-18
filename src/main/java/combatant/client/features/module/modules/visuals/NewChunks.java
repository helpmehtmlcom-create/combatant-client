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
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ModuleInfo(
        id = "newchunks",
        displayName = "NewChunks",
        aliases = {"chunkdetect", "basefinder"},
        category = ModuleCategory.VISUALS,
        description = "Accurately detects and highlights newly generated world chunks vs old chunks."
)
public final class NewChunks extends Module {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double MAX_RENDER_DISTANCE_SQ = 512.0 * 512.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue newChunkColor =
            color("new_chunk_color", "#80FF0000");
    private final RGBAColorValue oldChunkColor =
            color("old_chunk_color", "#800066FF");
    private final BooleanValue renderOld =
            bool("render_old", false);
    private final NumberValue<Double> renderY =
            num("render_y", 0.0, -64.0, 320.0);

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

        if (packet instanceof ClientboundRespawnPacket) {
            newChunks.clear();
            oldChunks.clear();
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
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
            // Flowing liquid update occurs immediately upon generation when liquids calculate flow
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
        Renderer3D target = depthRenderer != null ? depthRenderer : renderer;

        int newColor = newChunkColor.getArgb();
        synchronized (newChunks) {
            for (ChunkPos chunk : newChunks) {
                if (isWithinRenderDistance(chunk, playerX, playerZ)) {
                    renderChunkGrid(target, chunk, newColor);
                }
            }
        }

        if (renderOld.get()) {
            int oldColor = oldChunkColor.getArgb();
            synchronized (oldChunks) {
                for (ChunkPos chunk : oldChunks) {
                    if (!newChunks.contains(chunk) && isWithinRenderDistance(chunk, playerX, playerZ)) {
                        renderChunkGrid(target, chunk, oldColor);
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

    private void renderChunkGrid(Renderer3D renderer, ChunkPos chunk, int argb) {
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
        double maxY = minY + 1.0;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        if (!Renderer3D.Culling.isInFrustum(box)) {
            return;
        }

        int fillAlpha = Math.min(a, 35);
        int lineAlpha = Math.min(255, a + 40);

        // Horizontal bottom grid plane
        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, fillAlpha);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, fillAlpha);

        // Clean border grid outline
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, lineAlpha);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineAlpha);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineAlpha);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, lineAlpha);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
