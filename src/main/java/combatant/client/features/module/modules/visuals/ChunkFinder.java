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
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "chunkfinder",
        displayName = "Chunk Finder",
        description = "Detects player-modified chunks using rotated deepslate (Bypass 16) and collinear stone mining trails.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"radiumchunkfinder", "trailfinder", "bypass16"}
)
public class ChunkFinder extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final Minecraft mc = Minecraft.getInstance();

    private final ModeValue markMode =
            modeSetting("chunk_finder_mark", "mark_mode", "New", "New", "Legacy");
    private final RGBAColorValue chunkColor =
            color("chunk_finder_color", "#FFFF0000"); // Red

    private final BooleanValue rotDeepslate =
            bool("chunk_rot_deepslate", "rot_deepslate", true);

    private final BooleanValue cobblestoneLines =
            bool("chunk_cobble_lines", "cobble_lines", true);
    private final NumberValue<Integer> cobbleMinLength =
            num("chunk_cobble_min", "cobble_min", 10, 3, 50);

    private final BooleanValue tuffLines =
            bool("chunk_tuff_lines", "tuff_lines", true);
    private final NumberValue<Integer> tuffMinLength =
            num("chunk_tuff_min", "tuff_min", 10, 3, 50);

    private final BooleanValue andesiteLines =
            bool("chunk_andesite_lines", "andesite_lines", true);
    private final NumberValue<Integer> andesiteMinLength =
            num("chunk_andesite_min", "andesite_min", 10, 3, 50);

    private final BooleanValue dioriteLines =
            bool("chunk_diorite_lines", "diorite_lines", true);
    private final NumberValue<Integer> dioriteMinLength =
            num("chunk_diorite_min", "diorite_min", 10, 3, 50);

    private final BooleanValue obsidianLines =
            bool("chunk_obsidian_lines", "obsidian_lines", true);
    private final NumberValue<Integer> obsidianMinLength =
            num("chunk_obsidian_min", "obsidian_min", 5, 3, 50);

    private final BooleanValue renderTracers =
            bool("chunk_finder_tracers", "tracers", true);
    private final BooleanValue renderBoxes =
            bool("chunk_finder_render_boxes", "render_boxes", true);
    private final BooleanValue soundAlert =
            bool("chunk_finder_sound", "sound_alert", true);
    private final BooleanValue chatAlert =
            bool("chunk_finder_chat", "chat_alert", true);
    private final BooleanValue webhook =
            bool("chunk_finder_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("chunk_finder_webhook_url", "webhook_url", "");

    public record FlaggedFeature(ChunkPos chunkPos, BlockPos pos, String reason, AABB box) {
    }

    private final Map<ChunkPos, FlaggedFeature> flaggedChunks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> notifiedChunks = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    public int getFlaggedCount() {
        return flaggedChunks.size();
    }

    public FlaggedFeature getNearestFeature() {
        if (mc.player == null || flaggedChunks.isEmpty()) return null;
        Vec3 pPos = mc.player.position();
        FlaggedFeature best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (FlaggedFeature f : flaggedChunks.values()) {
            double dSq = f.pos().distToCenterSqr(pPos.x, pPos.y, pPos.z);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = f;
            }
        }
        return best;
    }

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
        notifiedChunks.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 8 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = 6;

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                ChunkPos cPos = new ChunkPos(cx, cz);
                if (flaggedChunks.containsKey(cPos)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                FlaggedFeature feature = scanChunk(chunk, cPos);
                if (feature != null) {
                    flaggedChunks.put(cPos, feature);
                    if (notifiedChunks.add(cPos)) {
                        notifyPlayer(feature);
                    }
                }
            }
        }
    }

    private FlaggedFeature scanChunk(LevelChunk chunk, ChunkPos cPos) {
        LevelChunkSection[] sections = chunk.getSections();

        for (int sIdx = 0; sIdx < sections.length; sIdx++) {
            LevelChunkSection section = sections[sIdx];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            int baseX = SectionPos.sectionToBlockCoord(cPos.x());
            int baseZ = SectionPos.sectionToBlockCoord(cPos.z());

            // 1. Rotated Deepslate (Bypass 16) check
            if (rotDeepslate.get() && section.maybeHas(s -> s.is(Blocks.DEEPSLATE))) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.is(Blocks.DEEPSLATE) && state.hasProperty(BlockStateProperties.AXIS)) {
                                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                                if (axis == Direction.Axis.X || axis == Direction.Axis.Z) {
                                    BlockPos pos = new BlockPos(baseX + x, worldY, baseZ + z);
                                    return new FlaggedFeature(cPos, pos, "Rotated Deepslate (Axis " + axis + ")",
                                            new AABB(cPos.getMinBlockX(), worldY - 2, cPos.getMinBlockZ(),
                                                    cPos.getMaxBlockX() + 1, worldY + 2, cPos.getMaxBlockZ() + 1));
                                }
                            }
                        }
                    }
                }
            }

            // 2. Collinear stone lines checks
            if (cobblestoneLines.get() && section.maybeHas(s -> s.is(Blocks.COBBLESTONE))) {
                BlockPos found = checkCollinear(section, baseX, baseY, baseZ, Blocks.COBBLESTONE, cobbleMinLength.get());
                if (found != null) {
                    return new FlaggedFeature(cPos, found, "Cobblestone Trail (" + cobbleMinLength.get() + "+)",
                            new AABB(cPos.getMinBlockX(), found.getY() - 1, cPos.getMinBlockZ(),
                                    cPos.getMaxBlockX() + 1, found.getY() + 3, cPos.getMaxBlockZ() + 1));
                }
            }

            if (tuffLines.get() && section.maybeHas(s -> s.is(Blocks.TUFF))) {
                BlockPos found = checkCollinear(section, baseX, baseY, baseZ, Blocks.TUFF, tuffMinLength.get());
                if (found != null) {
                    return new FlaggedFeature(cPos, found, "Tuff Line (" + tuffMinLength.get() + "+)",
                            new AABB(cPos.getMinBlockX(), found.getY() - 1, cPos.getMinBlockZ(),
                                    cPos.getMaxBlockX() + 1, found.getY() + 3, cPos.getMaxBlockZ() + 1));
                }
            }

            if (andesiteLines.get() && section.maybeHas(s -> s.is(Blocks.ANDESITE))) {
                BlockPos found = checkCollinear(section, baseX, baseY, baseZ, Blocks.ANDESITE, andesiteMinLength.get());
                if (found != null) {
                    return new FlaggedFeature(cPos, found, "Andesite Line (" + andesiteMinLength.get() + "+)",
                            new AABB(cPos.getMinBlockX(), found.getY() - 1, cPos.getMinBlockZ(),
                                    cPos.getMaxBlockX() + 1, found.getY() + 3, cPos.getMaxBlockZ() + 1));
                }
            }

            if (dioriteLines.get() && section.maybeHas(s -> s.is(Blocks.DIORITE))) {
                BlockPos found = checkCollinear(section, baseX, baseY, baseZ, Blocks.DIORITE, dioriteMinLength.get());
                if (found != null) {
                    return new FlaggedFeature(cPos, found, "Diorite Line (" + dioriteMinLength.get() + "+)",
                            new AABB(cPos.getMinBlockX(), found.getY() - 1, cPos.getMinBlockZ(),
                                    cPos.getMaxBlockX() + 1, found.getY() + 3, cPos.getMaxBlockZ() + 1));
                }
            }

            if (obsidianLines.get() && section.maybeHas(s -> s.is(Blocks.OBSIDIAN))) {
                BlockPos found = checkCollinear(section, baseX, baseY, baseZ, Blocks.OBSIDIAN, obsidianMinLength.get());
                if (found != null) {
                    return new FlaggedFeature(cPos, found, "Obsidian Highway/Line (" + obsidianMinLength.get() + "+)",
                            new AABB(cPos.getMinBlockX(), found.getY() - 1, cPos.getMinBlockZ(),
                                    cPos.getMaxBlockX() + 1, found.getY() + 3, cPos.getMaxBlockZ() + 1));
                }
            }
        }

        return null;
    }

    private BlockPos checkCollinear(LevelChunkSection section, int baseX, int baseY, int baseZ,
                                    net.minecraft.world.level.block.Block target, int minLen) {
        for (int y = 0; y < 16; y++) {
            // Check horizontal runs along X axis
            for (int z = 0; z < 16; z++) {
                int run = 0;
                int startX = 0;
                for (int x = 0; x < 16; x++) {
                    if (section.getBlockState(x, y, z).is(target)) {
                        if (run == 0) startX = x;
                        run++;
                        if (run >= minLen) {
                            return new BlockPos(baseX + startX, baseY + y, baseZ + z);
                        }
                    } else {
                        run = 0;
                    }
                }
            }

            // Check horizontal runs along Z axis
            for (int x = 0; x < 16; x++) {
                int run = 0;
                int startZ = 0;
                for (int z = 0; z < 16; z++) {
                    if (section.getBlockState(x, y, z).is(target)) {
                        if (run == 0) startZ = z;
                        run++;
                        if (run >= minLen) {
                            return new BlockPos(baseX + x, baseY + y, baseZ + startZ);
                        }
                    } else {
                        run = 0;
                    }
                }
            }
        }
        return null;
    }

    private void notifyPlayer(FlaggedFeature feature) {
        if (mc.player == null) return;
        BlockPos pos = feature.pos();
        double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String coordsStr = String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());

        if (chatAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            Component coordsComponent = Component.literal(String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(style -> style
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(coordsStr))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to copy coordinates: §f" + coordsStr))));

            Component msg = Component.literal("§c[ChunkFinder] §ePlayer activity §f(" + feature.reason() + ") §eat ")
                    .append(coordsComponent)
                    .append(Component.literal(String.format(" §7(Chunk [%d, %d], §f%.0fm§7 away)", feature.chunkPos().x(), feature.chunkPos().z(), dist)));

            mc.gui.hud.getChat().addClientSystemMessage(msg);
        }

        if (soundAlert.get()) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.4f);
        }

        if (webhook.get() && !webhookUrl.get().isBlank()) {
            sendWebhook(feature, dist);
        }
    }

    private void sendWebhook(FlaggedFeature feature, double dist) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String json = String.format(
                "{\"content\":null,\"embeds\":[{\"title\":\"Player Chunk Detected!\",\"color\":16711680,\"fields\":[{\"name\":\"Reason\",\"value\":\"%s\",\"inline\":true},{\"name\":\"Coordinates\",\"value\":\"[%d, %d, %d]\",\"inline\":true},{\"name\":\"Distance\",\"value\":\"%.1f blocks\",\"inline\":true}]}]}",
                escapeJson(feature.reason()),
                feature.pos().getX(), feature.pos().getY(), feature.pos().getZ(),
                dist
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || flaggedChunks.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        int argb = chunkColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        for (FlaggedFeature feature : flaggedChunks.values()) {
            AABB box = feature.box();
            if (box == null) continue;

            if (renderBoxes.get()) {
                int fillA = 35;
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
                renderer.line(camPos.x, camPos.y, camPos.z, center.x, center.y, center.z, r, g, b, 210);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
