/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.sodium.gui;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.config.subsystem.VisualConfig;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.options.control.StatefulControlElement;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.KeyBoundButtonWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.PageListWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.ResetButton;
import net.caffeinemc.mods.sodium.client.gui.widgets.ScrollableTooltip;
import net.caffeinemc.mods.sodium.client.gui.widgets.ScrollbarWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.SearchWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fixed-coordinate presentation layer for Sodium's video settings screen.
 *
 * Sodium still owns option state, search results, apply/undo semantics and all config APIs. The
 * Combatant layer owns only presentation and the pointer-to-widget bridge. The authored layout is
 * expressed directly in UNSCALED_LOGICAL units and therefore never depends on Minecraft GUI scale.
 */
public final class SodiumGraphicsGuiRenderer {
    private static final Map<Object, Motion> MOTION = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, ScrollState> SCROLL = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<VideoSettingsScreen, FrameLayout> LAST_FRAME = Collections.synchronizedMap(new WeakHashMap<>());

    // 1920x1080 authored canvas. These are direct logical coordinates, not scaled Sodium pixels.
    private static final float CANVAS_W = 1920f;
    private static final float CANVAS_H = 1080f;
    private static final float SEARCH_H = 80f;
    private static final float BODY_Y = 100f;
    private static final float NAV_W = 500f;
    private static final float NAV_H = 980f;
    private static final float OPTIONS_X = 500f;
    private static final float OPTIONS_W = 888f;
    private static final float OPTIONS_H = 960f;
    private static final float INFO_X = 1388f;
    private static final float INFO_W = 532f;

    private static final float PAGE_HEADER_GAP = 8f;
    private static final float PAGE_HEADER_H = 108f;
    private static final float PAGE_ENTRY_H = 72f;
    private static final float PAGE_LEFT = 14f;
    private static final float PAGE_RIGHT_GUTTER = 40f;
    private static final float PAGE_SCROLL_W = 8f;

    private static final float OPTION_MOD_GAP = 48f;
    private static final float OPTION_PAGE_GAP = 24f;
    private static final float OPTION_GROUP_GAP = 12f;
    private static final float OPTION_ROW_H = 72f;
    private static final float OPTION_MOD_HEADER_H = 92f;
    private static final float OPTION_PAGE_HEADER_H = 80f;
    private static final float OPTION_GROUP_HEADER_H = 64f;
    private static final float OPTION_LEFT = 12f;
    private static final float OPTION_RIGHT_GUTTER = 36f;
    private static final float OPTION_SCROLL_W = 8f;

    private static final float ACTION_W = 260f;
    private static final float ACTION_H = 80f;
    private static final float CLOSE_X = 1640f;
    private static final float CLOSE_Y = 980f;
    private static final float APPLY_X = 1640f;
    private static final float APPLY_Y = 880f;
    private static final float UNDO_X = 1640f;
    private static final float UNDO_Y = 780f;

    private static EnumPopup enumPopup;

    private SodiumGraphicsGuiRenderer() {
    }

    public static boolean shouldUseModernUi() {
        return !RuntimeGate.isPanic()
                && RuntimeGate.canRunRender()
                && VisualConfig.get().isModernSodiumGuiEnabled();
    }

