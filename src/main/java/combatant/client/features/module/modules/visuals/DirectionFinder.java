/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.HudPhase;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "directionfinder",
        displayName = "Direction Finder",
        description = "Calculates heading vectors towards suspected player bases and mining hubs, rendering directional arrows.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"basecompass", "tunneldirection"}
)
public class DirectionFinder extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue arrowColor =
            color("direction_color", "#FFFFFFFF"); // White
    private final NumberValue<Integer> searchRadius =
            num("direction_search_radius", "search_radius", 8, 2, 16);
    private final BooleanValue render3DArrow =
            bool("direction_render_3d", "render_3d", true);
    private final BooleanValue renderHudCompass =
            bool("direction_render_hud", "render_hud", true);

    private Vec3 calculatedTarget = null;
    private double calculatedDistance = 0.0;
    private float calculatedYaw = 0.0f;
    private int tickCounter = 0;

    public float getCalculatedYaw() { return calculatedYaw; }
    public double getCalculatedDistance() { return calculatedDistance; }
    public Vec3 getCalculatedTarget() { return calculatedTarget; }

    @Override
    public void onEnable() {
        calculatedTarget = null;
    }

    @Override
    public void onDisable() {
        calculatedTarget = null;
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter % 15 != 0) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int radius = searchRadius.get();

        List<Vec3> activityPoints = new ArrayList<>();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null || chunk.isEmpty()) continue;

                int baseX = SectionPos.sectionToBlockCoord(cx);
                int baseZ = SectionPos.sectionToBlockCoord(cz);

                // Analyze block entities (chests, furnaces, hoppers, barrels)
                int beCount = chunk.getBlockEntities().size();
                if (beCount > 0) {
                    chunk.getBlockEntities().values().forEach(be -> {
                        BlockPos p = be.getBlockPos();
                        activityPoints.add(new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                    });
                }

                // Sample sections for player blocks (torches, ladders, cobblestone)
                LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir()) continue;

                    if (section.maybeHas(s -> s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LADDER))) {
                        int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sIdx));
                        for (int y = 0; y < 16; y += 2) {
                            for (int z = 0; z < 16; z += 2) {
                                for (int x = 0; x < 16; x += 2) {
                                    BlockState state = section.getBlockState(x, y, z);
                                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LADDER)) {
                                        activityPoints.add(new Vec3(baseX + x + 0.5, baseY + y + 0.5, baseZ + z + 0.5));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activityPoints.size() >= 3) {
            double sumX = 0;
            double sumY = 0;
            double sumZ = 0;
            for (Vec3 p : activityPoints) {
                sumX += p.x;
                sumY += p.y;
                sumZ += p.z;
            }
            double avgX = sumX / activityPoints.size();
            double avgY = sumY / activityPoints.size();
            double avgZ = sumZ / activityPoints.size();

            calculatedTarget = new Vec3(avgX, avgY, avgZ);
            Vec3 playerPos = mc.player.position();
            calculatedDistance = playerPos.distanceTo(calculatedTarget);

            double diffX = avgX - playerPos.x;
            double diffZ = avgZ - playerPos.z;
            calculatedYaw = (float) (Mth.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0f;
        } else {
            calculatedTarget = null;
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!render3DArrow.get() || mc.player == null || calculatedTarget == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        Vec3 look = mc.player.getViewVector(tickDelta).normalize();

        // Render arrow 3 blocks in front of camera pointing towards target
        Vec3 arrowBase = camPos.add(look.scale(2.5)).subtract(0, 0.4, 0);
        Vec3 toTarget = calculatedTarget.subtract(arrowBase).normalize();

        Vec3 arrowTip = arrowBase.add(toTarget.scale(1.2));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = toTarget.cross(up).normalize().scale(0.35);

        Vec3 wingLeft = arrowBase.add(side);
        Vec3 wingRight = arrowBase.subtract(side);

        int argb = arrowColor.getArgb();
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        // Draw 3D arrow head
        renderer.triangle(arrowTip.x, arrowTip.y, arrowTip.z,
                wingLeft.x, wingLeft.y, wingLeft.z,
                wingRight.x, wingRight.y, wingRight.z,
                r, g, b, 230);

        // Draw line toward target
        renderer.line(arrowBase.x, arrowBase.y, arrowBase.z,
                arrowTip.x, arrowTip.y, arrowTip.z,
                r, g, b, 255);
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor graphics, float tickDelta) {
        if (!renderHudCompass.get() || mc.player == null || calculatedTarget == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float cx = screenWidth / 2.0f;
        float cy = screenHeight / 2.0f;

        // 1. Top compass pill
        String info = String.format("🧭 Base: %.0f° (%.0fm)", calculatedYaw, calculatedDistance);
        float textW = (float) textRenderer.getWidth(info);
        int x = (int) ((screenWidth - textW) / 2);
        int y = 50;
        int pad = 5;

        renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 0xBB111116);
        renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 1.0f, arrowColor.getArgb());
        textRenderer.render(info, x, y, new RenderColor(arrowColor.getArgb()), true);

        // 2. Radar Arrow around crosshair (Radium Client style)
        float playerYaw = mc.player.getYRot();
        float relativeYaw = (float) Mth.wrapDegrees(calculatedYaw - playerYaw);
        double angleRad = Math.toRadians(relativeYaw - 90.0);

        float arrowRadius = 65.0f;
        double ax = cx + Math.cos(angleRad) * arrowRadius;
        double ay = cy + Math.sin(angleRad) * arrowRadius;

        int col = arrowColor.getArgb();
        // Draw pointer dot / diamond
        renderer.roundedRect(ax - 3.5, ay - 3.5, 7.0, 7.0, 2.0f, col);

        // Distance text next to indicator
        String distBadge = String.format("%.0fm", calculatedDistance);
        float badgeW = (float) textRenderer.getWidth(distBadge);
        textRenderer.render(distBadge, (float) (ax - (badgeW / 2.0)), (float) (ay + 6.0), new RenderColor(0xFFFFFFFF), true);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }
}
