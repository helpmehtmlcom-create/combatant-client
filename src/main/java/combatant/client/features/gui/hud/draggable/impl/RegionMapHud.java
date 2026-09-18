/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * DonutSMP Region Minimap HUD element.
 * Displays a tactical 2D minimap showing DonutSMP 50k server regions, boundaries,
 * regional labels, and current player position.
 */
@HudElementRegister(order = 68)
public final class RegionMapHud extends DraggableHudElement {

    {
        defaultLayout(20.0f, 180.0f);
    }

    public static final double TOTAL_WORLD_RADIUS = 150_000.0;

    // 6 Main DonutSMP Regions arranged in grid:
    // [-150k to -50k, -50k to +50k, +50k to +150k]
    private static final RegionZone[] REGIONS = {
            new RegionZone("EU Central", "EU-C", -150_000, -50_000, -150_000, 0, 0xFFFF4444),
            new RegionZone("EU West", "EU-W", -50_000, 50_000, -150_000, 0, 0xFFFF8800),
            new RegionZone("East", "NA-E", 50_000, 150_000, -150_000, 0, 0xFFFFCC00),
            new RegionZone("West", "NA-W", -150_000, -50_000, 0, 150_000, 0xFF00E5FF),
            new RegionZone("Asia", "Asia", -50_000, 50_000, 0, 150_000, 0xFF33FF88),
            new RegionZone("Oceania", "Oce", 50_000, 150_000, 0, 150_000, 0xFFAA00FF)
    };

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final NumberValue<Double> mapSize =
            new NumberValue<>("region_map_size", 140.0, 80.0, 240.0);
    private final BooleanValue showLabels =
            new BooleanValue("region_map_labels", true);
    private final BooleanValue showGrid =
            new BooleanValue("region_map_grid", true);
    private final BooleanValue showPlayerBlip =
            new BooleanValue("region_map_player", true);
    private final RGBAColorValue bgColor =
            new RGBAColorValue("region_map_bg", "#CC0A0A0A");
    private final RGBAColorValue borderColor =
            new RGBAColorValue("region_map_border", "#FF333333");
    private final RGBAColorValue playerBlipColor =
            new RGBAColorValue("region_map_blip", "#FFFFFFFF");

