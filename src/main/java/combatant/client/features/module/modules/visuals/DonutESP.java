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
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "dnotesp",
        displayName = "DonutESP",
        description = "Specialized DonutSMP ESP. Highlights high-value mob spawners, shulker boxes, vaults, and netherite.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"spawneresp", "donuthighlight", "valuableesp"}
)
public class DonutESP extends Module {

    private static final int COLOR_SPAWNER = 0xFFE040FB;   // Neon Violet
    private static final int COLOR_SHULKER = 0xFFFF4081;   // Vibrant Pink
    private static final int COLOR_VAULT = 0xFF00E5FF;     // Cyan
    private static final int COLOR_DEBRIS = 0xFFFFAB00;    // Gold

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("dnotesp_range", "range", 64.0, 16.0, 128.0);
    private final BooleanValue spawners =
            bool("dnotesp_spawners", "spawners", true);
    private final BooleanValue shulkers =
            bool("dnotesp_shulkers", "shulkers", true);
    private final BooleanValue vaults =
            bool("dnotesp_vaults", "vaults", true);
    private final BooleanValue ancientDebris =
            bool("dnotesp_ancient_debris", "ancient_debris", true);

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || mc.level == null) return;

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        double maxRange = range.get();
        double maxRangeSq = maxRange * maxRange;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxRange / 16.0);

        List<DonutEntry> entries = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = pos.distToCenterSqr(camera.x, camera.y, camera.z);
                    if (distSq > maxRangeSq) continue;

                    Integer color = resolveBlockEntityColor(be);
                    if (color != null) {
                        entries.add(new DonutEntry(new AABB(pos), color));
                    }
                }
            }
        }

        for (DonutEntry entry : entries) {
            drawBox(renderer, entry.box, entry.argb);
        }
    }

    private Integer resolveBlockEntityColor(BlockEntity be) {
        if (spawners.get() && be instanceof SpawnerBlockEntity) {
            return COLOR_SPAWNER;
        }
        if (shulkers.get() && be instanceof ShulkerBoxBlockEntity) {
            return COLOR_SHULKER;
        }
        if (vaults.get() && be instanceof EnderChestBlockEntity) {
            return COLOR_VAULT;
        }
        return null;
    }

    private static void drawBox(Renderer3D renderer, AABB box, int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int fillA = 50;
        int lineA = 240;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

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

    private record DonutEntry(AABB box, int argb) {
    }
}
