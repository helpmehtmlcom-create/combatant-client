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
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "lightfinder",
        displayName = "Light Finder",
        description = "Scans subterranean chunks for player-placed light sources (torches, lanterns, glowstone) to uncover hidden bases.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"torchfinder", "undergroundlights"}
)
public class LightFinder extends Module {

    private static final Predicate<BlockState> LIGHT_SOURCE_PREDICATE = state ->
            state.is(Blocks.TORCH)
                    || state.is(Blocks.WALL_TORCH)
                    || state.is(Blocks.LANTERN)
                    || state.is(Blocks.SOUL_LANTERN)
                    || state.is(Blocks.GLOWSTONE)
                    || state.is(Blocks.SHROOMLIGHT)
                    || state.is(Blocks.SEA_LANTERN)
                    || state.is(Blocks.REDSTONE_LAMP)
                    || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.OCHRE_FROGLIGHT)
                    || state.is(Blocks.PEARLESCENT_FROGLIGHT)
                    || state.is(Blocks.VERDANT_FROGLIGHT)
                    || state.is(Blocks.END_ROD);

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> rangeChunks =
            num("light_range_chunks", "range_chunks", 3, 1, 8);
    private final NumberValue<Integer> maxY =
            num("light_max_y", "max_y", 60, -64, 320);
    private final BooleanValue renderTracers =
            bool("light_tracers", "tracers", true);
    private final BooleanValue renderBoxes =
            bool("light_boxes", "boxes", true);
    private final RGBAColorValue lightColor =
            color("light_color", "#FFFFD700"); // Bright Gold

    private final Set<BlockPos> discoveredLights = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> notifiedLights = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        discoveredLights.clear();
        notifiedLights.clear();
    }

    @Override
    public void onDisable() {
        discoveredLights.clear();
        notifiedLights.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = rangeChunks.get();
        int maxAllowedY = maxY.get();

        Set<BlockPos> currentScan = new HashSet<>();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(LIGHT_SOURCE_PREDICATE)) {
                        continue;
                    }

                    int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    if (baseY > maxAllowedY) continue;

                    int baseX = SectionPos.sectionToBlockCoord(cx);
                    int baseZ = SectionPos.sectionToBlockCoord(cz);

                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (worldY > maxAllowedY || !mc.level.isInsideBuildHeight(worldY)) continue;

                        for (int z = 0; z < 16; z++) {
                            int worldZ = baseZ + z;
                            for (int x = 0; x < 16; x++) {
                                int worldX = baseX + x;
                                BlockState state = section.getBlockState(x, y, z);
                                if (LIGHT_SOURCE_PREDICATE.test(state)) {
                                    BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                    currentScan.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        discoveredLights.clear();
        discoveredLights.addAll(currentScan);

        for (BlockPos pos : currentScan) {
            if (notifiedLights.add(pos)) {
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    String msg = String.format(
                            "§e[LightFinder] §aFound underground light source at §b[%d, %d, %d] §7(§f%.0fm§7 away)",
                            pos.getX(), pos.getY(), pos.getZ(), dist
                    );
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
                }
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                break; // One alert per tick cycle to avoid chat flooding
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || discoveredLights.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        int argb = lightColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        for (BlockPos pos : discoveredLights) {
            AABB box = new AABB(pos);

            if (renderBoxes.get()) {
                int fillA = 40;
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, fillA);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, fillA);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, fillA);

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

            if (renderTracers.get()) {
                Vec3 center = box.getCenter();
                Vec3 start = RenderState.tracerOrigin();
                renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, r, g, b, 210);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
