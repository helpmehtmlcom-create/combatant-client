/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBoxShape;
import combatant.client.render.engine.renderer.ui.draw.UiCompoundSdf;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.svg.SvgRenderOptions;

import java.util.EnumMap;
import java.util.List;

/**
 * Visual ownership boundary for the map screen chrome.
 *
 * <p>The terrain/viewport pipeline stays in {@link XaeroMapSurface}. Buttons, info cards,
 * browser/drawer presentation and context-menu presentation live here so UI work can be done
 * without touching map rendering code.</p>
 */
final class XaeroMapUiRenderer {
    private static final float MAP_INFO_FONT_SIZE = 18.0f;
    private static final float MAP_INFO_PAD_X = 14.0f;
    private static final float MAP_INFO_PAD_Y = 8.0f;
    private static final float MAP_INFO_RADIUS = 10.0f;
    private static final float MAP_INFO_EDGE_INSET = 18.0f;
    private static final float MAP_INFO_TOP_GAP = 10.0f;
    private static final float CONTROL_ISLAND_GAP = 7.0f;
    private static final float CONTROL_GROUP_GAP = 12.0f;
    private static final float CONTROL_SQUIRCLE_EXPONENT = 3.05f;

    private XaeroMapUiRenderer() {
    }


    static void layoutButtons(List<XaeroMapSurface.UiButton> out,
                              float areaX, float areaY, float areaWidth, float areaHeight,
                              ChromeState state) {
        out.clear();
        float size = 42.0f;
        float inset = 18.0f;

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.SETTINGS, "settings-2",
                areaX + inset, areaY + inset, size, state.settingsTooltip(), state.settingsOpen()));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RECENTER, "locate-fixed",
                areaX + inset + size + CONTROL_ISLAND_GAP, areaY + inset, size, state.recenterTooltip(), false));

        float leftBottom = areaY + areaHeight - inset - size;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CAVE, "layers",
                areaX + inset, leftBottom, size, state.caveTooltip(), state.caveActive()));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.DIMENSION, "route",
                areaX + inset, leftBottom - size - CONTROL_ISLAND_GAP, size,
                state.dimensionTooltip(), state.customDimension()));

        // Right-side chrome is intentionally split into small semantic islands rather than
        // one long glass rail. Inside an island the lobes sit close enough for a metaball neck;
        // between islands there is enough air that the groups remain readable at a glance.
        float right = areaX + areaWidth - inset - size;
        float cursor = areaY + areaHeight - inset - size;

        if (state.showWaypoints()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.WAYPOINTS, "map-pinned",
                    right, cursor, size, state.waypointsTooltip(),
                    state.drawer() == XaeroMapSurface.Drawer.WAYPOINTS));
            cursor -= size + CONTROL_ISLAND_GAP;
        }
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.PLAYERS, "users-round",
                right, cursor, size, state.playersTooltip(),
                state.drawer() == XaeroMapSurface.Drawer.PLAYERS));
        cursor -= size + CONTROL_GROUP_GAP;

        boolean hasOverlayGroup = false;
        if (state.showRadar()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RADAR, "radar",
                    right, cursor, size, state.radarTooltip(), state.radarActive()));
            cursor -= size + CONTROL_ISLAND_GAP;
            hasOverlayGroup = true;
        }
        if (state.showClaims()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CLAIMS, "land-plot",
                    right, cursor, size, state.claimsTooltip(), state.claimsActive()));
            cursor -= size + CONTROL_ISLAND_GAP;
            hasOverlayGroup = true;
        }
        if (hasOverlayGroup) {
            cursor += CONTROL_ISLAND_GAP - CONTROL_GROUP_GAP;
        }

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.EXPORT, "map",
                right, cursor, size, state.exportTooltip(), false));
        cursor -= size + CONTROL_ISLAND_GAP;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CONTROLS, "circle-question-mark",
                right, cursor, size, state.controlsTooltip(), false));
        cursor -= size + CONTROL_GROUP_GAP;

        if (state.showZoomButtons()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_OUT, "zoom-out",
                    right, cursor, size, state.zoomOutTooltip(), false));
            cursor -= size + CONTROL_ISLAND_GAP;
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_IN, "zoom-in",
                    right, cursor, size, state.zoomInTooltip(), false));
        }
    }

    static void drawZoom(float areaX, float areaY, float areaWidth, float areaHeight,
                         double destinationScale, SettingsGuiPalette palette) {
        String zoom = Math.round(destinationScale * 1000.0) / 1000.0 + "x";
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), zoom, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = areaY + areaHeight - MAP_INFO_EDGE_INSET - cardHeight;
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getOnestMedium(), zoom,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    /**
     * Draws all map chrome controls through one interaction/material system. Each semantic pair
     * is a true smooth union of two animated squircle lobes; missing optional controls collapse
     * to a single animated squircle without changing the rest of the stack.
     */
    static void drawControlChrome(List<XaeroMapSurface.UiButton> buttons,
                                  float mouseX, float mouseY,
                                  XaeroMapSurface.Action pressedAction,
                                  SettingsGuiPalette palette,
                                  ChromeMotion motion) {
        for (XaeroMapSurface.UiButton button : buttons) {
            motion.control(button.action()).update(
                    button, mouseX, mouseY, pressedAction == button.action(), button.active());
        }

        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.SETTINGS, XaeroMapSurface.Action.RECENTER);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.DIMENSION, XaeroMapSurface.Action.CAVE);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.WAYPOINTS, XaeroMapSurface.Action.PLAYERS);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.RADAR, XaeroMapSurface.Action.CLAIMS);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.EXPORT, XaeroMapSurface.Action.CONTROLS);
        drawControlIsland(buttons, palette, motion,
                XaeroMapSurface.Action.ZOOM_OUT, XaeroMapSurface.Action.ZOOM_IN);
    }

    private static void drawControlIsland(List<XaeroMapSurface.UiButton> buttons,
                                          SettingsGuiPalette palette,
                                          ChromeMotion motion,
                                          XaeroMapSurface.Action firstAction,
                                          XaeroMapSurface.Action secondAction) {
        XaeroMapSurface.UiButton first = findButton(buttons, firstAction);
        XaeroMapSurface.UiButton second = findButton(buttons, secondAction);
        if (first == null && second == null) return;

        XaeroMapSurface.UiButton aButton = first != null ? first : second;
        XaeroMapSurface.UiButton bButton = first != null && second != null ? second : null;
        ControlMotion aMotion = motion.control(aButton.action());
        ControlMotion bMotion = bButton != null ? motion.control(bButton.action()) : null;
        VisualControl a = visual(aButton, aMotion);
        VisualControl b = bButton != null ? visual(bButton, bMotion) : null;

        float hoverA = clamp01(aMotion.hover.value);
        float hoverB = bMotion != null ? clamp01(bMotion.hover.value) : 0.0f;
        float pressA = clamp01(aMotion.press.value);
        float pressB = bMotion != null ? clamp01(bMotion.press.value) : 0.0f;
        float groupHover = Math.max(hoverA, hoverB);
        float groupPress = Math.max(pressA, pressB);

        Renderer2D renderer = Renderer2D.COLOR;

        // blurAlpha is the opacity of the prepared blur layer, not the blur radius/quality.
        // Keeping it below 1.0 preserves recognizable terrain while the source is still fully blurred.
        int glassBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(),
                0.055f + groupHover * 0.055f);
        int glassTint = SettingsGuiPalette.withAlpha(
                glassBase, Math.round(114.0f + groupHover * 22.0f));
        int neutralGlow = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(34.0f + 18.0f * groupHover));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.038f + groupHover * 0.042f,
                        4.75f + groupHover * 1.15f, neutralGlow);
        float glassAlpha = 0.52f + groupHover * 0.065f + groupPress * 0.020f;
        float blurAlpha = 0.76f + groupHover * 0.10f + groupPress * 0.025f;

        renderer.withLiquidGlassMaterial(material, () -> {
            if (b != null) {
                // The idle neck is restrained. Hover/press adds mass so the transition is
                // geometric rather than merely a color change.
                float smoothing = 15.0f + groupHover * 5.5f + groupPress * 2.25f;
                UiCompoundSdf field = UiCompoundSdf.smoothSquircleUnion(
                        UiRect.of(a.x, a.y, a.size, a.size), CONTROL_SQUIRCLE_EXPONENT,
                        UiRect.of(b.x, b.y, b.size, b.size), CONTROL_SQUIRCLE_EXPONENT,
                        smoothing
                );
                renderer.liquidGlassCompound(field, glassTint, glassAlpha, blurAlpha,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL);
            } else {
                // Use the explicit whole-box squircle path. Passing a positive squircle
                // exponent to liquidGlassRect is rounded-rect smoothness and radius=0 therefore
                // degenerates to a square (most visible when Radar is the only overlay control).
                renderer.liquidGlassSquircle(
                        UiBoxShape.squircle(a.x, a.y, a.size, a.size, CONTROL_SQUIRCLE_EXPONENT),
                        glassTint, glassAlpha, blurAlpha,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL);
            }
        });

        drawControlActiveOverlay(renderer, aButton, a, aMotion);
        if (bButton != null) {
            drawControlActiveOverlay(renderer, bButton, b, bMotion);
        }

        drawControlIcon(renderer, aButton, a, aMotion, palette);
        if (bButton != null) {
            drawControlIcon(renderer, bButton, b, bMotion, palette);
        }
    }

    private static void drawControlActiveOverlay(Renderer2D renderer,
                                                 XaeroMapSurface.UiButton button,
                                                 VisualControl visual,
                                                 ControlMotion motion) {
        float active = clamp01(motion.active.value);
        if (!button.active() && active <= 0.001f) return;
        int accent = Themes.hudAccentGradient().start();
        int activeFill = SettingsGuiPalette.withAlpha(
                accent, Math.round(10.0f + 22.0f * active));
        renderer.box(UiBoxShape.squircle(
                        visual.x, visual.y, visual.size, visual.size, CONTROL_SQUIRCLE_EXPONENT),
                UiPaint.solid(activeFill));
    }

    private static void drawControlIcon(Renderer2D renderer,
                                        XaeroMapSurface.UiButton button,
                                        VisualControl visual,
                                        ControlMotion motion,
                                        SettingsGuiPalette palette) {
        float hover = clamp01(motion.hover.value);
        float press = clamp01(motion.press.value);
        float active = clamp01(motion.active.value);
        float iconScale = 1.0f + hover * 0.055f - press * 0.065f + active * 0.018f;
        float iconSize = button.size() * 0.50f * iconScale;
        float iconPullX = motion.pointerX.value * hover * 0.75f;
        float iconPullY = motion.pointerY.value * hover * 0.75f;
        int iconColor = SettingsGuiPalette.mix(
                palette.panelMuted(), palette.panelText(), Math.max(hover, active));
        renderer.svg(button.icon(),
                visual.centerX - iconSize * 0.5f + iconPullX,
                visual.centerY - iconSize * 0.5f + iconPullY,
                iconSize, iconSize,
                SvgRenderOptions.overrideColor(iconColor));
    }

    private static VisualControl visual(XaeroMapSurface.UiButton button, ControlMotion motion) {
        float hover = clamp01(motion.hover.value);
        float press = clamp01(motion.press.value);
        float active = clamp01(motion.active.value);
        float scale = 1.0f + hover * 0.040f - press * 0.060f + active * 0.010f;
        float size = button.size() * scale;
        float magneticX = motion.pointerX.value * hover * 1.55f;
        float magneticY = motion.pointerY.value * hover * 1.55f;
        float centerX = button.x() + button.size() * 0.5f + magneticX;
        float centerY = button.y() + button.size() * 0.5f + magneticY;
        return new VisualControl(centerX - size * 0.5f, centerY - size * 0.5f,
                size, centerX, centerY);
    }

    private static XaeroMapSurface.UiButton findButton(List<XaeroMapSurface.UiButton> buttons,
                                                        XaeroMapSurface.Action action) {
        for (XaeroMapSurface.UiButton button : buttons) {
            if (button.action() == action) return button;
        }
        return null;
    }

    static final class ChromeMotion {
        private final EnumMap<XaeroMapSurface.Action, ControlMotion> controls =
                new EnumMap<>(XaeroMapSurface.Action.class);
        private final TooltipMotion tooltip = new TooltipMotion();

        ControlMotion control(XaeroMapSurface.Action action) {
            return controls.computeIfAbsent(action, ignored -> new ControlMotion());
        }
    }

    private static final class ControlMotion {
        final Spring hover = new Spring();
        final Spring press = new Spring();
        final Spring active = new Spring();
        final Spring pointerX = new Spring();
        final Spring pointerY = new Spring();

        void update(XaeroMapSurface.UiButton button,
                    float mouseX, float mouseY,
                    boolean pressed,
                    boolean activeState) {
            boolean hovered = button.contains(mouseX, mouseY);
            float targetX = 0.0f;
            float targetY = 0.0f;
            if (hovered) {
                float half = Math.max(1.0f, button.size() * 0.5f);
                targetX = clamp((mouseX - (button.x() + half)) / half, -1.0f, 1.0f);
                targetY = clamp((mouseY - (button.y() + half)) / half, -1.0f, 1.0f);
            }
            float dt = Math.min(1.0f / 30.0f, Math.max(1.0f / 240.0f, AnimationUtility.deltaTime()));
            hover.step(hovered ? 1.0f : 0.0f, dt, 180.0f, 20.0f);
            press.step(pressed ? 1.0f : 0.0f, dt, 260.0f, 18.0f);
            active.step(activeState ? 1.0f : 0.0f, dt, 135.0f, 20.0f);
            pointerX.step(targetX, dt, 150.0f, 22.0f);
            pointerY.step(targetY, dt, 150.0f, 22.0f);
        }
    }

    private static final class Spring {
        float value;
        float velocity;

        void step(float target, float dt, float stiffness, float damping) {
            velocity += (target - value) * stiffness * dt;
            velocity *= (float) Math.exp(-damping * dt);
            value += velocity * dt;
            if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.0005f) {
                value = target;
                velocity = 0.0f;
            }
        }
    }

    private record VisualControl(float x, float y, float size, float centerX, float centerY) {
    }

    static void drawTooltip(float areaX, float areaY, float areaWidth, float areaHeight,
                            float mouseX, float mouseY,
                            List<XaeroMapSurface.UiButton> buttons,
                            boolean blocked,
                            SettingsGuiPalette palette,
                            ChromeMotion motion) {
        XaeroMapSurface.UiButton hovered = null;
        if (!blocked) {
            for (XaeroMapSurface.UiButton button : buttons) {
                if (button.contains(mouseX, mouseY)) {
                    hovered = button;
                    break;
                }
            }
        }

        motion.tooltip.update(hovered != null ? hovered.action() : null);
        XaeroMapSurface.Action renderedAction = motion.tooltip.renderedAction;
        float reveal = clamp01(motion.tooltip.reveal.value);
        if (renderedAction == null || reveal <= 0.005f) return;

        XaeroMapSurface.UiButton anchor = findButton(buttons, renderedAction);
        if (anchor == null || anchor.tooltip() == null || anchor.tooltip().isBlank()) return;

        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        float fontSize = 14.5f;
        float textWidth = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), anchor.tooltip(), fontSize);

        float height = 31.0f;
        float tip = 7.0f;
        float bodyPad = 10.0f;
        float totalWidth = textWidth + bodyPad * 2.0f + tip;
        float anchorCenterX = anchor.x() + anchor.size() * 0.5f;
        float anchorCenterY = anchor.y() + anchor.size() * 0.5f;
        boolean opensRight = anchorCenterX < areaX + areaWidth * 0.5f;

        float travel = (1.0f - eased) * 7.0f;
        float x = opensRight
                ? anchor.x() + anchor.size() + 9.0f - travel
                : anchor.x() - totalWidth - 9.0f + travel;
        float y = anchorCenterY - height * 0.5f + (1.0f - eased) * 2.0f;
        x = clamp(x, areaX + 6.0f, areaX + areaWidth - totalWidth - 6.0f);
        y = clamp(y, areaY + 6.0f, areaY + areaHeight - height - 6.0f);

        // Five vertices: the tip is part of the same analytic primitive as the tooltip body.
        // This is deliberately not a rounded-rect plus a separately drawn triangle, so the
        // refraction/rim remains continuous across the pointer.
        UiPrimitive tooltipShape = opensRight
                ? UiPrimitive.builder(x, y, totalWidth, height)
                    .customConvex(
                            0.0, 0.50,
                            tip / totalWidth, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tip / totalWidth, 1.0)
                    .rounding(4.8f)
                    .build()
                : UiPrimitive.builder(x, y, totalWidth, height)
                    .customConvex(
                            0.0, 0.0,
                            1.0 - tip / totalWidth, 0.0,
                            1.0, 0.50,
                            1.0 - tip / totalWidth, 1.0,
                            0.0, 1.0)
                    .rounding(4.8f)
                    .build();

        Renderer2D renderer = Renderer2D.COLOR;
        int glassBase = SettingsGuiPalette.mix(palette.panelBgRight(),
                palette.controlSurfaceHover(), 0.08f);
        int tint = SettingsGuiPalette.withAlpha(glassBase, Math.round(112.0f * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(30.0f * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.045f * eased, 4.4f, inner);
        renderer.withLiquidGlassMaterial(material, () ->
                renderer.liquidGlassPrimitive(
                        tooltipShape, tint,
                        0.50f * eased,
                        0.76f * eased,
                        Renderer2D.LiquidGlassPreset.HUD_SMALL));

        // A very small directional gleam at the body-side edge makes the pointer shape read
        // against both ocean and terrain without adding a conventional border.
        int gleam = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(26.0f * eased));
        float gleamX = opensRight ? x + tip + 1.5f : x + totalWidth - tip - 2.5f;
        renderer.roundedRect(gleamX, y + 7.0f, 1.0f, height - 14.0f, 0.5f, gleam);

        int textColor = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(255.0f * eased));
        float textX = opensRight ? x + tip + bodyPad : x + bodyPad;
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), anchor.tooltip(),
                textX, y + 8.25f, fontSize, textColor, false);
    }

    private static final class TooltipMotion {
        final Spring reveal = new Spring();
        XaeroMapSurface.Action renderedAction;

        void update(XaeroMapSurface.Action hoveredAction) {
            if (hoveredAction != null && hoveredAction != renderedAction) {
                renderedAction = hoveredAction;
                // Changing directly between neighbouring buttons should feel like a tooltip
                // relocating, not like an unrelated popup being destroyed and recreated.
                reveal.value = Math.min(reveal.value, 0.38f);
                reveal.velocity *= 0.25f;
            }
            float dt = Math.min(1.0f / 30.0f,
                    Math.max(1.0f / 240.0f, AnimationUtility.deltaTime()));
            reveal.step(hoveredAction != null ? 1.0f : 0.0f, dt, 205.0f, 22.0f);
            if (hoveredAction == null && reveal.value <= 0.002f && Math.abs(reveal.velocity) < 0.002f) {
                renderedAction = null;
            }
        }
    }

    static void drawCompass(float areaX, float areaY, float areaWidth, float areaHeight,
                            String north, String east, String south, String west,
                            SettingsGuiPalette palette) {
        float centerX = areaX + areaWidth * 0.5f;
        float centerY = areaY + areaHeight * 0.5f;
        float edgeInset = 102.0f;
        drawCardinal(north, centerX, areaY + edgeInset, palette);
        drawCardinal(south, centerX, areaY + areaHeight - edgeInset, palette);
        drawCardinal(west, areaX + edgeInset, centerY, palette);
        drawCardinal(east, areaX + areaWidth - edgeInset, centerY, palette);
    }

    private static void drawCardinal(String text, float centerX, float centerY,
                                     SettingsGuiPalette palette) {
        float size = 16.0f;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), text, size);
        Renderer2D.COLOR.roundedRect(centerX - width * 0.5f - 8.0f,
                centerY - 5.0f, width + 16.0f, size + 10.0f,
                8.0f, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), text,
                centerX - width * 0.5f, centerY, size, palette.panelText(), false);
    }

    static void drawCoordinates(float areaX, float areaY, float areaWidth,
                                String coordinates, float chromeBottom,
                                SettingsGuiPalette palette) {
        float textWidth = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), coordinates, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = Math.max(areaY + MAP_INFO_EDGE_INSET, chromeBottom + MAP_INFO_TOP_GAP);
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), coordinates,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    static void drawDrawer(float areaX, float areaY, float areaWidth,
                           float mouseX, float mouseY,
                           XaeroMapSurface.Drawer drawer,
                           XaeroMapElements.Snapshot snapshot,
                           List<XaeroMapSurface.ElementHit> hits,
                           String waypointsTitle,
                           SettingsGuiPalette palette) {
        hits.clear();
        if (drawer == XaeroMapSurface.Drawer.NONE) return;
        float width = 360.0f;
        float x = areaX + areaWidth - width - 82.0f;
        float y = areaY + 72.0f;
        float rowHeight = 46.0f;
        List<XaeroMapElements.Element> rows = snapshot.elements().stream()
                .filter(element -> drawer == XaeroMapSurface.Drawer.WAYPOINTS
                        ? element.kind() == XaeroMapElements.Kind.WAYPOINT
                        : element.kind() != XaeroMapElements.Kind.WAYPOINT)
                .limit(14)
                .toList();
        float height = 56.0f + Math.max(1, rows.size()) * rowHeight + 12.0f;
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 14.0f, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 14.0f,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 14.0f,
                1.2f, palette.panelStroke());
        String title = drawer == XaeroMapSurface.Drawer.WAYPOINTS ? waypointsTitle : "Players & Radar";
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                x + 18.0f, y + 18.0f, 17.0f, palette.panelText(), false);
        renderer.quad(x + 16.0f, y + 52.0f, width - 32.0f,
                1.0f, palette.panelDivider());
        float rowY = y + 58.0f;
        if (rows.isEmpty()) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Nothing to display",
                    x + 18.0f, rowY + 10.0f, 15.0f, palette.panelMuted(), false);
        }
        for (XaeroMapElements.Element element : rows) {
            boolean hover = inside(mouseX, mouseY, x + 10.0f, rowY, width - 20.0f, 42.0f);
            if (hover) {
                renderer.roundedRect(x + 10.0f, rowY, width - 20.0f, 42.0f,
                        8.0f, palette.controlSurfaceHover());
            }
            String icon = switch (element.kind()) {
                case WAYPOINT -> "map-pin";
                case PLAYER -> "users-round";
                case ENTITY -> "radar";
            };
            renderer.svg(icon, x + 18.0f, rowY + 11.0f,
                    20.0f, 20.0f, SvgRenderOptions.overrideColor(element.color()));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), element.plainName(),
                    x + 48.0f, rowY + 9.0f, 15.0f,
                    element.disabled() ? palette.panelMuted() : palette.panelText(), false);
            String location = (int) element.worldX() + ", " + (int) element.worldZ();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), location,
                    x + 48.0f, rowY + 27.0f, 12.0f, palette.panelMuted(), false);
            hits.add(new XaeroMapSurface.ElementHit(
                    element, x + 10.0f, rowY, width - 20.0f, 42.0f));
            rowY += rowHeight;
        }
    }

    static ContextBounds drawContext(float areaX, float areaY, float areaWidth, float areaHeight,
                                     float contextX, float contextY,
                                     float mouseX, float mouseY,
                                     List<XaeroMapSurface.MenuEntry> entries,
                                     SettingsGuiPalette palette) {
        float width = 340.0f;
        float rowHeight = 42.0f;
        float height = 16.0f + entries.size() * rowHeight;
        float x = clamp(contextX, areaX + 8.0f, areaX + areaWidth - width - 8.0f);
        float y = clamp(contextY, areaY + 8.0f, areaY + areaHeight - height - 8.0f);
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 13.0f, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 13.0f,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 13.0f,
                1.2f, palette.panelStroke());
        float rowY = y + 8.0f;
        for (XaeroMapSurface.MenuEntry entry : entries) {
            boolean hover = entry.enabled() && inside(mouseX, mouseY,
                    x + 8.0f, rowY, width - 16.0f, rowHeight - 3.0f);
            if (hover) {
                renderer.roundedRect(x + 8.0f, rowY,
                        width - 16.0f, rowHeight - 3.0f,
                        8.0f, palette.controlSurfaceHover());
            }
            if (entry.icon() != null) {
                renderer.svg(entry.icon(), x + 16.0f, rowY + 11.0f, 20.0f, 20.0f,
                        SvgRenderOptions.overrideColor(
                                entry.enabled() ? palette.panelText() : palette.panelMuted()));
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), entry.label(),
                    x + 46.0f, rowY + 11.0f, 15.0f,
                    entry.enabled() ? palette.panelText() : palette.panelMuted(), false);
            rowY += rowHeight;
        }
        return new ContextBounds(x, y, width, height);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    record ChromeState(
            boolean settingsOpen,
            boolean caveActive,
            boolean customDimension,
            boolean showWaypoints,
            boolean showRadar,
            boolean radarActive,
            boolean showClaims,
            boolean claimsActive,
            boolean showZoomButtons,
            XaeroMapSurface.Drawer drawer,
            String settingsTooltip,
            String recenterTooltip,
            String caveTooltip,
            String dimensionTooltip,
            String waypointsTooltip,
            String playersTooltip,
            String radarTooltip,
            String claimsTooltip,
            String exportTooltip,
            String controlsTooltip,
            String zoomOutTooltip,
            String zoomInTooltip
    ) {
    }

    record ContextBounds(float x, float y, float width, float height) {
    }
}
