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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "clusteresp",
        displayName = "Cluster ESP",
        description = "Detects dense clusters of base containers and utility blocks, rendering bounding boxes and tracers to stash locations.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"basecluster", "stashcluster"}
)
public class ClusterESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> alpha =
            num("cluster_alpha", "alpha", 125, 1, 255);
    private final BooleanValue tracers =
            bool("cluster_tracers", "tracers", true);
    private final NumberValue<Integer> minBlocks =
            num("cluster_min_blocks", "min_blocks", 4, 2, 20);
    private final NumberValue<Double> clusterDistance =
            num("cluster_distance", "cluster_distance", 16.0, 4.0, 32.0);
    private final RGBAColorValue clusterColor =
            color("cluster_color", "#FF00FFCC"); // Aqua / Mint

    public record BaseCluster(List<BlockPos> blocks, AABB boundingBox) {
        public Vec3 center() {
            return boundingBox.getCenter();
        }
    }

    private final List<BaseCluster> clusters = new ArrayList<>();
    private final Set<BlockPos> notifiedClusters = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        clusters.clear();
        notifiedClusters.clear();
    }

    @Override
    public void onDisable() {
        clusters.clear();
        notifiedClusters.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 6;

        List<BlockPos> targetPositions = new ArrayList<>();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isClusterBlock(be)) {
                        targetPositions.add(be.getBlockPos());
                    }
                }
            }
        }

        // Group target positions into clusters
        double maxDistSq = clusterDistance.get() * clusterDistance.get();
        List<List<BlockPos>> grouped = new ArrayList<>();

        for (BlockPos pos : targetPositions) {
            boolean added = false;
            for (List<BlockPos> group : grouped) {
                for (BlockPos member : group) {
                    if (member.distSqr(pos) <= maxDistSq) {
                        group.add(pos);
                        added = true;
                        break;
                    }
                }
                if (added) break;
            }
            if (!added) {
                List<BlockPos> newGroup = new ArrayList<>();
                newGroup.add(pos);
                grouped.add(newGroup);
            }
        }

        List<BaseCluster> newClusters = new ArrayList<>();
        for (List<BlockPos> group : grouped) {
            if (group.size() >= minBlocks.get()) {
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

                for (BlockPos p : group) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX() + 1);
                    maxY = Math.max(maxY, p.getY() + 1);
                    maxZ = Math.max(maxZ, p.getZ() + 1);
                }

                BaseCluster cluster = new BaseCluster(group, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
                newClusters.add(cluster);

                BlockPos centerPos = BlockPos.containing(cluster.center());
                if (notifiedClusters.add(centerPos)) {
                    if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                        String coordsStr = String.format("%d %d %d", centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        Component coordsComp = Component.literal(String.format("[%d, %d, %d]", centerPos.getX(), centerPos.getY(), centerPos.getZ()))
                                .withStyle(style -> style
                                        .withColor(0x55FFFF)
                                        .withUnderlined(true)
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(coordsStr))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to copy coordinates: §f" + coordsStr))));

                        Component msg = Component.literal("§b[ClusterESP] §aFound Base Cluster §e(" + group.size() + " containers) §aat ")
                                .append(coordsComp);

                        mc.gui.hud.getChat().addClientSystemMessage(msg);
                    }
                    mc.player.playSound(SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
                }
            }
        }

        synchronized (clusters) {
            clusters.clear();
            clusters.addAll(newClusters);
        }
    }

    private boolean isClusterBlock(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof AbstractFurnaceBlockEntity
                || be instanceof BrewingStandBlockEntity
                || be instanceof BeaconBlockEntity
                || be instanceof CrafterBlockEntity
                || be instanceof DispenserBlockEntity;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        int argb = clusterColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = alpha.get();

        synchronized (clusters) {
            for (BaseCluster cluster : clusters) {
                AABB box = cluster.boundingBox();

                // Filled box
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

                // Outline
                int lineA = Math.min(255, a + 100);
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
                if (tracers.get()) {
                    Vec3 center = box.getCenter();
                    Vec3 start = RenderState.tracerOrigin();
                    renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, r, g, b, 240);
                }
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
