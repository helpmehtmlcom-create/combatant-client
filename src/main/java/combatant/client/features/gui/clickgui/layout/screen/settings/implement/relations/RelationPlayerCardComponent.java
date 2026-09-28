/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Relation browser row. The component owns the per-player hover/selection animation so
 * Relations can rebuild/filter its list without losing the visual state of a row.
 */
public final class RelationPlayerCardComponent {
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private final Map<String, Float> selectedAnim = new HashMap<>();
    private final Map<String, Float> deleteHoverAnim = new HashMap<>();

    public CardHit renderRow(String name,
                             String relationLabel,
                             int relationColor,
                             boolean selected,
                             float x,
                             float y,
                             float w,
                             float h,
                             float mx,
                             float my,
                             float scale,
                             SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);

        String animationKey = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        float dt = AnimationUtility.deltaTime();
        float hoverValue = approach(hoverAnim.getOrDefault(animationKey, 0f), hover, dt, 11f);
        float selectedValue = approach(selectedAnim.getOrDefault(animationKey, selected ? 1f : 0f), selected, dt, 12f);
        hoverAnim.put(animationKey, hoverValue);
        selectedAnim.put(animationKey, selectedValue);

        float emphasis = Math.max(selectedValue, hoverValue * 0.72f);
        float radius = 5.5f * scale;
        if (emphasis > 0.01f) {
            LayoutRender2D.roundedSoftShadow(
                    x + 0.2f * scale,
                    y + 0.7f * scale,
                    w - 0.4f * scale,
                    h,
                    radius,
                    (3.2f + emphasis * 2.0f) * scale,
                    0.015f + emphasis * 0.018f,
                    SettingsGuiPalette.withAlpha(
                            SettingsGuiPalette.mix(0xFF000000, relationColor | 0xFF000000, selectedValue * 0.38f),
                            Math.round(48f + 34f * emphasis)
                    )
            );
        }

