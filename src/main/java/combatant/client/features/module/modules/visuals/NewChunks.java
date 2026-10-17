/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;
import combatant.client.features.module.WorldPhase;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
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
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "newchunks",
        displayName = "NewChunks",
        aliases = {"chunkdetect", "basefinder"},
        category = ModuleCategory.VISUALS,
        description = "Accurately detects and highlights newly generated world chunks vs old chunks."
)
public final class NewChunks extends Module {

    private static final Direction[] DIRECTIONS = Direction.values();

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue newChunkColor =
            color("new_chunk_color", "#80FF0000");
    private final RGBAColorValue oldChunkColor =
            color("old_chunk_color", "#800066FF");
    private final BooleanValue renderNew =
            bool("render_new", true);
    private final BooleanValue renderOld =
            bool("render_old", true);
    private final BooleanValue throughWalls =
            bool("through_walls", true);
    private final BooleanValue followPlayerY =
            bool("follow_player_y", true);
    private final NumberValue<Double> renderY =
            num("render_y", 64.0, -64.0, 320.0);
    private final ModeValue renderMode =
            mode("render_mode", "Plane", "Plane", "Column", "Outline");
    private final NumberValue<Double> columnHeight =
            num("column_height", 16.0, 1.0, 128.0);
    private final ModeValue detectionMode =
            mode("detection_mode", "Dynamic", "Dynamic", "LiquidFlow", "AllNew", "AllOld");
    private final NumberValue<Double> maxDistance =
            num("max_distance", 512.0, 64.0, 2048.0);

