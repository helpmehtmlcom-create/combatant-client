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
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "beehiveesp",
        displayName = "Beehive ESP",
        description = "Locates beehives and bee nests with customizable honey-level filtering to detect loaded player bases.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"beehives", "honeyfinder"}
)
public class BeehiveESP extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            num("beehive_range", "range", 100.0, 10.0, 256.0);
    private final BooleanValue fill =
            bool("beehive_fill", "fill", true);
    private final BooleanValue outline =
            bool("beehive_outline", "outline", true);
    private final BooleanValue tracers =
            bool("beehive_tracers", "tracers", false);

    private final RGBAColorValue fillColor =
            color("beehive_fill_color", "#55FFC800");
    private final RGBAColorValue outlineColor =
            color("beehive_outline_color", "#FFFFC800");
    private final RGBAColorValue tracerColor =
            color("beehive_tracer_color", "#FFFFD700");

    private final BooleanValue level0 = bool("beehive_lvl0", "level_0_empty", false);
    private final BooleanValue level1 = bool("beehive_lvl1", "level_1", false);
    private final BooleanValue level2 = bool("beehive_lvl2", "level_2", false);
    private final BooleanValue level3 = bool("beehive_lvl3", "level_3", false);
    private final BooleanValue level4 = bool("beehive_lvl4", "level_4", false);
    private final BooleanValue level5 = bool("beehive_lvl5", "level_5_full", true);

    private final BooleanValue detectBeehives = bool("beehive_hives", "beehives", true);
    private final BooleanValue detectBeeNests = bool("beehive_nests", "bee_nests", true);

    public record HiveInfo(BlockPos pos, int honeyLevel, boolean isNest) {}

    private final Map<BlockPos, HiveInfo> hives = new ConcurrentHashMap<>();
    private final Set<BlockPos> notifiedHives = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        hives.clear();
        notifiedHives.clear();
    }

    @Override
    public void onDisable() {
        hives.clear();
        notifiedHives.clear();
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        double maxDist = range.get();
        double maxDistSq = maxDist * maxDist;
        Vec3 playerPos = mc.player.position();

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDist / 16.0);

        Map<BlockPos, HiveInfo> found = new HashMap<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir()) continue;

                    if (!section.maybeHas(s -> s.is(Blocks.BEEHIVE) || s.is(Blocks.BEE_NEST))) {
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
                                double dx = (worldX + 0.5) - playerPos.x;
                                double dy = (worldY + 0.5) - playerPos.y;
                                double dz = (worldZ + 0.5) - playerPos.z;
                                if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

                                BlockState state = section.getBlockState(x, y, z);
                                boolean isHive = state.is(Blocks.BEEHIVE);
                                boolean isNest = state.is(Blocks.BEE_NEST);

                                if ((isHive && detectBeehives.get()) || (isNest && detectBeeNests.get())) {
                                    int lvl = state.hasProperty(BeehiveBlock.HONEY_LEVEL) ? state.getValue(BeehiveBlock.HONEY_LEVEL) : 0;
                                    if (matchesLevelFilter(lvl)) {
                                        BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                        found.put(pos, new HiveInfo(pos, lvl, isNest));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        hives.clear();
        hives.putAll(found);

        for (Map.Entry<BlockPos, HiveInfo> entry : found.entrySet()) {
            BlockPos p = entry.getKey();
            if (entry.getValue().honeyLevel() == 5 && notifiedHives.add(p)) {
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    double dist = Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                    String msg = String.format(
                            "§6[BeehiveESP] §aFound full honey hive (Level 5) at §b[%d, %d, %d] §7(§f%.0fm§7 away)",
                            p.getX(), p.getY(), p.getZ(), dist
                    );
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
                }
                mc.player.playSound(SoundEvents.BEE_POLLINATE, 1.0f, 1.0f);
            }
        }
    }

    private boolean matchesLevelFilter(int lvl) {
        return switch (lvl) {
            case 0 -> level0.get();
            case 1 -> level1.get();
            case 2 -> level2.get();
            case 3 -> level3.get();
            case 4 -> level4.get();
            case 5 -> level5.get();
            default -> false;
        };
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (mc.player == null || hives.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        int fArgb = fillColor.getArgb();
        int fr = (fArgb >>> 16) & 0xFF;
        int fg = (fArgb >>> 8) & 0xFF;
        int fb = fArgb & 0xFF;
        int fa = (fArgb >>> 24) & 0xFF;

        int oArgb = outlineColor.getArgb();
        int or = (oArgb >>> 16) & 0xFF;
        int og = (oArgb >>> 8) & 0xFF;
        int ob = oArgb & 0xFF;
        int oa = (oArgb >>> 24) & 0xFF;

        int tArgb = tracerColor.getArgb();
        int tr = (tArgb >>> 16) & 0xFF;
        int tg = (tArgb >>> 8) & 0xFF;
        int tb = tArgb & 0xFF;

        for (HiveInfo info : hives.values()) {
            AABB box = new AABB(info.pos());

            if (fill.get()) {
                renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, fr, fg, fb, fa);
                renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, fr, fg, fb, fa);
                renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, fr, fg, fb, fa);
            }

            if (outline.get()) {
                renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, or, og, ob, oa);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, or, og, ob, oa);
                renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, or, og, ob, oa);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, or, og, ob, oa);

                renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, or, og, ob, oa);
                renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, or, og, ob, oa);
                renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, or, og, ob, oa);
                renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, or, og, ob, oa);

                renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, or, og, ob, oa);
                renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, or, og, ob, oa);
                renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, or, og, ob, oa);
                renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, or, og, ob, oa);
            }

            if (tracers.get()) {
                Vec3 center = box.getCenter();
                Vec3 start = RenderState.tracerOrigin();
                renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, tr, tg, tb, 220);
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }
}
