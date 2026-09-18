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
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.AHSniper;
import combatant.client.features.module.modules.player.AutoBaseDig;
import combatant.client.features.module.modules.player.PlayerDetect;
import combatant.client.features.module.modules.player.SilentHome;
import combatant.client.features.module.modules.player.SpawnerProtect;
import combatant.client.features.module.modules.visuals.*;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

@HudElementRegister(order = 65)
public final class DonutIndicator extends DraggableHudElement {

    {
        defaultLayout(16.0f, 130.0f);
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final NumberValue<Double> scale =
            new NumberValue<>("donut_hud_scale", 1.0, 0.5, 3.0);
    private final BooleanValue alwaysShow =
            new BooleanValue("donut_hud_always_show", true);
    private final BooleanValue blur =
            new BooleanValue("donut_hud_blur", true);
    private final RGBAColorValue bgColor =
            new RGBAColorValue("donut_hud_bg", "#EE101218");
    private final RGBAColorValue strokeColor =
            new RGBAColorValue("donut_hud_stroke", "#FF00FFCC");

    public DonutIndicator() {
        super("donut_indicator", "DonutSMP Radar", true);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scale));
        defs.add(SettingDef.bool(alwaysShow));
        defs.add(SettingDef.bool(blur));
        defs.add(SettingDef.color(bgColor));
        defs.add(SettingDef.color(strokeColor));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16.0f;
        this.y = 130.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }
    public record TelemetryRow(String icon, String label, String value, int colorArgb) {
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry.isForceVisible();
        boolean forced = alwaysShow.get();
        if (!isEnabled()) {
            width = 0f;
            height = 0f;
            return;
        }

        List<TelemetryRow> rows = collectRows();

        if (rows.isEmpty() && !forced && !preview) {
            width = 0f;
            height = 0f;
            return;
        }

        // If in preview/config mode and no modules are active, show demo indicators
        if (rows.isEmpty()) {
            rows.add(new TelemetryRow("💎", "Netherite", "2 Debris (34m)", 0xFFFF9900));
            rows.add(new TelemetryRow("🗺️", "Chunks", "4 Flagged Chunks", 0xFFFF4444));
            rows.add(new TelemetryRow("🧭", "Direction", "Bearing: 142° (85m)", 0xFF00FFCC));
            rows.add(new TelemetryRow("📦", "Stashes", "1 Vault (16 crates)", 0xFF33FFAA));
            rows.add(new TelemetryRow("⛏️", "Base Dig", "Excavating [102, 14, -80]", 0xFFFF9933));
            rows.add(new TelemetryRow("🎯", "AH Sniper", "shulker (< 500k) • Armed", 0xFF00FF66));
            rows.add(new TelemetryRow("🛡️", "Radar", "Scanning (Safe)", 0xFF55FF55));
        }

        float baseScale = HudScale.scale(screenW, screenH) * (hud.getFontSize() / 18f);
        float drawScale = baseScale * scale.get().floatValue();

        float headerH = 15.0f * drawScale;
        float rowH = 11.5f * drawScale;
        float pad = 6.0f * drawScale;

        // Measure max text width
        float maxRowW = 110.0f;
        String headerTitle = "🍩 DonutSMP Radar (" + rows.size() + ")";
        float headerW = (float) textRenderer.getWidth(headerTitle);
        maxRowW = Math.max(maxRowW, headerW);

        for (TelemetryRow row : rows) {
            String full = row.icon() + " " + row.label() + ": " + row.value();
            float w = (float) textRenderer.getWidth(full);
            if (w > maxRowW) maxRowW = w;
        }

        float boxW = (maxRowW + (pad * 2.5f)) * drawScale;
        float boxH = (headerH + (rows.size() * rowH) + pad * 1.5f);

        int bgArgb = bgColor.getArgb();
        int strokeArgb = strokeColor.getArgb();

        // 1. Draw rounded container backdrop
        renderer.roundedRect(x, y, boxW, boxH, 4.0f * drawScale, bgArgb);
        renderer.roundedRectStroke(x, y, boxW, boxH, 4.0f * drawScale, 1.0f * drawScale, strokeArgb);

        // 2. Draw Header
        float curY = y + pad;
        textRenderer.render(headerTitle, x + pad, curY, new RenderColor(strokeArgb), true);

        // Header divider line
        curY += headerH - (4.0f * drawScale);
        renderer.line(x + pad, curY, x + boxW - pad, curY, (strokeArgb & 0x00FFFFFF) | 0x44000000);
        curY += 4.0f * drawScale;

        // 3. Draw Telemetry Rows
        for (TelemetryRow row : rows) {
            String line = row.icon() + " " + row.label() + ": ";
            float iconLabelW = (float) textRenderer.getWidth(line);

            // Icon & Label
            textRenderer.render(line, x + pad, curY, new RenderColor(0xFFDDDDDD), true);

            // Value with specific highlight color
            textRenderer.render(row.value(), x + pad + iconLabelW, curY, new RenderColor(row.colorArgb()), true);

            curY += rowH;
        }

        width = boxW;
        height = boxH;
    }

    private List<TelemetryRow> collectRows() {
        List<TelemetryRow> rows = new ArrayList<>();

        // 1. Netherite Finder
        NetheriteFinder netherite = Modules.get(NetheriteFinder.class);
        if (netherite != null && netherite.isEnabled()) {
            int count = netherite.getTargetCount();
            if (count > 0) {
                NetheriteFinder.TargetInfo nearest = netherite.getNearestTarget();
                double dist = nearest != null && mc.player != null
                        ? Math.sqrt(mc.player.distanceToSqr(nearest.pos().getX() + 0.5, nearest.pos().getY() + 0.5, nearest.pos().getZ() + 0.5))
                        : 0;
                String val = String.format("%d targets (Nearest: %.0fm)", count, dist);
                rows.add(new TelemetryRow("💎", "Netherite", val, 0xFFFF9900));
            } else {
                rows.add(new TelemetryRow("💎", "Netherite", "Scanning chunks...", 0xFFAAAAAA));
            }
        }

        // 2. Chunk Finder
        ChunkFinder chunkFinder = Modules.get(ChunkFinder.class);
        if (chunkFinder != null && chunkFinder.isEnabled()) {
            int count = chunkFinder.getFlaggedCount();
            if (count > 0) {
                ChunkFinder.FlaggedFeature nearest = chunkFinder.getNearestFeature();
                double dist = nearest != null && mc.player != null
                        ? Math.sqrt(mc.player.distanceToSqr(nearest.pos().getX() + 0.5, nearest.pos().getY() + 0.5, nearest.pos().getZ() + 0.5))
                        : 0;
                String val = String.format("%d chunks (Nearest: %.0fm)", count, dist);
                rows.add(new TelemetryRow("🗺️", "Chunks", val, 0xFFFF4444));
            } else {
                rows.add(new TelemetryRow("🗺️", "Chunks", "Scanning trails...", 0xFFAAAAAA));
            }
        }

        // 3. Direction Finder
        DirectionFinder dirFinder = Modules.get(DirectionFinder.class);
        if (dirFinder != null && dirFinder.isEnabled()) {
            if (dirFinder.getCalculatedTarget() != null) {
                float yaw = dirFinder.getCalculatedYaw();
                double dist = dirFinder.getCalculatedDistance();
                String arrow = resolveArrowChar(yaw);
                String val = String.format("%s %.0f° (%.0fm)", arrow, yaw, dist);
                rows.add(new TelemetryRow("🧭", "Direction", val, 0xFF00FFCC));
            } else {
                rows.add(new TelemetryRow("🧭", "Direction", "Awaiting activity...", 0xFFAAAAAA));
            }
        }

        // 4. Stash Finder
        StashFinder stashFinder = Modules.get(StashFinder.class);
        if (stashFinder != null && stashFinder.isEnabled()) {
            int count = stashFinder.getDiscoveredCount();
            if (count > 0) {
                StashFinder.DiscoveredStash nearest = stashFinder.getNearestStash();
                double dist = nearest != null && mc.player != null
                        ? Math.sqrt(mc.player.distanceToSqr(nearest.centerPos().getX() + 0.5, nearest.centerPos().getY() + 0.5, nearest.centerPos().getZ() + 0.5))
                        : 0;
                String val = String.format("%d vaults (Nearest: %.0fm)", count, dist);
                rows.add(new TelemetryRow("📦", "Stashes", val, 0xFF33FFAA));
            } else {
                rows.add(new TelemetryRow("📦", "Stashes", "Scanning containers...", 0xFFAAAAAA));
            }
        }

        // 5. Auto Base Dig
        AutoBaseDig baseDig = Modules.get(AutoBaseDig.class);
        if (baseDig != null && baseDig.isEnabled()) {
            BlockPos target = baseDig.getCurrentTarget();
            String val = target != null
                    ? String.format("Mining [%d, %d, %d]", target.getX(), target.getY(), target.getZ())
                    : "Searching...";
            rows.add(new TelemetryRow("⛏️", "Base Dig", val, 0xFFFF9933));
        }

        // 6. AH Sniper
        AHSniper ahSniper = Modules.get(AHSniper.class);
        if (ahSniper != null && ahSniper.isEnabled()) {
            String val = String.format("%s (< %s) • ARMED", ahSniper.getTargetFilter(), ahSniper.getMaxPrice());
            rows.add(new TelemetryRow("🎯", "AH Sniper", val, 0xFF00FF66));
        }

        // 7. Player Detect
        PlayerDetect playerDetect = Modules.get(PlayerDetect.class);
        if (playerDetect != null && playerDetect.isEnabled()) {
            boolean enemy = playerDetect.isEnemyDetected();
            String val = enemy ? "🚨 ENEMY SPOTTED" : "Scanning (Safe)";
            int col = enemy ? 0xFFFF2222 : 0xFF55FF55;
            rows.add(new TelemetryRow("🛡️", "Radar", val, col));
        }

        // 8. Silent Home
        SilentHome silentHome = Modules.get(SilentHome.class);
        if (silentHome != null && silentHome.isEnabled()) {
            rows.add(new TelemetryRow("🏠", "Home", "Trigger Armed", 0xFF55FFFF));
        }

        // 9. Extra ESP
        ExtraESP extraEsp = Modules.get(ExtraESP.class);
        if (extraEsp != null && extraEsp.isEnabled()) {
            rows.add(new TelemetryRow("👁️", "Extra ESP", "Scanning Life", 0xFF630CE6));
        }

        // 10. Hole ESP
        HoleESP holeEsp = Modules.get(HoleESP.class);
        if (holeEsp != null && holeEsp.isEnabled()) {
            rows.add(new TelemetryRow("🕳️", "Hole ESP", "Scanning Shafts", 0xFFFF0055));
        }

        // 11. Beehive ESP
        BeehiveESP beehive = Modules.get(BeehiveESP.class);
        if (beehive != null && beehive.isEnabled()) {
            rows.add(new TelemetryRow("🐝", "Beehives", "Scanning Honey", 0xFFFFC800));
        }

        return rows;
    }

    private String resolveArrowChar(float targetYaw) {
        if (mc.player == null) return "↑";
        float diff = (float) Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        if (diff >= -22.5f && diff < 22.5f) return "↑";
        if (diff >= 22.5f && diff < 67.5f) return "↗";
        if (diff >= 67.5f && diff < 112.5f) return "→";
        if (diff >= 112.5f && diff < 157.5f) return "↘";
        if (diff >= 157.5f || diff < -157.5f) return "↓";
        if (diff >= -157.5f && diff < -112.5f) return "↙";
        if (diff >= -112.5f && diff < -67.5f) return "←";
        return "↖";
    }
}
