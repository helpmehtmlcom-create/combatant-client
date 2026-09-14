/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import combatant.client.features.account.SkinManager;
import com.mojang.authlib.GameProfile;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.util.player.PlayerSkinResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Shared player marker renderer used by local Xaero contacts and unified location overlays. */
public final class MapPlayerMarkerRenderer {
    private MapPlayerMarkerRenderer() {}

    public static float draw(float centerX,
                             float centerY,
                             UUID playerUuid,
                             String playerName,
                             int accentArgb,
                             String sourceGlyph,
                             float size,
                             float alpha) {
        float extent = drawMarker(centerX, centerY, playerUuid, playerName, accentArgb, sourceGlyph, size, alpha);
        Renderer2D.flushBatch();
        drawLabel(centerX, centerY, playerUuid, playerName, accentArgb, size, alpha);
        return extent;
    }

    /** Emits only marker geometry. Labels are intentionally a separate pass. */
    public static float drawMarker(float centerX,
                                   float centerY,
                                   UUID playerUuid,
                                   String playerName,
                                   int accentArgb,
                                   String sourceGlyph,
                                   float size,
                                   float alpha) {
        alpha = clamp01(alpha);
        if (alpha <= 0.001f || size <= 0.0f) return 0.0f;
        String name = resolveDisplayName(playerUuid, playerName);
        int accent = withAlpha(accentArgb, alpha);
        Identifier skin = resolveSkin(playerUuid, name);
        float x = centerX - size * 0.5f;
        float y = centerY - size * 0.5f;
        float radius = Math.max(4.5f, size * 0.24f);

        Renderer2D.COLOR.roundedRect(x - 1.4f, y - 1.4f, size + 2.8f, size + 2.8f,
                radius + 1.4f, withAlpha(accentArgb, 0.23f * alpha));
        PlayerHeadRenderer.drawRounded(null, x, y, size, radius, skin,
                new RenderColor(255, 255, 255, Math.round(255.0f * alpha)), true,
                new RenderColor(accent), Math.max(0.85f, size * 0.045f), false);

        if (sourceGlyph != null && !sourceGlyph.isBlank()) {
            float badge = Math.max(9.0f, size * 0.33f);
            float bx = x + size - badge * 0.72f;
            float by = y + size - badge * 0.72f;
            Renderer2D.COLOR.circle(bx + badge * 0.5f, by + badge * 0.5f, badge * 0.58f,
                    withAlpha(0xFF091018, 0.92f * alpha));
            Renderer2D.COLOR.circleStroke(bx + badge * 0.5f, by + badge * 0.5f, badge * 0.58f,
                    0.8f, withAlpha(accentArgb, 0.9f * alpha));
            Renderer2D.COLOR.svg(sourceGlyph, bx + 1.4f, by + 1.4f, badge - 2.8f, badge - 2.8f,
                    SvgRenderOptions.overrideColor(withAlpha(accentArgb, alpha)));
        }
        return size * 0.5f;
    }

    /** Emits the identity plate above an already rendered marker. */
    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 int accentArgb,
                                 float size,
                                 float alpha) {
        drawLabel(centerX, centerY, playerUuid, playerName, "", accentArgb, size, alpha);
    }

    public static void drawLabel(float centerX,
                                 float centerY,
                                 UUID playerUuid,
                                 String playerName,
                                 String statusLabel,
                                 int accentArgb,
                                 float size,
                                 float alpha) {
        alpha = clamp01(alpha);
        if (alpha <= 0.001f || size <= 0.0f) return;
        String name = resolveDisplayName(playerUuid, playerName);
        if (name.isBlank()) return;
        String suffix = statusLabel == null ? "" : statusLabel.trim();
        String label = suffix.isBlank() ? name : name + " · " + suffix;

        float y = centerY - size * 0.5f;
        float textSize = Math.max(10.5f, size * 0.39f);
        float textW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), label, textSize);
        float padX = 5.5f;
        float labelH = textSize + 5.0f;
        float labelW = textW + padX * 2.0f;
        float labelX = centerX - labelW * 0.5f;
        float labelY = y - labelH - 3.0f;
        Renderer2D.COLOR.roundedRect(labelX, labelY, labelW, labelH, 5.5f,
                withAlpha(accentArgb, 0.24f * alpha));
        Renderer2D.COLOR.roundedRectStroke(labelX, labelY, labelW, labelH, 5.5f, 1.0f, 0.7f,
                withAlpha(accentArgb, 0.72f * alpha));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), label,
                labelX + padX, labelY + 2.1f, textSize, withAlpha(0xFFF7FAFC, alpha), false);
    }

    /** Resolve the visible player identity centrally so every map source gets the same label. */
    public static String resolveDisplayName(UUID id, String fallback) {
        String cleanFallback = fallback == null ? "" : fallback.trim();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && id != null) {
            if (mc.level != null) {
                net.minecraft.world.entity.player.Player player = mc.level.getPlayerByUUID(id);
                if (player != null) {
                    if (player.getGameProfile() != null && player.getGameProfile().name() != null
                            && !player.getGameProfile().name().isBlank()) {
                        return player.getGameProfile().name();
                    }
                    if (player.getName() != null && !player.getName().getString().isBlank()) {
                        return player.getName().getString();
                    }
                }
            }
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(id);
                if (info != null && info.getProfile() != null && info.getProfile().name() != null
                        && !info.getProfile().name().isBlank()) {
                    return info.getProfile().name();
                }
            }
        }
        return cleanFallback;
    }

    private static Identifier resolveSkin(UUID id, String name) {
        Identifier cached = PlayerHeadRenderer.getCachedSkin(id);
        if (cached == null && !name.isBlank()) cached = PlayerHeadRenderer.getCachedSkin(name);
        Minecraft mc = Minecraft.getInstance();
        if (cached == null && mc != null && mc.getConnection() != null && id != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile() != null) {
                Identifier runtime = PlayerSkinResolver.resolveProfileSkin(info.getProfile());
                cached = PlayerHeadRenderer.resolveCachedSkin(id, info.getProfile().name(), runtime);
            }
        }
        if (cached == null && id != null) {
            Identifier resolved = PlayerSkinResolver.resolveProfileSkin(new GameProfile(id, name.isBlank() ? "Player" : name));
            cached = PlayerHeadRenderer.resolveCachedSkin(id, name, resolved);
        }
        if (cached == null && !name.isBlank()) {
            cached = SkinManager.getSkin(name);
            cached = PlayerHeadRenderer.resolveCachedSkin(id, name, cached);
        }
        return cached;
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0f))) << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