        int baseA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(
                        palette.controlSurface(),
                        relationColor,
                        0.018f + selectedValue * 0.105f + hoverValue * 0.022f
                ),
                Math.round(94f + hoverValue * 30f + selectedValue * 38f)
        );
        int baseB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(
                        palette.controlSurfaceHover(),
                        relationColor,
                        0.032f + selectedValue * 0.155f + hoverValue * 0.055f
                ),
                Math.round(100f + hoverValue * 34f + selectedValue * 42f)
        );
        int baseC = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(
                        SettingsGuiPalette.darken(palette.controlSurface(), 0.08f),
                        relationColor,
                        selectedValue * 0.085f + hoverValue * 0.020f
                ),
                Math.round(96f + hoverValue * 26f + selectedValue * 34f)
        );
        LayoutRender2D.roundedQuad(x, y, w, h, radius, baseA, baseB, baseC, baseA);

        int stroke = selectedValue > 0.01f
                ? SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.panelStroke(), relationColor, 0.42f + selectedValue * 0.24f),
                        Math.round(128f + 82f * selectedValue)
                )
                : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), Math.round(58f + 52f * hoverValue));
        LayoutRender2D.roundedStroke(x, y, w, h, radius, (0.48f + selectedValue * 0.10f) * scale, stroke);

        if (selectedValue > 0.01f) {
            float accentW = (1.7f + 0.45f * selectedValue) * scale;
            float accentH = Math.max(5f * scale, h - 11f * scale);
            LayoutRender2D.roundedQuad(
                    x + 1.1f * scale,
                    y + (h - accentH) * 0.5f,
                    accentW,
                    accentH,
                    accentW * 0.5f,
                    SettingsGuiPalette.withAlpha(relationColor, Math.round(132f + 100f * selectedValue)),
                    SettingsGuiPalette.withAlpha(relationColor, Math.round(170f + 70f * selectedValue)),
                    SettingsGuiPalette.withAlpha(relationColor, Math.round(148f + 74f * selectedValue)),
                    SettingsGuiPalette.withAlpha(relationColor, Math.round(118f + 90f * selectedValue))
            );
        }

        float head = Math.min(h - 9f * scale, 20f * scale);
        float headX = x + 7f * scale;
        float headY = y + (h - head) * 0.5f;
        renderPortrait(name, headX, headY, head, 4.5f * scale, scale, palette, 1f);

        float relationDot = 4.7f * scale;
        float dotX = headX + head - relationDot * 0.76f;
        float dotY = headY + head - relationDot * 0.76f;
        Renderer2D.COLOR.roundedRect(
                dotX,
                dotY,
                relationDot,
                relationDot,
                relationDot * 0.5f,
                0.7f,
                SettingsGuiPalette.withAlpha(relationColor, 244)
        );
        Renderer2D.COLOR.roundedRectStroke(
                dotX,
                dotY,
                relationDot,
                relationDot,
                relationDot * 0.5f,
                0.7f,
                0.55f * scale,
                LayoutRender2D.alpha(palette.panelBgLeft(), 0.86f)
        );

        float deleteSize = 16f * scale;
        float deleteX = x + w - deleteSize - 5f * scale;
        float deleteY = y + (h - deleteSize) * 0.5f;
        boolean deleteVisible = hoverValue > 0.04f || selectedValue > 0.04f;
        boolean deleteHover = deleteVisible && ClickGuiMath.insideRect(mx, my, deleteX, deleteY, deleteSize, deleteSize);
        float deleteHoverValue = approach(deleteHoverAnim.getOrDefault(animationKey, 0f), deleteHover, dt, 13f);
        deleteHoverAnim.put(animationKey, deleteHoverValue);

        float textX = headX + head + 7f * scale;
        float rightEdge = deleteVisible ? deleteX - 5f * scale : x + w - 8f * scale;
        float titleW = Math.max(1f, rightEdge - textX);
        float titleSize = 8.0f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), name, titleSize, titleW);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                textX,
                y + 6.0f * scale,
                titleSize,
                palette.moduleTitleText(),
                false
        );

        float metaSize = 5.65f * scale;
        String meta = ClickGuiRenderer.fitText(
                ClickGuiRenderer.getInterRegular(),
                relationLabel == null ? "" : relationLabel,
                metaSize,
                titleW
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                meta,
                textX,
                y + 18.6f * scale,
                metaSize,
                SettingsGuiPalette.mix(palette.panelMuted(), relationColor | 0xFF000000, 0.34f + selectedValue * 0.12f),
                false
        );

        if (deleteVisible) {
            float visible = Math.max(hoverValue, selectedValue);
            if (deleteHoverValue > 0.01f) {
                int danger = 0xFFFF6B6B;
                int bgA = SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurfaceHover(), danger, 0.04f + 0.10f * deleteHoverValue),
                        Math.round(112f + 30f * deleteHoverValue)
                );
                int bgB = SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurface(), danger, 0.025f + 0.055f * deleteHoverValue),
                        Math.round(104f + 24f * deleteHoverValue)
                );
                LayoutRender2D.roundedQuad(deleteX, deleteY, deleteSize, deleteSize, 4.2f * scale, bgA, bgA, bgB, bgB);
            }
            float icon = 7.2f * scale;
            int danger = 0xFFFF6B6B;
            int idleIcon = SettingsGuiPalette.withAlpha(
                    SettingsGuiPalette.mix(palette.panelMuted(), danger, 0.28f + selectedValue * 0.18f),
                    Math.round(120f + 120f * visible)
            );
            int hoverIcon = SettingsGuiPalette.mix(palette.menuCategoryText(), danger, 0.58f);
            int iconColor = SettingsGuiPalette.mix(idleIcon, hoverIcon, deleteHoverValue);
            Renderer2D.COLOR.svg(
                    "trash-2",
                    deleteX + (deleteSize - icon) * 0.5f,
                    deleteY + (deleteSize - icon) * 0.5f,
                    icon,
                    icon,
                    SvgRenderOptions.overrideColor(iconColor)
            );
        }

        return new CardHit(x, y, w, h, deleteX, deleteY, deleteSize, deleteSize, deleteVisible);
    }

    void renderPortrait(String name,
                        float x,
                        float y,
                        float size,
                        float radius,
                        float scale,
                        SettingsGuiPalette palette,
                        float opacity) {
        Identifier skin = SkinManager.getSkin(name);
        GuiGraphicsExtractor ctx = ViewportContext.getCurrentContext();
        if (skin != null && ctx != null) {
            int alpha = Math.round(255f * AnimationUtility.clamp01(opacity));
            PlayerHeadRenderer.drawRounded(
                    ctx,
                    x,
                    y,
                    size,
                    radius,
                    skin,
                    new RenderColor(255, 255, 255, alpha),
                    true,
                    new RenderColor(255, 255, 255, Math.round(42f * AnimationUtility.clamp01(opacity))),
                    0.65f * scale,
                    false
            );
            return;
        }

        Renderer2D.COLOR.roundedRect(
                x,
                y,
                size,
                size,
                radius,
                1.0f,
                LayoutRender2D.alpha(palette.panelMuted(), 0.20f * opacity)
        );
        Renderer2D.COLOR.roundedRectStroke(
                x,
                y,
                size,
                size,
                radius,
                1.0f,
                0.5f * scale,
                LayoutRender2D.alpha(palette.panelMuted(), 0.56f * opacity)
        );
    }

    private static float approach(float current, boolean target, float dt, float speed) {
        float goal = target ? 1f : 0f;
        return AnimationUtility.snap(AnimationUtility.approach(current, goal, dt, speed), goal, 0.005f);
    }

    public record CardHit(float x,
                          float y,
                          float w,
                          float h,
                          float deleteX,
                          float deleteY,
                          float deleteW,
                          float deleteH,
                          boolean deleteVisible) {
        public boolean contains(float mx, float my) {
            return ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }

        public boolean containsDelete(float mx, float my) {
            return deleteVisible && ClickGuiMath.insideRect(mx, my, deleteX, deleteY, deleteW, deleteH);
        }
    }
}