    public static boolean render(VideoSettingsScreen screen,
                                 GuiGraphicsExtractor ctx,
                                 int mouseX,
                                 int mouseY,
                                 float delta,
                                 PageListWidget pageList,
                                 SearchWidget searchWidget,
                                 OptionListWidget optionList,
                                 KeyBoundButtonWidget applyButton,
                                 KeyBoundButtonWidget closeButton,
                                 KeyBoundButtonWidget undoButton,
                                 List<KeyBoundButtonWidget> shortcutButtons,
                                 ScrollableTooltip tooltip,
                                 ScreenPrompt prompt,
                                 boolean hasPendingChanges) {
        if (!shouldUseModernUi() || screen == null || ctx == null || pageList == null
                || searchWidget == null || optionList == null) {
            return false;
        }

        FixedCanvas canvas = FixedCanvas.capture();
        if (!canvas.valid()) return false;

        boolean projectionActive = false;
        boolean batchActive = false;
        try {
            CombatantRenderSystem.ensureFrameContext();
            ViewportContext.beginCurrentStratumUnscaledLogical(ctx);
            projectionActive = true;
            Renderer2D.COLOR.begin();
            batchActive = true;

            SettingsGuiPalette palette = SettingsGuiPalette.current();
            VisualStyle style = VisualStyle.from(palette);
            FrameLayout frame = buildFrame(canvas, pageList, optionList, applyButton, closeButton, undoButton, prompt);
            LAST_FRAME.put(screen, frame);

            drawBackdrop(canvas, style);
            drawWindow(frame, style);
            drawSearch(frame, searchWidget, style);
            drawPages(frame, style);
            drawOptions(frame, screen, style);
            drawActionButtons(frame, applyButton, closeButton, undoButton, hasPendingChanges, style);
            drawInfoPanel(frame, style);

            if (prompt != null) {
                drawPrompt(frame, prompt, style);
            } else {
                drawEnumPopup(frame, style);
            }

            Renderer2D.COLOR.render();
            batchActive = false;
            ViewportContext.endCurrentStratum(ctx);
            projectionActive = false;
            return true;
        } catch (Throwable t) {
            DebugLog.errorOnce("sodium-modern-gui-render-failed",
                    "[SodiumGui] Modern renderer failed; falling back to Sodium's native renderer", t);
            try {
                if (batchActive) Renderer2D.COLOR.render();
            } catch (Throwable ignored) {
            }
            try {
                if (projectionActive) ViewportContext.endCurrentStratum(ctx);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    public static boolean mouseClicked(VideoSettingsScreen screen,
                                       MouseButtonEvent event,
                                       boolean doubleClick,
                                       PageListWidget pageList,
                                       SearchWidget searchWidget,
                                       OptionListWidget optionList,
                                       KeyBoundButtonWidget applyButton,
                                       KeyBoundButtonWidget closeButton,
                                       KeyBoundButtonWidget undoButton,
                                       ScreenPrompt prompt) {
        if (!shouldUseModernUi() || screen == null || event == null) return false;
        FrameLayout frame = LAST_FRAME.get(screen);
        if (frame == null) return false;

        float mx = frame.canvas.mouseX;
        float my = frame.canvas.mouseY;

        if (prompt != null) {
            return clickPrompt(frame, prompt, event, doubleClick, mx, my);
        }

        if (enumPopup != null && enumPopup.screen == screen) {
            if (clickEnumPopup(enumPopup, mx, my, event.button())) return true;
            if (!enumPopup.bounds.contains(mx, my)) enumPopup = null;
        }

        Screen vanillaScreen = screen;
        if (frame.search.contains(mx, my)) {
            if (event.button() != 0) return true;
            String query = ((SodiumSearchWidgetAccess) searchWidget).combatant$getQuery();
            Rect clear = frame.searchClear;
            if (query != null && !query.isBlank() && clear.contains(mx, my)) {
                Dim2i nativeRect = searchWidget.getDimensions();
                MouseButtonEvent nativeEvent = synthetic(event,
                        nativeRect.x() + nativeRect.width() - 10.0,
                        nativeRect.y() + nativeRect.height() * 0.5);
                searchWidget.mouseClicked(nativeEvent, doubleClick);
            }
            vanillaScreen.setFocused(searchWidget);
            searchWidget.setFocused(true);
            return true;
        }

        if (searchWidget.isFocused()) {
            vanillaScreen.setFocused(null);
            searchWidget.setFocused(false);
        }

        if (frame.pageScrollbar.contains(mx, my)) {
            jumpScroll(frame.pageScroll, my, frame.pageScrollbar, frame.pageContentHeight, NAV_H);
            syncNativeScrollbar(pageList, frame.pageScroll, frame.pageContentHeight, NAV_H);
            return true;
        }

        for (PageSlot slot : frame.pages) {
            if (!slot.rect.contains(mx, my)) continue;
            AbstractWidget widget = slot.widget;
            MouseButtonEvent nativeEvent = synthetic(event,
                    widget.getX() + Math.max(1.0, widget.getWidth() * 0.5),
                    widget.getY() + Math.max(1.0, widget.getHeight() * 0.5));
            widget.mouseClicked(nativeEvent, doubleClick);
            return true;
        }

        if (frame.optionScrollbar.contains(mx, my)) {
            jumpScroll(frame.optionScroll, my, frame.optionScrollbar, frame.optionContentHeight, OPTIONS_H);
            syncNativeScrollbar(optionList, frame.optionScroll, frame.optionContentHeight, OPTIONS_H);
            return true;
        }

        for (OptionSlot slot : frame.options) {
            if (!slot.rect.contains(mx, my)) continue;
            if (slot.header != null) {
                ResetButton reset = slot.header.combatant$getResetButton();
                if (reset != null && reset.isActive() && slot.resetRect != null && slot.resetRect.contains(mx, my)) {
                    clickWidget(reset, event, doubleClick);
                    return true;
                }
                return true;
            }
            if (slot.control != null) {
                return clickControl(screen, slot, event, doubleClick, mx, my);
            }
        }

        if (frame.closeButton != null && frame.closeButton.rect.contains(mx, my)) {
            clickWidget(frame.closeButton.button, event, doubleClick);
            return true;
        }
        if (frame.undoButton != null && frame.undoButton.rect.contains(mx, my)) {
            clickWidget(frame.undoButton.button, event, doubleClick);
            return true;
        }
        if (frame.applyButton != null && frame.applyButton.rect.contains(mx, my)) {
            clickWidget(frame.applyButton.button, event, doubleClick);
            return true;
        }

        // The modern surface uses fixed unscaled-logical hitboxes. Do not let the same pointer
        // event fall through into Sodium's GUI-scale-dependent widget geometry.
        return frame.root.contains(mx, my);
    }

    public static boolean mouseScrolled(VideoSettingsScreen screen,
                                        double mouseX,
                                        double mouseY,
                                        double horizontal,
                                        double vertical,
                                        PageListWidget pageList,
                                        OptionListWidget optionList) {
        if (!shouldUseModernUi() || screen == null) return false;
        FrameLayout frame = LAST_FRAME.get(screen);
        if (frame == null) return false;
        float mx = frame.canvas.mouseX;
        float my = frame.canvas.mouseY;
        if (enumPopup != null && enumPopup.screen == screen && enumPopup.bounds.contains(mx, my)) {
            enumPopup.scrollBy(vertical);
            return true;
        }
        if (frame.navViewport.contains(mx, my)) {
            scrollBy(frame.pageScroll, vertical, frame.pageContentHeight, NAV_H);
            pageList.mouseScrolled(pageList.getX() + 2.0, pageList.getY() + 2.0, horizontal, vertical);
            return true;
        }
        if (frame.optionViewport.contains(mx, my)) {
            scrollBy(frame.optionScroll, vertical, frame.optionContentHeight, OPTIONS_H);
            optionList.mouseScrolled(optionList.getX() + 2.0, optionList.getY() + 2.0, horizontal, vertical);
            return true;
        }
        return false;
    }

    private static void drawBackdrop(FixedCanvas c, VisualStyle s) {
        Renderer2D.COLOR.quad(0f, 0f, c.logicalWidth, c.logicalHeight, 0x76000000);
    }

    private static void drawWindow(FrameLayout f, VisualStyle s) {
        Rect root = f.root;
        Renderer2D.COLOR.roundedRectSoftShadow(root.x, root.y + 4f, root.w, root.h,
                8f, 32f, 0.026f, s.shadow);
        Renderer2D.COLOR.quad(root.x, root.y, root.w, root.h,
                withAlpha(s.contentTop, 238), withAlpha(s.contentTop, 232),
                withAlpha(s.contentBottom, 245), withAlpha(s.contentBottom, 240));

        Renderer2D.COLOR.quad(f.navViewport.x, f.navViewport.y, f.navViewport.w, f.navViewport.h,
                withAlpha(s.navTop, 212), withAlpha(s.navTop, 205),
                withAlpha(s.navBottom, 224), withAlpha(s.navBottom, 218));
        Renderer2D.COLOR.quad(f.optionViewport.x, f.optionViewport.y, f.optionViewport.w, f.optionViewport.h,
                withAlpha(s.contentTop, 174), withAlpha(s.contentTop, 166),
                withAlpha(s.contentBottom, 196), withAlpha(s.contentBottom, 188));
        Renderer2D.COLOR.quad(f.infoViewport.x, f.infoViewport.y, f.infoViewport.w, f.infoViewport.h,
                withAlpha(mix(s.contentTop, s.accentSoft, 0.025f), 142),
                withAlpha(mix(s.contentTop, s.accentSoft, 0.035f), 150),
                withAlpha(s.contentBottom, 180), withAlpha(s.contentBottom, 172));

        // Strong structural dividers. These intentionally read above row/subcategory hierarchy.
        drawVerticalDivider(f.root.x + NAV_W, f.root.y + BODY_Y, 2f, NAV_H, s);
        drawVerticalDivider(f.root.x + INFO_X, f.root.y + BODY_Y, 2f, NAV_H, s);
        Renderer2D.COLOR.quad(root.x, root.y + SEARCH_H, root.w, 2f,
                withAlpha(s.strokeBright, 94), withAlpha(s.stroke, 78),
                withAlpha(s.stroke, 64), withAlpha(s.strokeBright, 82));
        Renderer2D.COLOR.quad(root.x, root.y + root.h - 1f, root.w, 1f, withAlpha(s.strokeBright, 80));
        Renderer2D.COLOR.quad(root.x, root.y, 1f, root.h, withAlpha(s.strokeBright, 72));
        Renderer2D.COLOR.quad(root.x + root.w - 1f, root.y, 1f, root.h, withAlpha(s.stroke, 66));
    }

    private static void drawVerticalDivider(float x, float y, float w, float h, VisualStyle s) {
        Renderer2D.COLOR.quad(x - 8f, y, 8f, h,
                0x00000000, withAlpha(s.accentSoft, 12), withAlpha(s.accentSoft, 7), 0x00000000);
        Renderer2D.COLOR.quad(x, y, w, h,
                withAlpha(s.strokeBright, 104), withAlpha(s.strokeBright, 92),
                withAlpha(s.stroke, 70), withAlpha(s.stroke, 76));
        Renderer2D.COLOR.quad(x + w, y, 8f, h,
                withAlpha(s.accentSoft, 10), 0x00000000, 0x00000000, withAlpha(s.accentSoft, 6));
    }

    private static void drawSearch(FrameLayout f, SearchWidget search, VisualStyle s) {
        Rect r = f.search;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        boolean focused = search.isFocused();
        String query = ((SodiumSearchWidgetAccess) search).combatant$getQuery();
        boolean hasQuery = query != null && !query.isBlank();
        Motion mo = motion(search, hover, focused);
        float h = AnimationUtility.easeOutCubic(mo.hover);
        float a = AnimationUtility.easeOutCubic(mo.active);

        int left = mix(s.control, s.controlHover, h * 0.52f);
        int right = mix(s.control, s.accentSoft, 0.04f + a * 0.09f + h * 0.05f);
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(left, 238), withAlpha(right, 238),
                withAlpha(right, 224), withAlpha(left, 224));
        if (focused) {
            Renderer2D.COLOR.quad(r.x, r.y + r.h - 3f, r.w, 3f,
                    withAlpha(s.accent, Math.round(150f + 85f * a)),
                    withAlpha(s.accentBright, Math.round(175f + 70f * a)),
                    withAlpha(s.accentBright, Math.round(175f + 70f * a)),
                    withAlpha(s.accent, Math.round(150f + 85f * a)));
            Renderer2D.COLOR.quad(r.x, r.y + r.h - 13f, r.w, 10f,
                    0x00000000, withAlpha(s.accentSoft, Math.round(20f * a)),
                    withAlpha(s.accentSoft, Math.round(13f * a)), 0x00000000);
        }

        float iconSize = 22f;
        float iconX = r.x + 24f;
        float iconY = r.y + 29f;
        Renderer2D.COLOR.svg("search", iconX, iconY, iconSize, iconSize,
                SvgRenderOptions.overrideColor(withAlpha(focused ? s.accentBright : s.textMuted,
                        focused ? 230 : Math.round(170f + h * 35f))));

        float textX = r.x + 62f;
        float textY = r.y + 27f;
        float textSize = 21f;
        String hint = Component.translatable("sodium.options.search.hint").getString();
        String visible = hasQuery ? query : hint;
        TextRenderer font = hasQuery ? fontMedium() : fontRegular();
        float maxW = r.w - 122f;
        String fit = ClickGuiRenderer.fitText(font, visible, textSize, maxW);
        drawText(font, fit, textX, textY, textSize,
                hasQuery ? withAlpha(s.textPrimary, 246) : withAlpha(s.textMuted, focused ? 126 : 174));

        if (focused && blinkCaret()) {
            float caretX = hasQuery
                    ? Math.min(textX + ClickGuiRenderer.textWidth(fontMedium(), fit, textSize) + 3f, r.x + r.w - 76f)
                    : textX;
            Renderer2D.COLOR.quad(caretX, r.y + 22f, 2f, 36f, withAlpha(s.accentBright, 246));
        }

        if (hasQuery) {
            Renderer2D.COLOR.svg("x", f.searchClear.x + 8f, f.searchClear.y + 8f, 18f, 18f,
                    SvgRenderOptions.overrideColor(withAlpha(s.textMuted, Math.round(165f + h * 55f))));
        }
    }

    private static void drawPages(FrameLayout f, VisualStyle s) {
        boolean pushed = ScissorFunction.pushRaw(f.navViewport.x, f.navViewport.y, f.navViewport.w, f.navViewport.h);
        try {
            for (PageSlot slot : f.pages) {
                if (!slot.rect.intersects(f.navViewport)) continue;
                drawPageEntry(f, slot, s);
            }
            drawScrollbar(f.pageScrollbar, f.pageScroll, f.pageContentHeight, NAV_H, f.canvas, s);
        } finally {
            if (pushed) ScissorFunction.pop();
        }
    }

    private static void drawPageEntry(FrameLayout f, PageSlot slot, VisualStyle s) {
        AbstractWidget widget = slot.widget;
        if (!(widget instanceof SodiumCenteredWidgetAccess access) || !access.combatant$isVisible()) return;
        Rect r = slot.rect;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        boolean selected = access.combatant$isSelected();
        boolean header = slot.modHeader;
        Motion mo = motion(widget, hover, selected);
        float h = AnimationUtility.easeOutCubic(mo.hover);
        float a = AnimationUtility.easeOutCubic(mo.active);
        float alpha = access.combatant$isEnabled() ? 1f : 0.42f;

        if (header) {
            int left = mix(s.headerMod, s.accentSoft, 0.045f + h * 0.025f);
            int right = mix(s.headerModRight, s.accent, 0.030f + h * 0.020f);
            Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                    withAlpha(left, 226), withAlpha(right, 210),
                    withAlpha(right, 188), withAlpha(left, 202));
            Renderer2D.COLOR.quad(r.x, r.y + r.h - 2f, r.w, 2f,
                    withAlpha(s.strokeBright, 96), withAlpha(s.accentSoft, 82),
                    withAlpha(s.accentSoft, 70), withAlpha(s.stroke, 78));
            drawModIcon(widget, r.x + 18f, r.y + 30f, 48f, s, 1f);
            Component label = access.combatant$getLabel();
            Component subtitle = access.combatant$getSubtitle();
            drawStyledComponent(label, fontBold(), r.x + 82f, r.y + 27f, 23f,
                    withAlpha(s.textPrimary, Math.round(245f * alpha)), r.w - 108f);
            if (subtitle != null && !subtitle.getString().isBlank()) {
                drawStyledComponent(subtitle, fontRegular(), r.x + 82f, r.y + 60f, 15f,
                        withAlpha(s.textMuted, Math.round(202f * alpha)), r.w - 108f);
            }
            return;
        }

        if (h > 0.002f || a > 0.002f) {
            int left = mix(s.categoryHoverLeft, s.categorySelectedLeft, a);
            int right = mix(s.categoryHoverRight, s.categorySelectedRight, a);
            Renderer2D.COLOR.quad(r.x, r.y + 2f, r.w, r.h - 4f,
                    scaleAlpha(left, 0.28f + h * 0.36f + a * 0.38f),
                    scaleAlpha(right, 0.30f + h * 0.38f + a * 0.40f),
                    scaleAlpha(right, 0.24f + h * 0.31f + a * 0.34f),
                    scaleAlpha(left, 0.22f + h * 0.29f + a * 0.32f));
        }
        if (a > 0.004f) {
            Renderer2D.COLOR.quad(r.x, r.y + 12f, 4f, r.h - 24f,
                    withAlpha(s.accentBright, Math.round(220f * a)));
            Renderer2D.COLOR.quad(r.x + 4f, r.y + 12f, 13f, r.h - 24f,
                    withAlpha(s.accentSoft, Math.round(26f * a)), 0x00000000,
                    0x00000000, withAlpha(s.accentSoft, Math.round(16f * a)));
        }

        Component label = access.combatant$getLabel();
        Component subtitle = access.combatant$getSubtitle();
        if (subtitle == null || subtitle.getString().isBlank()) {
            drawStyledComponent(label, fontMedium(), r.x + 24f, r.y + 24f, 19f,
                    scaleAlpha(s.textPrimary, alpha), r.w - 48f);
        } else {
            drawStyledComponent(label, fontMedium(), r.x + 24f, r.y + 15f, 18f,
                    scaleAlpha(s.textPrimary, alpha), r.w - 48f);
            drawStyledComponent(subtitle, fontRegular(), r.x + 24f, r.y + 42f, 14f,
                    scaleAlpha(s.textMuted, alpha * 0.90f), r.w - 48f);
        }
    }

    private static void drawOptions(FrameLayout f, VideoSettingsScreen screen, VisualStyle s) {
        boolean pushed = ScissorFunction.pushRaw(f.optionViewport.x, f.optionViewport.y,
                f.optionViewport.w, f.optionViewport.h);
        try {
            for (OptionSlot slot : f.options) {
                if (!slot.rect.intersects(f.optionViewport)) continue;
                if (slot.control != null) drawControl(f, screen, slot, s);
                else if (slot.header != null) drawHeader(f, slot, s);
            }
            drawScrollbar(f.optionScrollbar, f.optionScroll, f.optionContentHeight, OPTIONS_H, f.canvas, s);
        } finally {
            if (pushed) ScissorFunction.pop();
        }
    }

    private static void drawHeader(FrameLayout f, OptionSlot slot, VisualStyle s) {
        Rect r = slot.rect;
        AbstractWidget widget = slot.widget;
        SodiumHeaderWidgetAccess header = slot.header;
        String title = header.combatant$getTitle();
        if (title == null) title = "";

        if (slot.modHeader) {
            Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                    withAlpha(s.headerMod, 218), withAlpha(s.headerModRight, 202),
                    withAlpha(s.headerModRight, 178), withAlpha(s.headerMod, 192));
            Renderer2D.COLOR.quad(r.x, r.y + r.h - 3f, r.w, 3f,
                    withAlpha(s.accentSoft, 74), withAlpha(s.strokeBright, 98),
                    withAlpha(s.stroke, 72), withAlpha(s.accentSoft, 64));
            drawModIcon(widget, r.x + 18f, r.y + 15f, 42f, s, 1f);
            drawStyledComponent(Component.literal(title), fontBold(), r.x + 76f, r.y + 22f, 22f,
                    withAlpha(s.textPrimary, 248), r.w - 132f);
        } else if (slot.pageHeader) {
            Renderer2D.COLOR.quad(r.x, r.y, 5f, r.h,
                    withAlpha(s.accent, 210), withAlpha(s.accentBright, 220),
                    withAlpha(s.accent, 178), withAlpha(s.accent, 188));
            Renderer2D.COLOR.quad(r.x + 5f, r.y, r.w - 5f, r.h,
                    withAlpha(mix(s.rowBase, s.accentSoft, 0.035f), 112),
                    withAlpha(mix(s.rowBaseRight, s.accentSoft, 0.045f), 118),
                    withAlpha(s.rowBaseRight, 82), withAlpha(s.rowBase, 78));
            drawStyledComponent(Component.literal(title), fontBold(), r.x + 22f, r.y + 23f, 20f,
                    withAlpha(s.textPrimary, 242), r.w - 74f);
        } else {
            Renderer2D.COLOR.quad(r.x + 14f, r.y + 12f, 3f, r.h - 24f,
                    withAlpha(s.strokeBright, 112));
            drawStyledComponent(Component.literal(title), fontMedium(), r.x + 30f, r.y + 24f, 17f,
                    withAlpha(s.textMuted, 236), r.w - 82f);
            Renderer2D.COLOR.quad(r.x + 30f, r.y + r.h - 7f, r.w - 46f, 1f,
                    withAlpha(s.stroke, 58));
        }

        ResetButton reset = header.combatant$getResetButton();
        if (reset != null && reset.isActive() && slot.resetRect != null) {
            Rect rr = slot.resetRect;
            boolean hover = rr.contains(f.canvas.mouseX, f.canvas.mouseY);
            Motion mo = motion(reset, hover, false);
            float h = AnimationUtility.easeOutCubic(mo.hover);
            if (h > 0.002f) {
                Renderer2D.COLOR.quad(rr.x, rr.y, rr.w, rr.h,
                        scaleAlpha(s.controlHover, 0.25f + h * 0.35f));
            }
            Renderer2D.COLOR.svg("rotate-ccw", rr.x + 8f, rr.y + 8f, 18f, 18f,
                    SvgRenderOptions.overrideColor(withAlpha(s.textMuted, Math.round(160f + h * 70f))));
        }
    }

