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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "spawnerbeacon",
        displayName = "Spawner Beacon",
        description = "Projects tall vertical beacon beams from mob spawners into the sky for effortless long-distance base finding.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"spawnerbeam", "spawnerpillar"}
)
public class SpawnerBeacon extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue beamColor =
            color("spawner_beacon_color", "#FFBF00FF"); // Neon Purple / Magenta
    private final NumberValue<Double> range =
            num("spawner_beacon_range", "range", 128.0, 32.0, 320.0);
    private final NumberValue<Integer> beamHeight =
            num("spawner_beacon_height", "height", 320, 100, 320);
    private final BooleanValue renderBox =
            bool("spawner_beacon_box", "render_box", true);

    private final Set<BlockPos> spawners = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        spawners.clear();
    }

    @Override
    public void onDisable() {
        spawners.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = (int) Math.ceil(range.get() / 16.0);

        Set<BlockPos> found = new HashSet<>();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) {
                        found.add(be.getBlockPos());
                    }
                }
            }
        }

        spawners.clear();
        spawners.addAll(found);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || spawners.isEmpty()) return;

        int argb = beamColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int maxH = beamHeight.get();

        for (BlockPos pos : spawners) {
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();

            // 1. Spawner Block Highlight
            if (renderBox.get()) {
                renderer.quad(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, r, g, b, 45);
                renderer.quad(x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, r, g, b, 45);
                renderer.quad(x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, 45);
                renderer.quad(x, y, z, x, y + 1, z, x + 1, y + 1, z, x + 1, y, z, r, g, b, 45);
                renderer.quad(x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1, r, g, b, 45);
                renderer.quad(x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, r, g, b, 45);

                renderer.line(x, y, z, x + 1, y, z, r, g, b, 240);
                renderer.line(x + 1, y, z, x + 1, y, z + 1, r, g, b, 240);
                renderer.line(x + 1, y, z + 1, x, y, z + 1, r, g, b, 240);
                renderer.line(x, y, z + 1, x, y, z, r, g, b, 240);

                renderer.line(x, y + 1, z, x + 1, y + 1, z, r, g, b, 240);
                renderer.line(x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, 240);
                renderer.line(x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, 240);
                renderer.line(x, y + 1, z + 1, x, y + 1, z, r, g, b, 240);

                renderer.line(x, y, z, x, y + 1, z, r, g, b, 240);
                renderer.line(x + 1, y, z, x + 1, y + 1, z, r, g, b, 240);
                renderer.line(x + 1, y, z + 1, x + 1, y + 1, z + 1, r, g, b, 240);
                renderer.line(x, y, z + 1, x, y + 1, z + 1, r, g, b, 240);
            }

            // 2. Vertical Beacon Beam (quad pillar)
            double bx = x + 0.2;
            double bz = z + 0.2;
            double bw = 0.6;
            double byTop = maxH;
            int beamAlpha = 60;

            renderer.quad(bx, y + 1, bz, bx + bw, y + 1, bz, bx + bw, byTop, bz, bx, byTop, bz, r, g, b, beamAlpha);
            renderer.quad(bx + bw, y + 1, bz, bx + bw, y + 1, bz + bw, bx + bw, byTop, bz + bw, bx + bw, byTop, bz, r, g, b, beamAlpha);
            renderer.quad(bx + bw, y + 1, bz + bw, bx, y + 1, bz + bw, bx, byTop, bz + bw, bx + bw, byTop, bz + bw, r, g, b, beamAlpha);
            renderer.quad(bx, y + 1, bz + bw, bx, y + 1, bz, bx, byTop, bz, bx, byTop, bz + bw, r, g, b, beamAlpha);

            // Pillar center vertical lines for sharp visibility from distance
            renderer.line(x + 0.5, y + 1, z + 0.5, x + 0.5, byTop, z + 0.5, r, g, b, 220);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
