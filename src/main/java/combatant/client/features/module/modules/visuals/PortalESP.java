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
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "portalesp",
        displayName = "PortalESP",
        aliases = {"NetherPortalESP", "EndPortalESP"},
        category = ModuleCategory.VISUALS,
        description = "Renders colored 3D outlines and boxes around Nether and End portals."
)
public final class PortalESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> range =
            num("portalEspRange", "range", 64, 8, 128);

    private final BooleanValue netherPortals =
            bool("portalEspNetherPortals", "nether_portals", true);

    private final BooleanValue endPortals =
            bool("portalEspEndPortals", "end_portals", true);

    private final BooleanValue endGateways =
            bool("portalEspEndGateways", "end_gateways", true);

    private final BooleanValue fill =
            bool("portalEspFill", "fill", true);

    private final BooleanValue outline =
            bool("portalEspOutline", "outline", true);

    private final RGBAColorValue netherColor =
            color("portalEspNetherColor", "nether_color", "#AA00FF44");

    private final RGBAColorValue endColor =
            color("portalEspEndColor", "end_color", "#00FFAA44");

    private final RGBAColorValue gatewayColor =
            color("portalEspGatewayColor", "gateway_color", "#FF00AA44");

    private final List<PortalEntry> targets = new ArrayList<>();

    @Override
    public void onDisable() {
        targets.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            targets.clear();
            return;
        }

        int playerChunkX = player.getBlockX() >> 4;
        int playerChunkZ = player.getBlockZ() >> 4;
        int r = range.get();
        int chunkRadius = Math.min(8, (r + 15) >> 4);
        double maxDistSq = (double) r * r;

        List<PortalEntry> nextTargets = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                // 1. Check End Portals & Gateways via block entities
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > maxDistSq) continue;

                    if (endPortals.get() && be instanceof TheEndPortalBlockEntity) {
                        nextTargets.add(new PortalEntry(pos, endColor.getArgb()));
                    } else if (endGateways.get() && be instanceof TheEndGatewayBlockEntity) {
                        nextTargets.add(new PortalEntry(pos, gatewayColor.getArgb()));
                    }
                }

                // 2. Check Nether Portal blocks via fast palette check on chunk sections
                if (netherPortals.get()) {
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int sIndex = 0; sIndex < sections.length; sIndex++) {
                        LevelChunkSection section = sections[sIndex];
                        if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL))) {
                            continue;
                        }

                        int sectionY = mc.level.getSectionYFromSectionIndex(sIndex);
                        int baseX = SectionPos.sectionToBlockCoord(cx);
                        int baseY = SectionPos.sectionToBlockCoord(sectionY);
                        int baseZ = SectionPos.sectionToBlockCoord(cz);

                        for (int ly = 0; ly < 16; ly++) {
                            int y = baseY + ly;
                            for (int lz = 0; lz < 16; lz++) {
                                int z = baseZ + lz;
                                for (int lx = 0; lx < 16; lx++) {
                                    int x = baseX + lx;
                                    BlockState state = section.getBlockState(lx, ly, lz);
                                    if (state.is(Blocks.NETHER_PORTAL)) {
                                        BlockPos pos = new BlockPos(x, y, z);
                                        double distSq = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                                        if (distSq <= maxDistSq) {
                                            nextTargets.add(new PortalEntry(pos, netherColor.getArgb()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        targets.clear();
        targets.addAll(nextTargets);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || targets.isEmpty()) return;

        boolean doFill = fill.get();
        boolean doOutline = outline.get();

        for (PortalEntry entry : targets) {
            AABB box = new AABB(entry.pos());
            if (doFill) {
                addFilledBox(renderer, box, entry.argb());
            }
            if (doOutline) {
                addOutlineBox(renderer, box, entry.argb() | 0xFF000000);
            }
        }
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

    private record PortalEntry(BlockPos pos, int argb) {
    }
}