    private static void drawControl(FrameLayout f, VideoSettingsScreen screen, OptionSlot slot, VisualStyle s) {
        ControlElement control = slot.control;
        Option option = control.getOption();
        if (option == null) return;
        Rect r = slot.rect;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        boolean changed = option.hasChanged();
        boolean enabled = option.isEnabled();
        Motion mo = motion(control, hover, changed);
        float h = AnimationUtility.easeOutCubic(mo.hover);
        float c = AnimationUtility.easeOutCubic(mo.active);

        int left = mix(s.rowBase, s.rowHover, h * 0.70f);
        int right = mix(s.rowBaseRight, s.rowHoverRight, h * 0.74f);
        if (changed) {
            left = mix(left, s.accentSoft, 0.09f + c * 0.08f);
            right = mix(right, s.accentSoft, 0.12f + c * 0.10f);
        }
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                scaleAlpha(left, 0.90f), scaleAlpha(right, 0.91f),
                scaleAlpha(right, 0.76f), scaleAlpha(left, 0.74f));
        if (hover || changed) {
            Renderer2D.COLOR.quad(r.x, r.y + 10f, 3f, r.h - 20f,
                    withAlpha(s.accent, Math.round((hover ? 86f : 0f) + c * 116f)));
        }

        Rect controlRect = slot.controlRect;
        float labelRight = controlRect.x - 18f;
        if (option instanceof IntegerOption integer) {
            String value = integer.formatValue(integer.getValidatedValue()).getString();
            labelRight -= Math.min(170f, ClickGuiRenderer.textWidth(fontMedium(), value, 15f) + 26f);
        }
        Component name = option.getName() != null ? option.getName() : Component.empty();
        drawStyledComponent(name, fontMedium(), r.x + 24f, r.y + 25f, 18f,
                enabled ? withAlpha(s.textPrimary, 242) : withAlpha(s.textMuted, 132),
                Math.max(24f, labelRight - (r.x + 24f)));

        if (control instanceof StatefulControlElement stateful && stateful.isResetOverlayActive()) {
            ResetButton reset = ((SodiumStatefulControlAccess) stateful).combatant$getResetButton();
            if (reset != null && reset.isActive() && slot.resetRect != null) {
                Rect rr = slot.resetRect;
                Renderer2D.COLOR.quad(rr.x, rr.y, rr.w, rr.h, withAlpha(s.control, 182));
                Renderer2D.COLOR.svg("rotate-ccw", rr.x + 9f, rr.y + 9f, 18f, 18f,
                        SvgRenderOptions.overrideColor(withAlpha(s.textMuted, 208)));
            }
            return;
        }

