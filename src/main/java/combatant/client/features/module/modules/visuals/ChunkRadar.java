/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DonutSMP base & stash scanner.
 * Combines container density analysis with 67Client's loaded beehive honey-level-5 detection.
 */
@ModuleInfo(
        id = "chunkradar",
        displayName = "ChunkRadar",
        description = "Scans loaded chunks for hidden DonutSMP player bases, chest rooms, and loaded beehive honey signals.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"basefinder", "stashradar", "claimfinder", "chunkfinder"}
)
public class ChunkRadar extends Module {

    private final NumberValue<Integer> minContainers =
            num("chunkradar_min_containers", "min_containers", 5, 2, 25);
    private final BooleanValue scanBeehives =
            bool("chunkradar_beehives", "beehives", true);
    private final BooleanValue chatAlert =
            bool("chunkradar_chat_alert", "chat_alert", true);
    private final BooleanValue soundAlert =
            bool("chunkradar_sound_alert", "sound_alert", true);
    private final BooleanValue renderBoxes =
            bool("chunkradar_render_boxes", "render_boxes", true);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, Integer> firstLoadedTicks = new ConcurrentHashMap<>();
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
        flaggedChunks.clear();
        firstLoadedTicks.clear();
        tickCounter = 0;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 8;

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                if (flaggedChunks.contains(chunkPos)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                long key = chunkPos.pack();
                firstLoadedTicks.putIfAbsent(key, tickCounter);
                boolean longLoaded = (tickCounter - firstLoadedTicks.get(key)) >= 100;

                // Check 1: Container count
                int containerCount = 0;
                BlockPos samplePos = null;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity || be instanceof HopperBlockEntity) {
                        containerCount++;
                        if (samplePos == null) samplePos = be.getBlockPos();
                    }
                }

                if (containerCount >= minContainers.get() && samplePos != null) {
                    flaggedChunks.add(chunkPos);
                    notifyDetection(chunkPos, samplePos, containerCount + " containers");
                    continue;
                }

                // Check 2: 67Client Beehive Full Honey signal (indicates chunk kept loaded by player base)
                if (scanBeehives.get() && longLoaded && hasFullHoneyBeehive(chunk)) {
                    flaggedChunks.add(chunkPos);
                    BlockPos centerPos = new BlockPos(chunkPos.getMinBlockX() + 8, 64, chunkPos.getMinBlockZ() + 8);
                    notifyDetection(chunkPos, centerPos, "loaded active beehive (honey 5)");
                }
            }
        }
    }

    private boolean hasFullHoneyBeehive(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section == null || section.hasOnlyAir()) continue;
            if (section.maybeHas(state -> state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST))) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int ly = 0; ly < 16; ly++) {
                            BlockState state = section.getBlockState(lx, ly, lz);
                            if ((state.is(Blocks.BEEHIVE) || state.is(Blocks.BEE_NEST))
                                    && state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                                    && state.getValue(BeehiveBlock.HONEY_LEVEL) == 5) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void notifyDetection(ChunkPos chunkPos, BlockPos pos, String reason) {
        if (chatAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            String message = String.format(
                    "§d[ChunkRadar] §eBase Signal at [%d, %d, %d] §7(Chunk [%d, %d] - §a%s§7)",
                    pos.getX(), pos.getY(), pos.getZ(),
                    chunkPos.x(), chunkPos.z(), reason
            );
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        }

        if (soundAlert.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.BELL_BLOCK, 1.0f, 1.2f);
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!renderBoxes.get() || mc.player == null || flaggedChunks.isEmpty()) return;

        for (ChunkPos cp : flaggedChunks) {
            double minX = cp.getMinBlockX();
            double maxX = cp.getMaxBlockX() + 1;
            double minZ = cp.getMinBlockZ();
            double maxZ = cp.getMaxBlockZ() + 1;
            double minY = mc.level != null ? mc.level.getMinY() : -64;
            double maxY = minY + 16;

            int r = 255;
            int g = 64;
            int b = 128;
            int fillA = 35;
            int lineA = 220;

            // Box quads
            renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, fillA);
            renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, fillA);
            renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, fillA);
            renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, fillA);
            renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, fillA);
            renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, fillA);

            // Outlines
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
    }
}