    public RegionMapHud() {
        super("region_map_hud", "Region Map", false);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(mapSize));
        defs.add(SettingDef.bool(showLabels));
        defs.add(SettingDef.bool(showGrid));
        defs.add(SettingDef.bool(showPlayerBlip));
        defs.add(SettingDef.color(bgColor));
        defs.add(SettingDef.color(borderColor));
        defs.add(SettingDef.color(playerBlipColor));
    }
    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 20.0f;
        this.y = 180.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor graphics,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry.isForceVisible();
        if (!isEnabled()) {
            width = 0f;
            height = 0f;
            return;
        }
        float scale = HudScale.scale(screenW, screenH) * (hud.getFontSize() / 18f);
        float size = (float) (mapSize.get() * scale);

        float baseX = x;
        float baseY = y;

        int bgArgb = bgColor.getArgb();
        int borderArgb = borderColor.getArgb();

        // 1. Map container box
        renderer.roundedRect(baseX, baseY, size, size, 6.0f, bgArgb);
        renderer.roundedRectStroke(baseX, baseY, size, size, 6.0f, 1.5f, borderArgb);

        // 2. Draw DonutSMP Region cells
        double minCoord = -TOTAL_WORLD_RADIUS;
        double maxCoord = TOTAL_WORLD_RADIUS;
        double coordSpan = maxCoord - minCoord;

        // Draw regions and labels
        for (RegionZone zone : REGIONS) {
            float x1 = (float) (baseX + ((zone.minX - minCoord) / coordSpan) * size);
            float x2 = (float) (baseX + ((zone.maxX - minCoord) / coordSpan) * size);
            float y1 = (float) (baseY + ((zone.minZ - minCoord) / coordSpan) * size);
            float y2 = (float) (baseY + ((zone.maxZ - minCoord) / coordSpan) * size);

            float rw = x2 - x1;
            float rh = y2 - y1;

            // Faint region tint
            int zoneTint = (zone.colorArgb & 0x00FFFFFF) | 0x15000000;
            renderer.quad(x1, y1, rw, rh, zoneTint);

            // Grid lines
            if (showGrid.get()) {
                int lineCol = (zone.colorArgb & 0x00FFFFFF) | 0x44000000;
                renderer.quad(x1, y1, rw, 1.0f, lineCol);
                renderer.quad(x1, y1, 1.0f, rh, lineCol);
            }

            // Region label text
            if (showLabels.get() && textRenderer != null) {
                float labelW = (float) textRenderer.getWidth(zone.shortName, true);
                float fontH = (float) textRenderer.getHeight(true);
                float labelX = x1 + (rw - labelW) * 0.5f;
                float labelY = y1 + (rh - fontH) * 0.5f;

                int textCol = (zone.colorArgb & 0x00FFFFFF) | 0xBB000000;
                textRenderer.render(zone.shortName, labelX, labelY, new RenderColor(textCol), true);
            }
        }

        // Center World Spawn indicator / Cross
        float spawnPxX = (float) (baseX + ((0.0 - minCoord) / coordSpan) * size);
        float spawnPxY = (float) (baseY + ((0.0 - minCoord) / coordSpan) * size);
        renderer.quad(spawnPxX - 3.0f, spawnPxY, 7.0f, 1.0f, 0x88FFFFFF);
        renderer.quad(spawnPxX, spawnPxY - 3.0f, 1.0f, 7.0f, 0x88FFFFFF);

        // 3. Player Position Blip & Current Region Tag
        double playerX = 0;
        double playerZ = 0;
        if (mc.player != null) {
            playerX = mc.player.getX();
            playerZ = mc.player.getZ();
        }

        float playerPxX = (float) (baseX + ((playerX - minCoord) / coordSpan) * size);
        float playerPxY = (float) (baseY + ((playerZ - minCoord) / coordSpan) * size);

        playerPxX = Mth.clamp(playerPxX, baseX + 4.0f, baseX + size - 4.0f);
        playerPxY = Mth.clamp(playerPxY, baseY + 4.0f, baseY + size - 4.0f);

        if (showPlayerBlip.get()) {
            int blipCol = playerBlipColor.getArgb();
            // Blip glow
            renderer.roundedRect(playerPxX - 3.5f, playerPxY - 3.5f, 7.0f, 7.0f, 3.5f, (blipCol & 0x00FFFFFF) | 0x44000000);
            // Blip center
            renderer.roundedRect(playerPxX - 2.0f, playerPxY - 2.0f, 4.0f, 4.0f, 2.0f, blipCol);
        }

        // Bottom HUD badge with current region & distance
        String currentRegion = getRegionName(playerX, playerZ);
        String subText = String.format("%s (%.0fk, %.0fk)", currentRegion, playerX / 1000.0, playerZ / 1000.0);
        float badgeW = (float) textRenderer.getWidth(subText, true);
        float badgeH = (float) textRenderer.getHeight(true) + 4.0f;
        float badgeX = baseX + (size - badgeW) * 0.5f;
        float badgeY = baseY + size - badgeH - 2.0f;

        renderer.roundedRect(badgeX - 4.0f, badgeY - 1.0f, badgeW + 8.0f, badgeH, 4.0f, 0xAA000000);
        textRenderer.render(subText, badgeX, badgeY + 1.0f, new RenderColor(0xFF00E5FF), true);

        width = size;
        height = size;
    }

    public static String getRegionName(double x, double z) {
        for (RegionZone r : REGIONS) {
            if (x >= r.minX && x < r.maxX && z >= r.minZ && z < r.maxZ) {
                return r.name;
            }
        }
        return "Wilderness";
    }

    private record RegionZone(String name, String shortName, double minX, double maxX, double minZ, double maxZ, int colorArgb) {}
}