        if (option instanceof BooleanOption bool) {
            drawBooleanControl(bool, controlRect, enabled, hover, s);
        } else if (option instanceof IntegerOption integer) {
            drawIntegerControl(integer, controlRect, enabled, hover, s);
        } else if (option instanceof EnumOption<?> enumeration) {
            drawEnumControl(screen, enumeration, controlRect, enabled, hover, s);
        } else if (option instanceof ExternalButtonOption) {
            drawExternalControl(controlRect, enabled, hover, s);
        } else if (option instanceof StatefulOption<?> stateful) {
            drawGenericStatefulControl(stateful, controlRect, enabled, hover, s);
        }
    }

    private static void drawBooleanControl(BooleanOption option, Rect r, boolean enabled, boolean hover, VisualStyle s) {
        boolean on = Boolean.TRUE.equals(option.getValidatedValue());
        Motion mo = motion(option, hover, on);
        float a = AnimationUtility.easeOutCubic(mo.active);
        Rect box = new Rect(r.x + r.w - 112f, r.y + 15f, 104f, 42f);
        int off = enabled ? s.control : withAlpha(s.control, 98);
        int active = enabled ? mix(s.accentSoft, s.accent, 0.20f) : off;
        Renderer2D.COLOR.quad(box.x, box.y, box.w, box.h,
                mix(off, active, a), mix(off, active, a * 0.92f),
                mix(withAlpha(off, 176), withAlpha(active, 204), a), withAlpha(off, 166));
        Renderer2D.COLOR.quad(box.x, box.y, box.w, 1f,
                withAlpha(on ? s.accentBright : s.strokeBright, on ? 128 : 58));
        String label = on ? "ON" : "OFF";
        float tw = ClickGuiRenderer.textWidth(fontBold(), label, 15f);
        drawText(fontBold(), label, box.x + (box.w - tw) * 0.5f, box.y + 12f, 15f,
                enabled ? (on ? withAlpha(s.textPrimary, 250) : withAlpha(s.textMuted, 214)) : withAlpha(s.textMuted, 112));
    }

    private static void drawIntegerControl(IntegerOption option, Rect r, boolean enabled, boolean hover, VisualStyle s) {
        int value = option.getValidatedValue();
        SteppedValidator validator = option.getSteppedValidator();
        int min = validator != null ? validator.min() : value;
        int max = validator != null ? validator.max() : value;
        float ratio = max > min ? (value - min) / (float) (max - min) : 0f;
        ratio = clamp01(ratio);

        String valueText = option.formatValue(value).getString();
        float valueW = Math.min(170f, ClickGuiRenderer.textWidth(fontMedium(), valueText, 15f));
        float trackX = r.x + 4f;
        float trackW = r.w - 8f;
        float trackY = r.y + 35f;
        Renderer2D.COLOR.quad(trackX, trackY, trackW, 4f, withAlpha(s.stroke, enabled ? 94 : 42));
        Renderer2D.COLOR.quad(trackX, trackY, Math.max(4f, trackW * ratio), 4f,
                withAlpha(s.accent, enabled ? 206 : 78), withAlpha(s.accentBright, enabled ? 224 : 84),
                withAlpha(s.accentBright, enabled ? 214 : 82), withAlpha(s.accent, enabled ? 192 : 72));
        float cx = trackX + trackW * ratio;
        float knob = hover ? 16f : 14f;
        Renderer2D.COLOR.roundedRectSoftShadow(cx - knob * 0.5f, trackY - 6f, knob, knob,
                knob * 0.5f, 10f, 0.018f, withAlpha(s.accent, enabled ? 120 : 48));
        Renderer2D.COLOR.roundedRectGradientQuad(cx - knob * 0.5f, trackY - 6f, knob, knob,
                knob * 0.5f, 0.65f,
                enabled ? s.accentBright : withAlpha(s.textMuted, 90),
                enabled ? s.accent : withAlpha(s.textMuted, 76),
                enabled ? s.accent : withAlpha(s.textMuted, 70),
                enabled ? s.accentBright : withAlpha(s.textMuted, 86));
        drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), valueText, 15f, valueW),
                r.x - valueW - 20f, r.y + 26f, 15f,
                enabled ? withAlpha(s.textPrimary, 228) : withAlpha(s.textMuted, 112));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void drawEnumControl(VideoSettingsScreen screen, EnumOption<?> option, Rect r,
                                        boolean enabled, boolean hover, VisualStyle s) {
        Object raw = option.getValidatedValue();
        Component valueComponent = raw instanceof Enum<?> e
                ? ((EnumOption) option).getElementName(e)
                : Component.literal(String.valueOf(raw));
        boolean open = enumPopup != null && enumPopup.screen == screen && enumPopup.option == option;
        int left = open ? mix(s.controlHover, s.accentSoft, 0.13f) : hover && enabled ? s.controlHover : s.control;
        int right = open ? mix(s.controlHoverRight, s.accent, 0.09f) : hover && enabled ? s.controlHoverRight : s.control;
        Renderer2D.COLOR.quad(r.x, r.y + 11f, r.w, 50f,
                withAlpha(left, 232), withAlpha(right, 228), withAlpha(right, 198), withAlpha(left, 202));
        Renderer2D.COLOR.quad(r.x, r.y + 60f, r.w, 1f,
                withAlpha(open ? s.accentBright : s.strokeBright, open ? 158 : 62));
        drawStyledComponent(valueComponent, fontMedium(), r.x + 14f, r.y + 27f, 15f,
                enabled ? withAlpha(s.textPrimary, 230) : withAlpha(s.textMuted, 108), r.w - 52f);
        String chevron = open ? "chevron-up" : "chevron-down";
        Renderer2D.COLOR.svg(chevron, r.x + r.w - 30f, r.y + 27f, 14f, 14f,
                SvgRenderOptions.overrideColor(enabled ? withAlpha(s.textMuted, 202) : withAlpha(s.textMuted, 92)));
    }

    private static void drawExternalControl(Rect r, boolean enabled, boolean hover, VisualStyle s) {
        int left = hover && enabled ? s.controlHover : s.control;
        int right = hover && enabled ? mix(s.controlHoverRight, s.accentSoft, 0.10f) : s.control;
        Renderer2D.COLOR.quad(r.x, r.y + 11f, r.w, 50f,
                withAlpha(left, 228), withAlpha(right, 220), withAlpha(right, 194), withAlpha(left, 198));
        String value = Component.translatable("sodium.options.open_external_page_button").getString();
        drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), value, 15f, r.w - 52f),
                r.x + 14f, r.y + 27f, 15f,
                enabled ? withAlpha(s.textPrimary, 226) : withAlpha(s.textMuted, 108));
        Renderer2D.COLOR.svg("chevron-right", r.x + r.w - 30f, r.y + 27f, 14f, 14f,
                SvgRenderOptions.overrideColor(enabled ? withAlpha(s.accentBright, 210) : withAlpha(s.textMuted, 82)));
    }

    private static void drawGenericStatefulControl(StatefulOption<?> option, Rect r,
                                                   boolean enabled, boolean hover, VisualStyle s) {
        int left = hover && enabled ? s.controlHover : s.control;
        int right = hover && enabled ? s.controlHoverRight : s.control;
        Renderer2D.COLOR.quad(r.x, r.y + 11f, r.w, 50f,
                withAlpha(left, 226), withAlpha(right, 220), withAlpha(right, 190), withAlpha(left, 194));
        String value = String.valueOf(option.getValidatedValue());
        drawText(fontMedium(), ClickGuiRenderer.fitText(fontMedium(), value, 15f, r.w - 28f),
                r.x + 14f, r.y + 27f, 15f,
                enabled ? withAlpha(s.textPrimary, 220) : withAlpha(s.textMuted, 104));
    }

    private static void drawActionButtons(FrameLayout f,
                                          KeyBoundButtonWidget apply,
                                          KeyBoundButtonWidget close,
                                          KeyBoundButtonWidget undo,
                                          boolean pending,
                                          VisualStyle s) {
        if (f.undoButton != null) drawFlatButton(f, f.undoButton, false, s);
        if (f.applyButton != null) drawFlatButton(f, f.applyButton, pending, s);
        if (f.closeButton != null) drawFlatButton(f, f.closeButton, false, s);
    }

    private static void drawFlatButton(FrameLayout f, ButtonSlot slot, boolean primary, VisualStyle s) {
        FlatButtonWidget button = slot.button;
        if (!(button instanceof SodiumFlatButtonAccess access) || !access.combatant$isVisible()) return;
        Rect r = slot.rect;
        boolean hover = r.contains(f.canvas.mouseX, f.canvas.mouseY);
        boolean enabled = access.combatant$isEnabled();
        Motion mo = motion(button, hover && enabled, primary || access.combatant$isSelected());
        float h = AnimationUtility.easeOutCubic(mo.hover);
        float a = AnimationUtility.easeOutCubic(mo.active);
        int left = enabled ? mix(s.control, s.controlHover, h * 0.76f) : withAlpha(s.control, 90);
        int right = enabled ? mix(s.control, s.controlHoverRight, h * 0.80f) : withAlpha(s.control, 82);
        if (primary && enabled) {
            left = mix(left, s.accentSoft, 0.15f + a * 0.08f);
            right = mix(right, s.accent, 0.10f + a * 0.06f);
            Renderer2D.COLOR.roundedRectSoftShadow(r.x, r.y, r.w, r.h, 5f, 18f,
                    0.014f + h * 0.012f, withAlpha(s.accent, Math.round(76f + h * 42f)));
        }
        Renderer2D.COLOR.quad(r.x, r.y, r.w, r.h,
                withAlpha(left, 236), withAlpha(right, 230), withAlpha(right, 202), withAlpha(left, 206));
        Renderer2D.COLOR.quad(r.x, r.y, r.w, 1f,
                withAlpha(primary ? s.accentBright : s.strokeBright, enabled ? 80 : 28));

        Component label = access.combatant$getLabel() != null ? access.combatant$getLabel() : Component.empty();
        String plain = LegacyTextUtil.stripLegacy(label.getString());
        float tw = ClickGuiRenderer.textWidth(fontMedium(), plain, 16f);
        float tx = access.combatant$isLeftAligned() ? r.x + 18f : r.x + (r.w - tw) * 0.5f;
        drawStyledComponent(label, fontMedium(), tx, r.y + 29f, 16f,
                enabled ? withAlpha(s.textPrimary, 238) : withAlpha(s.textMuted, 106), r.w - 36f);
    }

    private static void drawInfoPanel(FrameLayout f, VisualStyle s) {
        ControlElement hovered = null;
        for (OptionSlot slot : f.options) {
            if (slot.control != null && slot.rect.contains(f.canvas.mouseX, f.canvas.mouseY)) {
                hovered = slot.control;
                break;
            }
        }
        if (hovered == null || hovered.getOption() == null) {
            drawText(fontBold(), "SODIUM", f.infoViewport.x + 32f, f.infoViewport.y + 34f, 22f,
                    withAlpha(s.textPrimary, 222));
            drawText(fontRegular(), "Video settings", f.infoViewport.x + 32f, f.infoViewport.y + 70f, 15f,
                    withAlpha(s.textMuted, 174));
            Renderer2D.COLOR.quad(f.infoViewport.x + 32f, f.infoViewport.y + 104f,
                    f.infoViewport.w - 64f, 1f, withAlpha(s.stroke, 70));
            return;
        }

        Option option = hovered.getOption();
        Component name = option.getName() != null ? option.getName() : Component.empty();
        drawStyledComponent(name, fontBold(), f.infoViewport.x + 32f, f.infoViewport.y + 32f, 21f,
                withAlpha(s.textPrimary, 240), f.infoViewport.w - 64f);
        float y = f.infoViewport.y + 76f;
        if (option.getImpact() != null) {
            Component impact = Component.translatable("sodium.options.performance_impact_string",
                    option.getImpact().getName());
            drawStyledComponent(impact, fontMedium(), f.infoViewport.x + 32f, y, 15f,
                    withAlpha(s.accentBright, 220), f.infoViewport.w - 64f);
            y += 34f;
        }
        Renderer2D.COLOR.quad(f.infoViewport.x + 32f, y, f.infoViewport.w - 64f, 1f,
                withAlpha(s.stroke, 68));
        y += 24f;

        Component tooltip = option.getTooltip();
        if (tooltip == null) return;
        List<StyledLine> lines = wrapStyledComponent(tooltip, fontRegular(), 15f,
                f.infoViewport.w - 64f, 18, withAlpha(s.textPrimary, 214));
        for (StyledLine line : lines) {
            drawStyledLine(line, f.infoViewport.x + 32f, y, 15f);
            y += 25f;
            if (y > f.infoViewport.y + f.infoViewport.h - 110f) break;
        }
    }

    private static void drawEnumPopup(FrameLayout f, VisualStyle s) {
        EnumPopup popup = enumPopup;
        if (popup == null || popup.screen == null || popup.bounds == null) return;
        Rect r = popup.bounds;
        popup.tickScroll();
        float reveal = AnimationUtility.easeOutCubic(popup.reveal = AnimationUtility.approach(
                popup.reveal, 1f, Math.max(0.001f, AnimationUtility.deltaTime()), 13f));
        Renderer2D.COLOR.roundedRectSoftShadow(r.x, r.y + 4f, r.w, r.h, 7f, 24f,
                0.025f * reveal, withAlpha(s.shadow, Math.round(210f * reveal)));
        Renderer2D.COLOR.liquidGlassRect(r.x, r.y, r.w, r.h, 7f, s.glassTint,
                0.48f, 0.76f, Renderer2D.LiquidGlassPreset.HUD_SMALL, 0.90f, 0f);
        Renderer2D.COLOR.roundedRectGradientQuad(r.x, r.y, r.w, r.h, 7f, 0.65f,
                withAlpha(s.strokeBright, Math.round(90f * reveal)),
                withAlpha(s.strokeBright, Math.round(72f * reveal)),
                withAlpha(s.stroke, Math.round(48f * reveal)),
                withAlpha(s.stroke, Math.round(62f * reveal)));

        boolean clipped = ScissorFunction.pushRaw(r.x + 5f, r.y + 6f, r.w - 10f, r.h - 12f);
        try {
            for (int i = 0; i < popup.values.size(); i++) {
                Enum<?> value = popup.values.get(i);
                Rect base = popup.items.get(i);
                Rect ir = new Rect(base.x, base.y - popup.scrollCurrent, base.w, base.h);
                if (!ir.intersects(r)) continue;
                boolean hover = ir.contains(f.canvas.mouseX, f.canvas.mouseY);
                boolean selected = value == popup.option.getValidatedValue();
                Motion mo = motion(value, hover, selected);
                float h = AnimationUtility.easeOutCubic(mo.hover);
                float a = AnimationUtility.easeOutCubic(mo.active);
                if (h > 0.002f || a > 0.002f) {
                    Renderer2D.COLOR.quad(ir.x + 5f, ir.y + 2f, ir.w - 10f, ir.h - 4f,
                            scaleAlpha(mix(s.controlHover, s.accentSoft, a * 0.16f), 0.34f + h * 0.36f + a * 0.24f));
                }
                Component label = enumElementName(popup.option, value);
                drawStyledComponent(label, selected ? fontBold() : fontMedium(), ir.x + 16f, ir.y + 16f, 15f,
                        selected ? withAlpha(s.accentBright, 244) : withAlpha(s.textPrimary, 226), ir.w - 48f);
                if (selected) {
                    Renderer2D.COLOR.quad(ir.x + ir.w - 22f, ir.y + 18f, 7f, 7f,
                            withAlpha(s.accentBright, 232));
                }
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }

        if (popup.scrollMax > 0f) {
            Rect track = new Rect(r.x + r.w - 6f, r.y + 8f, 2f, r.h - 16f);
            float visibleRatio = Math.min(1f, (r.h - 12f) / Math.max(r.h - 12f, popup.contentHeight));
            float handleH = Math.max(28f, track.h * visibleRatio);
            float handleY = track.y + (track.h - handleH) * clamp01(popup.scrollCurrent / popup.scrollMax);
            Renderer2D.COLOR.quad(track.x, track.y, track.w, track.h, withAlpha(s.scrollTrack, 50));
            Renderer2D.COLOR.quad(track.x, handleY, track.w, handleH, withAlpha(s.scrollHandleBright, 156));
        }
    }

    private static void drawPrompt(FrameLayout f, ScreenPrompt prompt, VisualStyle s) {
        if (!(prompt instanceof SodiumPromptAccess access)) return;
        Renderer2D.COLOR.quad(0f, 0f, f.canvas.logicalWidth, f.canvas.logicalHeight, 0x90000000);
        Rect modal = f.promptBounds;
        if (modal == null) return;
        Renderer2D.COLOR.roundedRectSoftShadow(modal.x, modal.y + 6f, modal.w, modal.h, 10f, 34f, 0.032f, s.shadow);
        Renderer2D.COLOR.liquidGlassRect(modal.x, modal.y, modal.w, modal.h, 10f, s.glassTint,
                0.56f, 0.82f, Renderer2D.LiquidGlassPreset.BALANCED, 0.96f, 0f);
        Renderer2D.COLOR.roundedRectGradientQuad(modal.x, modal.y, modal.w, modal.h, 10f, 0.7f,
                withAlpha(s.strokeBright, 96), withAlpha(s.strokeBright, 80),
                withAlpha(s.stroke, 50), withAlpha(s.stroke, 66));

        float y = modal.y + 34f;
        float maxW = modal.w - 68f;
        for (FormattedText text : access.combatant$getText()) {
            if (text == null) continue;
            if (text instanceof Component component) {
                List<StyledLine> styled = wrapStyledComponent(component, fontRegular(), 17f, maxW, 9,
                        withAlpha(s.textPrimary, 232));
                for (StyledLine line : styled) {
                    drawStyledLine(line, modal.x + 34f, y, 17f);
                    y += 29f;
                }
                y += 8f;
                continue;
            }
            List<String> lines = ClickGuiRenderer.wrapText(fontRegular(), text.getString(), 17f, maxW, 9);
            for (String line : lines) {
                drawText(fontRegular(), line, modal.x + 34f, y, 17f, withAlpha(s.textPrimary, 232));
                y += 29f;
            }
            y += 8f;
        }
        for (ButtonSlot slot : f.promptButtons) drawFlatButton(f, slot, true, s);
    }

    private static FrameLayout buildFrame(FixedCanvas canvas,
                                          PageListWidget pages,
                                          OptionListWidget options,
                                          KeyBoundButtonWidget apply,
                                          KeyBoundButtonWidget close,
                                          KeyBoundButtonWidget undo,
                                          ScreenPrompt prompt) {
        float ox = canvas.originX;
        float oy = canvas.originY;
        Rect root = new Rect(ox, oy, CANVAS_W, CANVAS_H);
        Rect search = new Rect(ox, oy, CANVAS_W, SEARCH_H);
        Rect searchClear = new Rect(ox + CANVAS_W - 58f, oy + 22f, 34f, 34f);
        Rect nav = new Rect(ox, oy + BODY_Y, NAV_W, NAV_H);
        Rect option = new Rect(ox + OPTIONS_X, oy + BODY_Y, OPTIONS_W, OPTIONS_H);
        Rect info = new Rect(ox + INFO_X, oy + BODY_Y, INFO_W, NAV_H);

        ScrollState pageScroll = scroll(pages);
        ScrollState optionScroll = scroll(options);

        PageBuild pageBuild = buildPageSlots(canvas, pages, nav, pageScroll);
        OptionBuild optionBuild = buildOptionSlots(canvas, options, option, optionScroll);

        pageScroll.clamp(pageBuild.contentHeight, NAV_H);
        optionScroll.clamp(optionBuild.contentHeight, OPTIONS_H);
        pageScroll.tick();
        optionScroll.tick();
        // Rebuild with animated offsets after tick.
        pageBuild = buildPageSlots(canvas, pages, nav, pageScroll);
        optionBuild = buildOptionSlots(canvas, options, option, optionScroll);

        Rect pageScrollbar = new Rect(ox + NAV_W - 18f, oy + BODY_Y + 12f, PAGE_SCROLL_W, NAV_H - 24f);
        Rect optionScrollbar = new Rect(ox + OPTIONS_X + OPTIONS_W - 18f, oy + BODY_Y + 12f, OPTION_SCROLL_W, OPTIONS_H - 24f);

        ButtonSlot closeSlot = close != null ? new ButtonSlot(close, new Rect(ox + CLOSE_X, oy + CLOSE_Y, ACTION_W, ACTION_H)) : null;
        ButtonSlot applySlot = apply != null ? new ButtonSlot(apply, new Rect(ox + APPLY_X, oy + APPLY_Y, ACTION_W, ACTION_H)) : null;
        ButtonSlot undoSlot = undo != null ? new ButtonSlot(undo, new Rect(ox + UNDO_X, oy + UNDO_Y, ACTION_W, ACTION_H)) : null;

        Rect promptBounds = null;
        ArrayList<ButtonSlot> promptButtons = new ArrayList<>();
        if (prompt != null) {
            float pw = 760f;
            float ph = 430f;
            promptBounds = new Rect(ox + (CANVAS_W - pw) * 0.5f, oy + (CANVAS_H - ph) * 0.5f, pw, ph);
            List<AbstractWidget> widgets = prompt.getWidgets();
            float bx = promptBounds.x + promptBounds.w - 34f;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                if (!(widget instanceof FlatButtonWidget flat)) continue;
                bx -= 210f;
                promptButtons.add(new ButtonSlot(flat,
                        new Rect(bx, promptBounds.y + promptBounds.h - 78f, 190f, 52f)));
                bx -= 14f;
            }
        }

        return new FrameLayout(canvas, root, search, searchClear, nav, option, info,
                pageBuild.slots, optionBuild.slots,
                pageScroll, optionScroll, pageBuild.contentHeight, optionBuild.contentHeight,
                pageScrollbar, optionScrollbar,
                applySlot, closeSlot, undoSlot,
                promptBounds, promptButtons);
    }

    private static PageBuild buildPageSlots(FixedCanvas canvas, PageListWidget pages, Rect viewport, ScrollState scroll) {
        ArrayList<PageSlot> out = new ArrayList<>();
        float cursor = 0f;
        for (GuiEventListener child : pages.children()) {
            if (child instanceof ScrollbarWidget) continue;
            if (!(child instanceof AbstractWidget widget)) continue;
            boolean modHeader = widget.getClass().getSimpleName().contains("HeaderEntryWidget");
            if (modHeader) cursor += PAGE_HEADER_GAP;
            float h = modHeader ? PAGE_HEADER_H : PAGE_ENTRY_H;
            float x = viewport.x + PAGE_LEFT;
            float w = viewport.w - PAGE_LEFT - PAGE_RIGHT_GUTTER;
            Rect rect = new Rect(x, viewport.y + cursor - scroll.current, w, h);
            out.add(new PageSlot(widget, rect, modHeader));
            cursor += h;
        }
        return new PageBuild(out, cursor + 20f);
    }

    private static OptionBuild buildOptionSlots(FixedCanvas canvas, OptionListWidget list, Rect viewport, ScrollState scroll) {
        ArrayList<OptionSlot> out = new ArrayList<>();
        float cursor = 0f;
        int nativeScroll = list.getScrollAmount();
        int previousNativeEnd = list.getY();
        for (GuiEventListener child : list.children()) {
            if (child instanceof ScrollbarWidget) continue;
            if (!(child instanceof AbstractWidget widget)) continue;

            int nativeY = widget.getY() + nativeScroll;
            int nativeGap = nativeY - previousNativeEnd;
            cursor += fixedOptionGap(nativeGap);

            ControlElement control = child instanceof ControlElement c ? c : null;
            SodiumHeaderWidgetAccess header = child instanceof SodiumHeaderWidgetAccess h ? h : null;
            String simple = widget.getClass().getSimpleName();
            boolean modHeader = simple.contains("ModHeader");
            boolean pageHeader = simple.contains("PageHeader");
            boolean groupHeader = simple.contains("GroupHeader");
            float rowHeight = modHeader ? OPTION_MOD_HEADER_H
                    : pageHeader ? OPTION_PAGE_HEADER_H
                    : groupHeader ? OPTION_GROUP_HEADER_H
                    : OPTION_ROW_H;
            float x = viewport.x + OPTION_LEFT;
            float w = viewport.w - OPTION_LEFT - OPTION_RIGHT_GUTTER;
            Rect rect = new Rect(x, viewport.y + cursor - scroll.current, w, rowHeight);

            Rect controlRect = null;
            Rect resetRect = null;
            if (control != null) {
                if (control.getOption() instanceof IntegerOption) {
                    controlRect = new Rect(rect.x + rect.w - 380f, rect.y, 350f, rect.h);
                } else {
                    controlRect = new Rect(rect.x + rect.w - 310f, rect.y, 280f, rect.h);
                }
                if (control instanceof StatefulControlElement stateful && stateful.isResetOverlayActive()) {
                    resetRect = new Rect(rect.x + rect.w - 42f, rect.y + 18f, 36f, 36f);
                }
            } else if (header != null) {
                ResetButton reset = header.combatant$getResetButton();
                if (reset != null && reset.isActive()) {
                    resetRect = new Rect(rect.x + rect.w - 42f, rect.y + 18f, 34f, 34f);
                }
            }

            out.add(new OptionSlot(widget, rect, controlRect, resetRect, control, header,
                    modHeader, pageHeader, groupHeader));
            cursor += rowHeight;
            previousNativeEnd = nativeY + widget.getHeight();
        }
        return new OptionBuild(out, cursor + 20f);
    }

    private static float fixedOptionGap(int nativeGap) {
        if (nativeGap <= 0) return 0f;
        if (nativeGap <= 3) return OPTION_GROUP_GAP;
        if (nativeGap <= 6) return OPTION_PAGE_GAP;
        return OPTION_MOD_GAP;
    }

    private static boolean clickControl(VideoSettingsScreen screen, OptionSlot slot,
                                        MouseButtonEvent event, boolean doubleClick, float mx, float my) {
        ControlElement control = slot.control;
        Option option = control.getOption();
        if (option == null || !option.isEnabled()) return true;
        if (event.button() != 0) return true;

        if (control instanceof StatefulControlElement stateful && stateful.isResetOverlayActive()) {
            if (slot.resetRect != null && slot.resetRect.contains(mx, my)) {
                ResetButton reset = ((SodiumStatefulControlAccess) stateful).combatant$getResetButton();
                if (reset != null && reset.isActive()) clickWidget(reset, event, doubleClick);
            }
            return true;
        }

        if (option instanceof BooleanOption bool) {
            bool.modifyValue(!Boolean.TRUE.equals(bool.getValidatedValue()));
            return true;
        }
        if (option instanceof IntegerOption integer) {
            Rect r = slot.controlRect;
            setIntegerFromPointer(integer, r, mx);
            return true;
        }
        if (option instanceof EnumOption<?> enumeration) {
            openEnumPopup(screen, enumeration, slot.controlRect);
            return true;
        }
        if (option instanceof ExternalButtonOption external) {
            external.getCurrentScreenConsumer().accept(screen);
            return true;
        }

        clickWidget(control, event, doubleClick);
        return true;
    }

    private static void setIntegerFromPointer(IntegerOption integer, Rect r, float mx) {
        float trackX = r.x + 4f;
        float trackW = r.w - 8f;
        float ratio = clamp01((mx - trackX) / Math.max(1f, trackW));
        SteppedValidator validator = integer.getSteppedValidator();
        if (validator == null) return;
        int min = validator.min();
        int max = validator.max();
        int step = Math.max(1, validator.step());
        int count = Math.max(0, (max - min) / step);
        int index = Math.round(count * ratio);
        int value = Math.max(min, Math.min(max, min + index * step));
        integer.modifyValue(value);
    }

    private static void openEnumPopup(VideoSettingsScreen screen, EnumOption<?> option, Rect anchor) {
        ArrayList<Enum<?>> values = new ArrayList<>();
        Object[] constants = option.getEnumClass().getEnumConstants();
        if (constants != null) {
            for (Object raw : constants) {
                if (!(raw instanceof Enum<?> e)) continue;
                if (isEnumAllowed(option, e)) values.add(e);
            }
        }
        if (values.isEmpty()) return;
        float itemH = 48f;
        float contentHeight = values.size() * itemH;
        float h = Math.min(336f, contentHeight + 12f);
        float x = anchor.x;
        float y = anchor.y + anchor.h - 5f;
        FrameLayout frame = LAST_FRAME.get(screen);
        if (frame != null && y + h > frame.root.y + frame.root.h - 16f) {
            y = Math.max(frame.root.y + 16f, anchor.y - h + 5f);
        }
        Rect bounds = new Rect(x, y, anchor.w, h);
        ArrayList<Rect> items = new ArrayList<>();
        float iy = y + 6f;
        for (int i = 0; i < values.size(); i++) {
            items.add(new Rect(x + 5f, iy, anchor.w - 10f, itemH));
            iy += itemH;
        }
        enumPopup = new EnumPopup(screen, option, values, bounds, items, contentHeight, 0f);
    }

    private static boolean clickEnumPopup(EnumPopup popup, float mx, float my, int button) {
        if (button != 0) return popup.bounds.contains(mx, my);
        for (int i = 0; i < popup.items.size(); i++) {
            Rect base = popup.items.get(i);
            Rect visible = new Rect(base.x, base.y - popup.scrollCurrent, base.w, base.h);
            if (!visible.contains(mx, my)) continue;
            setEnumValue(popup.option, popup.values.get(i));
            enumPopup = null;
            return true;
        }
        return popup.bounds.contains(mx, my);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumValue(EnumOption<?> option, Enum<?> value) {
        ((EnumOption) option).modifyValue(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isEnumAllowed(EnumOption<?> option, Enum<?> value) {
        return ((EnumOption) option).isValueAllowed(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Component enumElementName(EnumOption<?> option, Enum<?> value) {
        return ((EnumOption) option).getElementName(value);
    }

    private static boolean clickPrompt(FrameLayout frame, ScreenPrompt prompt, MouseButtonEvent event,
                                       boolean doubleClick, float mx, float my) {
        for (ButtonSlot slot : frame.promptButtons) {
            if (!slot.rect.contains(mx, my)) continue;
            clickWidget(slot.button, event, doubleClick);
            return true;
        }
        return frame.promptBounds != null && frame.promptBounds.contains(mx, my);
    }

    private static void clickWidget(GuiEventListener widget, MouseButtonEvent source, boolean doubleClick) {
        if (!(widget instanceof AbstractWidget abstractWidget)) return;
        MouseButtonEvent event = synthetic(source,
                abstractWidget.getX() + Math.max(1.0, abstractWidget.getWidth() * 0.5),
                abstractWidget.getY() + Math.max(1.0, abstractWidget.getHeight() * 0.5));
        abstractWidget.mouseClicked(event, doubleClick);
    }

    private static MouseButtonEvent synthetic(MouseButtonEvent source, double x, double y) {
        return new MouseButtonEvent(x, y, source.buttonInfo());
    }

    private static void scrollBy(ScrollState state, double vertical, float contentHeight, float viewportHeight) {
        if (state == null || vertical == 0.0) return;
        state.target -= (float) Math.signum(vertical) * 64f;
        state.clamp(contentHeight, viewportHeight);
    }

    private static void jumpScroll(ScrollState state, float mouseY, Rect track, float contentHeight, float viewportHeight) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        if (max <= 0f) {
            state.target = 0f;
            return;
        }
        float ratio = clamp01((mouseY - track.y) / Math.max(1f, track.h));
        state.target = max * ratio;
        state.clamp(contentHeight, viewportHeight);
    }

    private static void syncNativeScrollbar(Object list, ScrollState state, float contentHeight, float viewportHeight) {
        ScrollbarWidget bar = findScrollbar(list);
        if (bar == null) return;
        SodiumScrollbarAccess access = (SodiumScrollbarAccess) bar;
        int visible = access.combatant$getVisibleAmount();
        int total = access.combatant$getTotalAmount();
        int nativeMax = Math.max(0, total - visible);
        float customMax = Math.max(0f, contentHeight - viewportHeight);
        int target = customMax <= 0f ? 0 : Math.round(nativeMax * clamp01(state.target / customMax));
        bar.scrollTo(target);
    }

    private static ScrollbarWidget findScrollbar(Object list) {
        if (list instanceof PageListWidget pages) {
            for (GuiEventListener child : pages.children()) if (child instanceof ScrollbarWidget bar) return bar;
        } else if (list instanceof OptionListWidget options) {
            for (GuiEventListener child : options.children()) if (child instanceof ScrollbarWidget bar) return bar;
        }
        return null;
    }

    private static void drawScrollbar(Rect track, ScrollState state, float contentHeight, float viewportHeight,
                                      FixedCanvas canvas, VisualStyle s) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        if (max <= 0f) return;
        boolean hover = track.contains(canvas.mouseX, canvas.mouseY);
        Motion mo = motion(state, hover, false);
        float h = AnimationUtility.easeOutCubic(mo.hover);
        Renderer2D.COLOR.quad(track.x, track.y, track.w, track.h,
                withAlpha(s.scrollTrack, Math.round(56f + h * 24f)));
        float ratio = Math.min(1f, viewportHeight / Math.max(viewportHeight, contentHeight));
        float handleH = Math.max(42f, track.h * ratio);
        float pos = (track.h - handleH) * clamp01(state.current / max);
        Renderer2D.COLOR.quad(track.x, track.y + pos, track.w, handleH,
                withAlpha(s.scrollHandle, Math.round(132f + h * 68f)),
                withAlpha(s.scrollHandleBright, Math.round(148f + h * 72f)),
                withAlpha(s.scrollHandleBright, Math.round(130f + h * 64f)),
                withAlpha(s.scrollHandle, Math.round(120f + h * 60f)));
    }

    private static void drawModIcon(Object source, float x, float y, float size, VisualStyle s, float alpha) {
        if (!(source instanceof SodiumModIconAccess iconAccess)) return;
        Identifier id = iconAccess.combatant$getIcon();
        if (id == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        AbstractTexture texture = mc.getTextureManager().getTexture(id);
        if (texture == null) return;
        GpuTextureView view = texture.getTextureView();
        GpuSampler sampler = texture.getSampler();
        if (view == null || sampler == null) return;
        int tint = iconAccess.combatant$isIconMonochrome()
                ? scaleAlpha(s.textPrimary, alpha)
                : withAlpha(0xFFFFFFFF, Math.round(255f * clamp01(alpha)));
        Renderer2D.COLOR.textureQuad(view, sampler, x, y, size, size, tint);
    }

    private static void drawStyledComponent(Component component,
                                            TextRenderer baseFont,
                                            float x,
                                            float y,
                                            float size,
                                            int fallbackColor,
                                            float maxWidth) {
        if (component == null || maxWidth <= 0f) return;
        Component converted = LegacyTextUtil.convertLegacyCodesRobust(component);
        boolean clipped = ScissorFunction.pushRaw(x, y - 3f, maxWidth, size + 12f);
        final float[] cursor = {x};
        try {
            converted.visit((style, text) -> {
                if (text == null || text.isEmpty()) return java.util.Optional.empty();
                TextRenderer font = style != null && style.isBold() ? fontBold() : baseFont;
                int color = styledColor(style, fallbackColor);
                drawText(font, text, cursor[0], y, size, color);
                float width = ClickGuiRenderer.textWidth(font, text, size);
                if (style != null && style.isUnderlined()) {
                    Renderer2D.COLOR.quad(cursor[0], y + size + 1f, width, 1f, color);
                }
                if (style != null && style.isStrikethrough()) {
                    Renderer2D.COLOR.quad(cursor[0], y + size * 0.56f, width, 1f, color);
                }
                cursor[0] += width;
                return java.util.Optional.empty();
            }, Style.EMPTY);
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private static List<StyledLine> wrapStyledComponent(Component component,
                                                        TextRenderer baseFont,
                                                        float size,
                                                        float maxWidth,
                                                        int maxLines,
                                                        int fallbackColor) {
        if (component == null || maxWidth <= 0f || maxLines <= 0) return List.of();
        Component converted = LegacyTextUtil.convertLegacyCodesRobust(component);
        ArrayList<StyledRun> source = new ArrayList<>();
        converted.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                source.add(new StyledRun(text, style == null ? Style.EMPTY : style, fallbackColor));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);

        ArrayList<StyledLine> lines = new ArrayList<>();
        ArrayList<StyledRun> current = new ArrayList<>();
        float currentWidth = 0f;

        outer:
        for (StyledRun run : source) {
            String text = run.text;
            int start = 0;
            for (int i = 0; i <= text.length(); i++) {
                boolean end = i == text.length();
                char ch = end ? '\0' : text.charAt(i);
                boolean boundary = end || ch == '\n' || Character.isWhitespace(ch);
                if (!boundary) continue;

                if (i > start) {
                    String word = text.substring(start, i);
                    float wordWidth = styledWidth(run.style, baseFont, word, size);
                    if (currentWidth > 0f && currentWidth + wordWidth > maxWidth) {
                        lines.add(new StyledLine(List.copyOf(current)));
                        if (lines.size() >= maxLines) break outer;
                        current.clear();
                        currentWidth = 0f;
                    }

                    if (wordWidth <= maxWidth) {
                        current.add(run.withText(word));
                        currentWidth += wordWidth;
                    } else {
                        for (int c = 0; c < word.length(); c++) {
                            String glyph = String.valueOf(word.charAt(c));
                            float glyphWidth = styledWidth(run.style, baseFont, glyph, size);
                            if (currentWidth > 0f && currentWidth + glyphWidth > maxWidth) {
                                lines.add(new StyledLine(List.copyOf(current)));
                                if (lines.size() >= maxLines) break outer;
                                current.clear();
                                currentWidth = 0f;
                            }
                            current.add(run.withText(glyph));
                            currentWidth += glyphWidth;
                        }
                    }
                }

                if (!end && ch == '\n') {
                    lines.add(new StyledLine(List.copyOf(current)));
                    if (lines.size() >= maxLines) break outer;
                    current.clear();
                    currentWidth = 0f;
                } else if (!end && Character.isWhitespace(ch)) {
                    String space = " ";
                    float spaceWidth = styledWidth(run.style, baseFont, space, size);
                    if (currentWidth > 0f && currentWidth + spaceWidth <= maxWidth) {
                        current.add(run.withText(space));
                        currentWidth += spaceWidth;
                    }
                }
                start = i + 1;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(new StyledLine(List.copyOf(current)));
        }
        return lines;
    }

    private static float styledWidth(Style style, TextRenderer baseFont, String text, float size) {
        TextRenderer font = style != null && style.isBold() ? fontBold() : baseFont;
        return ClickGuiRenderer.textWidth(font, text, size);
    }

    private static void drawStyledLine(StyledLine line, float x, float y, float size) {
        float cursor = x;
        for (StyledRun run : line.runs) {
            TextRenderer font = run.style != null && run.style.isBold() ? fontBold() : fontRegular();
            int color = styledColor(run.style, run.fallbackColor);
            drawText(font, run.text, cursor, y, size, color);
            float width = ClickGuiRenderer.textWidth(font, run.text, size);
            if (run.style != null && run.style.isUnderlined()) {
                Renderer2D.COLOR.quad(cursor, y + size + 1f, width, 1f, color);
            }
            if (run.style != null && run.style.isStrikethrough()) {
                Renderer2D.COLOR.quad(cursor, y + size * 0.56f, width, 1f, color);
            }
            cursor += width;
        }
    }

    private static int styledColor(Style style, int fallback) {
        if (style == null) return fallback;
        TextColor color = style.getColor();
        if (color == null) return fallback;
        int alpha = (fallback >>> 24) & 0xFF;
        return (alpha << 24) | (color.getValue() & 0x00FFFFFF);
    }

    private static List<String> wrapTooltip(String raw, TextRenderer font, float size, float maxWidth, int maxLines) {
        ArrayList<String> out = new ArrayList<>();
        String[] paragraphs = raw.replace("\r", "").split("\n", -1);
        for (String paragraph : paragraphs) {
            if (out.size() >= maxLines) break;
            if (paragraph.isBlank()) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) out.add("");
                continue;
            }
            int remaining = maxLines - out.size();
            out.addAll(ClickGuiRenderer.wrapText(font, paragraph, size, maxWidth, remaining));
        }
        if (out.size() > maxLines) return new ArrayList<>(out.subList(0, maxLines));
        return out;
    }

    private static Motion motion(Object key, boolean hover, boolean active) {
        Motion m;
        synchronized (MOTION) {
            m = MOTION.computeIfAbsent(key, unused -> new Motion());
        }
        float dt = Math.max(0.001f, AnimationUtility.deltaTime());
        m.hover = AnimationUtility.approach(m.hover, hover ? 1f : 0f, dt, 12.5f);
        m.active = AnimationUtility.approach(m.active, active ? 1f : 0f, dt, 10.5f);
        m.hover = AnimationUtility.snap(m.hover, hover ? 1f : 0f, 0.002f);
        m.active = AnimationUtility.snap(m.active, active ? 1f : 0f, 0.002f);
        return m;
    }

    private static ScrollState scroll(Object key) {
        synchronized (SCROLL) {
            return SCROLL.computeIfAbsent(key, unused -> new ScrollState());
        }
    }

    private static boolean blinkCaret() {
        return (((long) (AnimationUtility.time(1f) / 500f)) & 1L) == 0L;
    }

    private static TextRenderer fontRegular() {
        return BuiltinFontCatalog.ONEST_REGULAR.renderer();
    }

    private static TextRenderer fontMedium() {
        return ClickGuiRenderer.getOnestMedium();
    }

    private static TextRenderer fontBold() {
        return ClickGuiRenderer.getOnestBold();
    }

    private static void drawText(TextRenderer renderer, String text, float x, float y, float size, int color) {
        ClickGuiRenderer.drawText(renderer, text, x, y, size, color, false);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int mix(int from, int to, float t) {
        return SettingsGuiPalette.mix(from, to, clamp01(t));
    }

    private static int withAlpha(int color, int alpha) {
        return SettingsGuiPalette.withAlpha(color, alpha);
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        return withAlpha(color, Math.round(alpha * clamp01(factor)));
    }

    private static final class Motion {
        float hover;
        float active;
    }

    private static final class ScrollState {
        float current;
        float target;

        void clamp(float contentHeight, float viewportHeight) {
            float max = Math.max(0f, contentHeight - viewportHeight);
            target = Math.max(0f, Math.min(max, target));
            current = Math.max(0f, Math.min(max, current));
        }

        void tick() {
            float dt = Math.max(0.001f, AnimationUtility.deltaTime());
            current = AnimationUtility.approach(current, target, dt, 10.5f);
            current = AnimationUtility.snap(current, target, 0.08f);
        }
    }

    private static final class FixedCanvas {
        final float logicalWidth;
        final float logicalHeight;
        final float originX;
        final float originY;
        final float mouseX;
        final float mouseY;

        private FixedCanvas(float logicalWidth, float logicalHeight, float originX, float originY, float mouseX, float mouseY) {
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.originX = originX;
            this.originY = originY;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        static FixedCanvas capture() {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
                return new FixedCanvas(1f, 1f, 0f, 0f, 0f, 0f);
            }
            int framebufferWidth = Math.max(1, mc.getWindow().getWidth());
            int framebufferHeight = Math.max(1, mc.getWindow().getHeight());
            float logicalWidth = HudScale.virtualWidth(framebufferWidth, framebufferHeight);
            float logicalHeight = HudScale.virtualHeight(framebufferWidth, framebufferHeight);
            float uiScale = HudScale.scale(framebufferWidth, framebufferHeight);
            float mouseX = uiScale > 0f ? (float) mc.mouseHandler.xpos() / uiScale : (float) mc.mouseHandler.xpos();
            float mouseY = uiScale > 0f ? (float) mc.mouseHandler.ypos() / uiScale : (float) mc.mouseHandler.ypos();
            float originX = (logicalWidth - CANVAS_W) * 0.5f;
            float originY = (logicalHeight - CANVAS_H) * 0.5f;
            return new FixedCanvas(logicalWidth, logicalHeight, originX, originY, mouseX, mouseY);
        }

        boolean valid() {
            return Float.isFinite(logicalWidth) && Float.isFinite(logicalHeight)
                    && logicalWidth > 0f && logicalHeight > 0f;
        }
    }

    private record Rect(float x, float y, float w, float h) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        boolean intersects(Rect other) {
            return x + w >= other.x && x <= other.x + other.w
                    && y + h >= other.y && y <= other.y + other.h;
        }
    }

    private record PageSlot(AbstractWidget widget, Rect rect, boolean modHeader) {
    }

    private record OptionSlot(AbstractWidget widget,
                              Rect rect,
                              Rect controlRect,
                              Rect resetRect,
                              ControlElement control,
                              SodiumHeaderWidgetAccess header,
                              boolean modHeader,
                              boolean pageHeader,
                              boolean groupHeader) {
    }

    private record ButtonSlot(FlatButtonWidget button, Rect rect) {
    }

    private record PageBuild(List<PageSlot> slots, float contentHeight) {
    }

    private record OptionBuild(List<OptionSlot> slots, float contentHeight) {
    }

    private record StyledRun(String text, Style style, int fallbackColor) {
        StyledRun withText(String value) {
            return new StyledRun(value, style, fallbackColor);
        }
    }

    private record StyledLine(List<StyledRun> runs) {
    }

    private static final class FrameLayout {
        final FixedCanvas canvas;
        final Rect root;
        final Rect search;
        final Rect searchClear;
        final Rect navViewport;
        final Rect optionViewport;
        final Rect infoViewport;
        final List<PageSlot> pages;
        final List<OptionSlot> options;
        final ScrollState pageScroll;
        final ScrollState optionScroll;
        final float pageContentHeight;
        final float optionContentHeight;
        final Rect pageScrollbar;
        final Rect optionScrollbar;
        final ButtonSlot applyButton;
        final ButtonSlot closeButton;
        final ButtonSlot undoButton;
        final Rect promptBounds;
        final List<ButtonSlot> promptButtons;

        FrameLayout(FixedCanvas canvas,
                    Rect root,
                    Rect search,
                    Rect searchClear,
                    Rect navViewport,
                    Rect optionViewport,
                    Rect infoViewport,
                    List<PageSlot> pages,
                    List<OptionSlot> options,
                    ScrollState pageScroll,
                    ScrollState optionScroll,
                    float pageContentHeight,
                    float optionContentHeight,
                    Rect pageScrollbar,
                    Rect optionScrollbar,
                    ButtonSlot applyButton,
                    ButtonSlot closeButton,
                    ButtonSlot undoButton,
                    Rect promptBounds,
                    List<ButtonSlot> promptButtons) {
            this.canvas = canvas;
            this.root = root;
            this.search = search;
            this.searchClear = searchClear;
            this.navViewport = navViewport;
            this.optionViewport = optionViewport;
            this.infoViewport = infoViewport;
            this.pages = pages;
            this.options = options;
            this.pageScroll = pageScroll;
            this.optionScroll = optionScroll;
            this.pageContentHeight = pageContentHeight;
            this.optionContentHeight = optionContentHeight;
            this.pageScrollbar = pageScrollbar;
            this.optionScrollbar = optionScrollbar;
            this.applyButton = applyButton;
            this.closeButton = closeButton;
            this.undoButton = undoButton;
            this.promptBounds = promptBounds;
            this.promptButtons = promptButtons;
        }
    }

    private static final class EnumPopup {
        final VideoSettingsScreen screen;
        final EnumOption<?> option;
        final List<Enum<?>> values;
        final Rect bounds;
        final List<Rect> items;
        final float contentHeight;
        final float scrollMax;
        float reveal;
        float scrollCurrent;
        float scrollTarget;

        EnumPopup(VideoSettingsScreen screen, EnumOption<?> option, List<Enum<?>> values,
                  Rect bounds, List<Rect> items, float contentHeight, float reveal) {
            this.screen = screen;
            this.option = option;
            this.values = values;
            this.bounds = bounds;
            this.items = items;
            this.contentHeight = contentHeight;
            this.scrollMax = Math.max(0f, contentHeight - (bounds.h - 12f));
            this.reveal = reveal;
        }

        void scrollBy(double vertical) {
            if (vertical == 0.0 || scrollMax <= 0f) return;
            scrollTarget -= (float) Math.signum(vertical) * 48f;
            scrollTarget = Math.max(0f, Math.min(scrollMax, scrollTarget));
        }

        void tickScroll() {
            float dt = Math.max(0.001f, AnimationUtility.deltaTime());
            scrollCurrent = AnimationUtility.approach(scrollCurrent, scrollTarget, dt, 12f);
            scrollCurrent = AnimationUtility.snap(scrollCurrent, scrollTarget, 0.08f);
        }
    }

    private record VisualStyle(int accent,
                               int accentBright,
                               int accentSoft,
                               int textPrimary,
                               int textMuted,
                               int glassTint,
                               int shadow,
                               int stroke,
                               int strokeBright,
                               int navTop,
                               int navBottom,
                               int contentTop,
                               int contentBottom,
                               int categoryHoverLeft,
                               int categoryHoverRight,
                               int categorySelectedLeft,
                               int categorySelectedRight,
                               int control,
                               int controlHover,
                               int controlHoverRight,
                               int rowBase,
                               int rowBaseRight,
                               int rowHover,
                               int rowHoverRight,
                               int headerMod,
                               int headerModRight,
                               int scrollTrack,
                               int scrollTrackBright,
                               int scrollHandle,
                               int scrollHandleBright) {
        static VisualStyle from(SettingsGuiPalette p) {
            int accent = Theme.theme().accent();
            int accentSoft = Theme.theme().accentSoft();
            int bright = mix(accent, p.menuHeaderText(), 0.24f);
            return new VisualStyle(
                    accent,
                    bright,
                    accentSoft,
                    p.menuHeaderText(),
                    p.panelMuted(),
                    p.workspaceGlassTint(),
                    p.menuShadow(),
                    p.glassEdgeSoft(),
                    p.glassEdgeStrong(),
                    p.navigationPlaneTop(),
                    p.navigationPlaneBottom(),
                    p.contentPlaneTop(),
                    p.contentPlaneBottom(),
                    p.menuCategoryHoverLeft(),
                    p.menuCategoryHoverRight(),
                    p.menuCategorySelectedLeft(),
                    p.menuCategorySelectedRight(),
                    p.controlSurface(),
                    p.controlSurfaceHover(),
                    mix(p.controlSurfaceHover(), accentSoft, 0.10f),
                    withAlpha(p.contentPlaneTop(), 82),
                    withAlpha(p.contentPlaneBottom(), 92),
                    withAlpha(p.controlSurfaceHover(), 124),
                    withAlpha(mix(p.controlSurfaceHover(), accentSoft, 0.08f), 132),
                    withAlpha(mix(p.navigationPlaneTop(), accent, 0.055f), 182),
                    withAlpha(mix(p.navigationPlaneBottom(), accentSoft, 0.045f), 168),
                    p.panelScrollTrackA(),
                    p.panelScrollTrackB(),
                    p.panelScrollHandleA(),
                    p.panelScrollHandleB()
            );
        }
    }
}
