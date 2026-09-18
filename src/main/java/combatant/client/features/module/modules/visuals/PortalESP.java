/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;
import combatant.client.features.module.WorldPhase;

import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@ModuleInfo(
        id = "portalesp",
        displayName = "PortalESP",
        category = ModuleCategory.VISUALS,
        description = "Automatically detects and highlights Nether and End portals in loaded chunks."
)
public final class PortalESP extends Module {

    private static final Predicate<BlockState> PORTAL_PREDICATE = state ->
            state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);

    private final NumberValue<Integer> range =
            num("range", 64, 8, 128);
    private final RGBAColorValue netherColor =
            color("nether_color", "#809900FF");
    private final RGBAColorValue endColor =
            color("end_color", "#8000FFB2");

    private final Minecraft mc = Minecraft.getInstance();
    private final List<PortalEntry> portals = new ArrayList<>();
    private int scanTicks = 0;

    @Override
    public void onDisable() {
        portals.clear();
        scanTicks = 0;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        scanTicks++;
        if (scanTicks % 15 == 0 || portals.isEmpty()) {
            scanPortals();
        }

        Renderer3D target = depthRenderer != null ? depthRenderer : renderer;
        for (PortalEntry entry : portals) {
            if (!Renderer3D.Culling.isInFrustum(entry.box())) {
                continue;
            }
            drawPortalBox(target, entry.box(), entry.argb());
        }
    }

    private void scanPortals() {
        portals.clear();
        if (mc.level == null || mc.player == null) return;

        int r = range.get();
        double rSq = (double) r * r;
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(r / 16.0);

        int netherArgb = netherColor.getArgb();
        int endArgb = endColor.getArgb();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }

                LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(PORTAL_PREDICATE)) {
                        continue;
                    }

                    int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                    int baseX = SectionPos.sectionToBlockCoord(cx);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    int baseZ = SectionPos.sectionToBlockCoord(cz);

                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (!mc.level.isInsideBuildHeight(worldY)) continue;

                        for (int z = 0; z < 16; z++) {
                            int worldZ = baseZ + z;
                            for (int x = 0; x < 16; x++) {
                                int worldX = baseX + x;
                                double dx = (worldX + 0.5) - playerX;
                                double dy = (worldY + 0.5) - playerY;
                                double dz = (worldZ + 0.5) - playerZ;
                                if (dx * dx + dy * dy + dz * dz > rSq) {
                                    continue;
                                }

                                BlockState state = section.getBlockState(x, y, z);
                                if (state.is(Blocks.NETHER_PORTAL)) {
                                    cursor.set(worldX, worldY, worldZ);
                                    portals.add(new PortalEntry(new AABB(cursor), netherArgb));
                                } else if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
                                    cursor.set(worldX, worldY, worldZ);
                                    portals.add(new PortalEntry(new AABB(cursor), endArgb));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void drawPortalBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int fillA = Math.min(a, 65);
        int lineA = Math.min(255, a + 50);

        // Filled faces
        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);

        // Outline
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

    private record PortalEntry(AABB box, int argb) {}

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