    private final Set<ChunkPos> newChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> oldChunks = ConcurrentHashMap.newKeySet();

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
            sectionPacket.runUpdates(this::handleBlockUpdate);
        } else if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            handleBlockUpdate(blockPacket.getPos(), blockPacket.getBlockState());
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            handleChunkPacket(chunkPacket);
        }
    }

    private void handleChunkPacket(ClientboundLevelChunkWithLightPacket chunkPacket) {
        ChunkPos chunkPos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
        String mode = detectionMode.get();

        if ("AllNew".equalsIgnoreCase(mode)) {
            newChunks.add(chunkPos);
            oldChunks.remove(chunkPos);
            return;
        }

        if ("AllOld".equalsIgnoreCase(mode)) {
            oldChunks.add(chunkPos);
            newChunks.remove(chunkPos);
            return;
        }

        if ("Dynamic".equalsIgnoreCase(mode)) {
            boolean isNew = false;
            ClientboundLightUpdatePacketData lightData = chunkPacket.getLightData();
            if (lightData != null) {
                // When chunks are freshly generated, block light updates or empty masks reflect generation state
                var blockMask = lightData.getBlockYMask();
                var blockUpdates = lightData.getBlockUpdates();
                if (blockUpdates != null && !blockUpdates.isEmpty() && blockMask != null && !blockMask.isEmpty()) {
                    isNew = true;
                }
            }

            // Check if level already had this chunk cached or if it's currently unvisited
            if (!isNew && mc.level != null) {
                LevelChunk existing = mc.level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), false);
                if (existing == null && !oldChunks.contains(chunkPos)) {
                    isNew = true;
                }
            }

            if (isNew) {
                newChunks.add(chunkPos);
                oldChunks.remove(chunkPos);
            } else if (!newChunks.contains(chunkPos)) {
                oldChunks.add(chunkPos);
            }
            return;
        }

        // LiquidFlow mode: chunks default to oldChunks unless liquid flow is detected
        if (!newChunks.contains(chunkPos)) {
            oldChunks.add(chunkPos);
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
        Renderer3D target = throughWalls.get() ? renderer : (depthRenderer != null ? depthRenderer : renderer);

        double maxDistSq = maxDistance.get() * maxDistance.get();

        if (renderNew.get()) {
            int newColor = newChunkColor.getArgb();
            for (ChunkPos chunk : newChunks) {
                if (isWithinRenderDistance(chunk, playerX, playerZ, maxDistSq)) {
                    renderChunk(target, chunk, newColor);
                }
            }
        }

        if (renderOld.get()) {
            int oldColor = oldChunkColor.getArgb();
            for (ChunkPos chunk : oldChunks) {
                if (!newChunks.contains(chunk) && isWithinRenderDistance(chunk, playerX, playerZ, maxDistSq)) {
                    renderChunk(target, chunk, oldColor);
                }
            }
        }
    }

    private boolean isWithinRenderDistance(ChunkPos chunk, double playerX, double playerZ, double maxDistSq) {
        double centerX = chunk.getMiddleBlockX();
        double centerZ = chunk.getMiddleBlockZ();
        double dx = centerX - playerX;
        double dz = centerZ - playerZ;
        return (dx * dx + dz * dz) <= maxDistSq;
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

        // Full vertical chunk column for frustum visibility so chunk highlights don't vanish when looking horizontally
        double worldMinY = (mc.level != null) ? mc.level.getMinY() : -64.0;
        double worldMaxY = (mc.level != null) ? mc.level.getMaxY() : 320.0;
        AABB cullingBox = new AABB(minX, worldMinY, minZ, maxX, worldMaxY, maxZ);
        if (!Renderer3D.Culling.isInFrustum(cullingBox)) {
            return;
        }

        double baseY = (followPlayerY.get() && mc.player != null) ? Math.floor(mc.player.getY()) : renderY.get();
        String mode = renderMode.get();

        int fillAlpha = Math.min(a, 35);
        int lineAlpha = Math.min(255, a + 40);

        if ("Column".equalsIgnoreCase(mode)) {
            double topY = baseY + columnHeight.get();

            // 4 vertical corner lines
            renderer.line(minX, baseY, minZ, minX, topY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, minZ, maxX, topY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, maxZ, maxX, topY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, baseY, maxZ, minX, topY, maxZ, r, g, b, lineAlpha);

            // Bottom border outline lines
            renderer.line(minX, baseY, minZ, maxX, baseY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, minZ, maxX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, maxZ, minX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, baseY, maxZ, minX, baseY, minZ, r, g, b, lineAlpha);

            // Top border outline lines
            renderer.line(minX, topY, minZ, maxX, topY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, topY, minZ, maxX, topY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, topY, maxZ, minX, topY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, topY, maxZ, minX, topY, minZ, r, g, b, lineAlpha);

            // Side translucent quads (double sided)
            // North face (minZ)
            renderer.quad(minX, baseY, minZ, maxX, baseY, minZ, maxX, topY, minZ, minX, topY, minZ, r, g, b, fillAlpha);
            renderer.quad(minX, topY, minZ, maxX, topY, minZ, maxX, baseY, minZ, minX, baseY, minZ, r, g, b, fillAlpha);

            // South face (maxZ)
            renderer.quad(maxX, baseY, maxZ, minX, baseY, maxZ, minX, topY, maxZ, maxX, topY, maxZ, r, g, b, fillAlpha);
            renderer.quad(maxX, topY, maxZ, minX, topY, maxZ, minX, baseY, maxZ, maxX, baseY, maxZ, r, g, b, fillAlpha);

            // West face (minX)
            renderer.quad(minX, baseY, maxZ, minX, baseY, minZ, minX, topY, minZ, minX, topY, maxZ, r, g, b, fillAlpha);
            renderer.quad(minX, topY, maxZ, minX, topY, minZ, minX, baseY, minZ, minX, baseY, maxZ, r, g, b, fillAlpha);

            // East face (maxX)
            renderer.quad(maxX, baseY, minZ, maxX, baseY, maxZ, maxX, topY, maxZ, maxX, topY, minZ, r, g, b, fillAlpha);
            renderer.quad(maxX, topY, minZ, maxX, topY, maxZ, maxX, baseY, maxZ, maxX, baseY, minZ, r, g, b, fillAlpha);

            // Bottom and top quads
            renderer.quad(minX, baseY, minZ, maxX, baseY, minZ, maxX, baseY, maxZ, minX, baseY, maxZ, r, g, b, fillAlpha);
            renderer.quad(minX, baseY, maxZ, maxX, baseY, maxZ, maxX, baseY, minZ, minX, baseY, minZ, r, g, b, fillAlpha);
            renderer.quad(minX, topY, minZ, maxX, topY, minZ, maxX, topY, maxZ, minX, topY, maxZ, r, g, b, fillAlpha);
            renderer.quad(minX, topY, maxZ, maxX, topY, maxZ, maxX, topY, minZ, minX, topY, minZ, r, g, b, fillAlpha);
        } else if ("Outline".equalsIgnoreCase(mode)) {
            // Clean border outline lines only
            renderer.line(minX, baseY, minZ, maxX, baseY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, minZ, maxX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, maxZ, minX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, baseY, maxZ, minX, baseY, minZ, r, g, b, lineAlpha);
        } else {
            // "Plane" mode: horizontal bottom quad with fillAlpha + border outline lines
            renderer.quad(minX, baseY, minZ, maxX, baseY, minZ, maxX, baseY, maxZ, minX, baseY, maxZ, r, g, b, fillAlpha);
            renderer.quad(minX, baseY, maxZ, maxX, baseY, maxZ, maxX, baseY, minZ, minX, baseY, minZ, r, g, b, fillAlpha);

            // Clean border outline lines
            renderer.line(minX, baseY, minZ, maxX, baseY, minZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, minZ, maxX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(maxX, baseY, maxZ, minX, baseY, maxZ, r, g, b, lineAlpha);
            renderer.line(minX, baseY, maxZ, minX, baseY, minZ, r, g, b, lineAlpha);
        }
    }
    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
