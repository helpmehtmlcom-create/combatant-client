/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingsViewLogic;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-panel state and drawing for the module settings page: in-module filter, collapsible sections,
 * changed-from-default markers with reset, a "Reset module" chip, description tooltips and reveal.
 * Layout reuses the settings' own widgets; hit rectangles live in preallocated arrays.
 */
final class ModulesSettingsView {
    private static final int AMBER = 0xFFFFB84D;
    private static final long TOOLTIP_DELAY_NANOS = 350_000_000L;
    private static final long CHANGED_REFRESH_NANOS = 200_000_000L;
    private static final int MIN_SECTION_RUN = 3;

    private String moduleId;
    private String filter = "";
    private boolean filterFocused;
    private String pendingReveal;
    private Setting highlight;
    private float highlightAnim;

    // sections
    private List<Setting> sectionSource;
    private int sectionSourceSize = -1;
    private int[] secOf = new int[0];
    private final List<SettingsViewLogic.Section> sections = new ArrayList<>();
    private boolean[] secCollapsed = new boolean[0];
    private float[] secAnim = new float[0];

    // changed cache
    private final IdentityHashMap<Setting, Boolean> changed = new IdentityHashMap<>();
    private final IdentityHashMap<Setting, float[]> filterAnim = new IdentityHashMap<>();
    private long lastChangedCheck;
    private int changedCount;

    // hits: kind 0 = reset dot (ref Setting), 1 = section header (ref Integer idx)
    private Object[] hitRef = new Object[32];
    private float[] hitRect = new float[32 * 4];
    private int[] hitKind = new int[32];
    private int hitCount;
    private float filterX, filterY, filterW, filterH;
    private float resetX, resetY, resetW, resetH;

    // tooltip
    private Setting tooltipSetting;
    private int tooltipKind;
    private long tooltipSince;
    private float tooltipMx, tooltipMy;
    private float tooltipAnim;

    void revealSetting(String name) {
        pendingReveal = name;
    }

    private void syncModule(String id) {
        if (id != null && id.equals(moduleId)) return;
        moduleId = id;
        filter = "";
        filterFocused = false;
        sectionSource = null;
        sectionSourceSize = -1;
        changed.clear();
        filterAnim.clear();
        changedCount = 0;
        tooltipSetting = null;
        highlight = null;
    }

    private void addHit(int kind, Object ref, float x, float y, float w, float h) {
        if (hitCount == hitRef.length) {
            hitRef = java.util.Arrays.copyOf(hitRef, hitCount * 2);
            hitKind = java.util.Arrays.copyOf(hitKind, hitCount * 2);
            hitRect = java.util.Arrays.copyOf(hitRect, hitCount * 8);
        }
        hitRef[hitCount] = ref;
        hitKind[hitCount] = kind;
        int o = hitCount * 4;
        hitRect[o] = x;
        hitRect[o + 1] = y;
        hitRect[o + 2] = w;
        hitRect[o + 3] = h;
        hitCount++;
    }

    private void rebuildSections(List<Setting> settings) {
        if (settings == sectionSource && settings.size() == sectionSourceSize) return;
        sectionSource = settings;
        sectionSourceSize = settings.size();
        List<String> ids = new ArrayList<>(settings.size());
        for (Setting s : settings) ids.add(s.getName());
        sections.clear();
        sections.addAll(SettingsViewLogic.sections(ids, MIN_SECTION_RUN));
        secOf = new int[settings.size()];
        java.util.Arrays.fill(secOf, -1);
        for (int k = 0; k < sections.size(); k++) {
            SettingsViewLogic.Section s = sections.get(k);
            for (int i = s.start(); i < s.end(); i++) secOf[i] = k;
        }
        secCollapsed = new boolean[sections.size()];
        secAnim = new float[sections.size()];
        java.util.Arrays.fill(secAnim, 1f);
    }

    private void refreshChanged(List<Setting> settings) {
        long now = System.nanoTime();
        if (now - lastChangedCheck < CHANGED_REFRESH_NANOS) return;
        lastChangedCheck = now;
        int n = 0;
        for (Setting s : settings) {
            boolean c;
            try {
                c = s.isChangedFromDefault();
            } catch (RuntimeException e) {
                c = false;
            }
            changed.put(s, c);
            if (c) n++;
        }
        changedCount = n;
    }

    private boolean isChanged(Setting s) {
        Boolean c = changed.get(s);
        return c != null && c;
    }

    private static int withAlpha(int color, float alpha) {
        float a = AnimationUtility.clamp(alpha, 0.0f, 1.0f);
        int na = Math.round(((color >>> 24) & 0xFF) * a);
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    /** Draws filter bar + rows; returns the content height consumed (for scroll range). */
    float renderRows(ModulesMenuPanel panel, float settingsX, float startY, float settingsW,
                     float clipY, float clipH, float mouseX, float mouseY, float alpha, float scale,
                     TextRenderer regular, TextRenderer medium) {
        List<Setting> settings = panel.selectedSettings;
        syncModule(panel.selected);
        rebuildSections(settings);
        refreshChanged(settings);
        hitCount = 0;
        float dt = AnimationUtility.deltaTime();
        float y = startY;
        float total = 0f;

        if (pendingReveal != null) {
            applyRevealPrepare(settings);
        }

        // --- filter bar -------------------------------------------------------------------------
        float barH = 16.0f * scale;
        float barGap = 4.0f * scale;
        float chipW = changedCount > 0 ? 38.0f * scale : 0f;
        float chipGap = changedCount > 0 ? 3.0f * scale : 0f;
        filterX = settingsX;
        filterY = y;
        filterW = settingsW - chipW - chipGap;
        filterH = barH;
        if (y + barH >= clipY && y <= clipY + clipH) {
            boolean hover = ModulesMenuScreen.inside(mouseX, mouseY, filterX, filterY, filterW, filterH);
            int bg = withAlpha(hover || filterFocused ? ModulesMenuStyle.rowHover() : ModulesMenuStyle.panelBgGlassDark(),
                    alpha * (filterFocused ? 0.95f : 0.7f));
            LayoutRender2D.roundedQuad(filterX, filterY, filterW, filterH, 5.0f * scale, bg, bg, bg, bg);
            float size = 7.2f * scale;
            float ty = filterY + (filterH - ClickGuiRenderer.textHeight(regular, size)) * 0.5f;
            float tx = filterX + 6.0f * scale;
            if (filter.isEmpty() && !filterFocused) {
                ClickGuiRenderer.drawText(regular, ClickGuiI18n.tr("clickgui.settings.filter", "Filter settings..."),
                        tx, ty, size, withAlpha(ModulesMenuStyle.textFaint(), alpha), false);
            } else {
                String shown = ClickGuiRenderer.fitText(regular, filter, size, filterW - 12.0f * scale);
                ClickGuiRenderer.drawText(regular, shown, tx, ty, size, withAlpha(ModulesMenuStyle.text(), alpha), false);
                if (filterFocused && ((System.nanoTime() / 450_000_000L) & 1L) == 0L) {
                    float cx = tx + ClickGuiRenderer.textWidth(regular, shown, size) + scale;
                    LayoutRender2D.roundedQuad(cx, ty, Math.max(1f, 0.7f * scale), ClickGuiRenderer.textHeight(regular, size),
                            0f, withAlpha(ModulesMenuStyle.text(), alpha), withAlpha(ModulesMenuStyle.text(), alpha),
                            withAlpha(ModulesMenuStyle.text(), alpha), withAlpha(ModulesMenuStyle.text(), alpha));
                }
            }
            if (hover) SystemCursor.set(SystemCursor.CursorType.TEXT);
        }
        if (changedCount > 0) {
            resetX = settingsX + settingsW - chipW;
            resetY = y;
            resetW = chipW;
            resetH = barH;
            if (y + barH >= clipY && y <= clipY + clipH) {
                boolean hover = ModulesMenuScreen.inside(mouseX, mouseY, resetX, resetY, resetW, resetH);
                int bg = withAlpha(AMBER, alpha * (hover ? 0.38f : 0.2f));
                LayoutRender2D.roundedQuad(resetX, resetY, resetW, resetH, 5.0f * scale, bg, bg, bg, bg);
                float size = 6.6f * scale;
                String label = ClickGuiRenderer.fitText(medium, ClickGuiI18n.tr("clickgui.settings.reset_module", "Reset"),
                        size, resetW - 4.0f * scale);
                float lw = ClickGuiRenderer.textWidth(medium, label, size);
                ClickGuiRenderer.drawText(medium, label, resetX + (resetW - lw) * 0.5f,
                        resetY + (resetH - ClickGuiRenderer.textHeight(medium, size)) * 0.5f, size,
                        withAlpha(AMBER, alpha), false);
                if (hover) {
                    SystemCursor.set(SystemCursor.CursorType.HAND);
                    markTooltip(null, 2, mouseX, mouseY);
                }
            }
        } else {
            resetW = 0f;
        }
        y += barH + barGap;
        total += barH + barGap;

        boolean filtering = !filter.isEmpty();
        Setting hovered = null;
        int hoveredKind = 0;

        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(
                combatant.client.features.gui.clickgui.settings.SettingRenderSurface.MODULES, scale)) {
            int lastHeaderSection = -1;
            for (int i = 0; i < settings.size(); i++) {
                Setting setting = settings.get(i);
                int sec = secOf[i];

                // section header
                if (!filtering && sec >= 0 && sec != lastHeaderSection) {
                    lastHeaderSection = sec;
                    SettingsViewLogic.Section s = sections.get(sec);
                    float hh = 14.0f * scale;
                    secAnim[sec] = AnimationUtility.approach(secAnim[sec], secCollapsed[sec] ? 0f : 1f, dt, 14f);
                    secAnim[sec] = AnimationUtility.snap(secAnim[sec], secCollapsed[sec] ? 0f : 1f, 0.01f);
                    if (y + hh >= clipY && y <= clipY + clipH) {
                        boolean hover = ModulesMenuScreen.inside(mouseX, mouseY, settingsX, y, settingsW, hh);
                        if (hover) {
                            int hb = withAlpha(ModulesMenuStyle.rowHover(), alpha * 0.8f);
                            LayoutRender2D.roundedQuad(settingsX, y, settingsW, hh, 4.0f * scale, hb, hb, hb, hb);
                            SystemCursor.set(SystemCursor.CursorType.HAND);
                        }
                        float size = 6.8f * scale;
                        float ty = y + (hh - ClickGuiRenderer.textHeight(medium, size)) * 0.5f;
                        ClickGuiRenderer.drawText(medium, secAnim[sec] > 0.5f ? "v" : ">", settingsX + 2.0f * scale, ty,
                                size, withAlpha(ModulesMenuStyle.textMuted(), alpha), false);
                        String title = SettingsViewLogic.titleFor(s.key()) + "  " + s.size();
                        ClickGuiRenderer.drawText(medium, title, settingsX + 11.0f * scale, ty, size,
                                withAlpha(ModulesMenuStyle.textMuted(), alpha), false);
                    }
                    addHit(1, sec, settingsX, y, settingsW, hh);
                    y += hh;
                    total += hh;
                }

                float vis = setting.updateVisibilitySafely();
                if (!setting.isVisibilityTargetVisibleSafely() && vis <= 0.01f) continue;

                boolean match = !filtering || SettingsViewLogic.matches(filter, setting.getDisplayName(), setting.getName(),
                        setting.getDescription());
                float[] fa = filterAnim.get(setting);
                if (fa == null) {
                    fa = new float[]{1f};
                    filterAnim.put(setting, fa);
                }
                fa[0] = AnimationUtility.approach(fa[0], match ? 1f : 0f, dt, 16f);
                fa[0] = AnimationUtility.snap(fa[0], match ? 1f : 0f, 0.01f);
                float sectionF = (!filtering && sec >= 0) ? secAnim[sec] : 1f;
                float f = vis * fa[0] * sectionF;
                if (f <= 0.01f) continue;

                float baseH = setting.getHeightSafely();
                float h = baseH * f;
                if (h > 0.5f) {
                    panel.settingHits.add(new ModulesMenuPanel.SettingHit(setting, settingsX, y, settingsW, h));
                    if (y + h >= clipY && y <= clipY + clipH) {
                        float slide = (-baseH + baseH * f) * 0.5f;
                        boolean rowClip = ScissorFunction.pushRaw(settingsX, y, settingsW, h);
                        try {
                            setting.renderSafely(settingsX, y + slide, settingsW, mouseX, mouseY);
                        } finally {
                            if (rowClip) ScissorFunction.pop();
                        }
                        if (setting == highlight && highlightAnim > 0.01f) {
                            int hc = withAlpha(AMBER, alpha * highlightAnim * 0.22f);
                            LayoutRender2D.roundedQuad(settingsX - 2.0f * scale, y, settingsW + 2.0f * scale, h,
                                    4.0f * scale, hc, hc, hc, hc);
                        }
                        if (f > 0.6f && isChanged(setting)) {
                            float dotR = 1.5f * scale;
                            float dcx = settingsX - 3.0f * scale;
                            float dcy = y + Math.min(h * 0.5f, 7.0f * scale);
                            boolean dh = ModulesMenuScreen.inside(mouseX, mouseY, dcx - 3.0f * scale, y, 6.0f * scale,
                                    Math.min(h, 14.0f * scale));
                            ClickGuiRenderer.drawCircle(dcx, dcy, dh ? dotR * 1.5f : dotR,
                                    withAlpha(AMBER, alpha * f));
                            addHit(0, setting, dcx - 3.0f * scale, y, 6.0f * scale, Math.min(h, 14.0f * scale));
                            if (dh) {
                                SystemCursor.set(SystemCursor.CursorType.HAND);
                                hovered = setting;
                                hoveredKind = 1;
                            }
                        }
                        if (hovered == null && ModulesMenuScreen.inside(mouseX, mouseY, settingsX, Math.max(y, clipY), settingsW,
                                Math.min(y + h, clipY + clipH) - Math.max(y, clipY))) {
                            hovered = setting;
                            hoveredKind = 0;
                        }
                    }
                    if (setting == highlight && pendingScrollApplied(panel, setting, y, clipY)) {
                        // scrolled once; fall through
                    }
                }
                y += h;
                total += h;
            }
        }
        if (highlight != null) {
            highlightAnim = AnimationUtility.approach(highlightAnim, 0f, dt, 0.9f);
            if (highlightAnim <= 0.01f) highlight = null;
        }
        if (hovered != null) markTooltip(hovered, hoveredKind, mouseX, mouseY);
        else if (tooltipKind != 2) tooltipSetting = null;
        if (hovered == null && !(resetW > 0f
                && ModulesMenuScreen.inside(mouseX, mouseY, resetX, resetY, resetW, resetH))) {
            tooltipSetting = null;
            tooltipKind = 0;
        }
        return total;
    }

    private boolean scrollPending;
    private float scrollTargetOffset;

    private boolean pendingScrollApplied(ModulesMenuPanel panel, Setting setting, float y, float clipY) {
        if (!scrollPending) return false;
        scrollPending = false;
        panel.settingsScroll = Math.max(0f, (y - (clipY - panel.settingsSmoothScroll)) - 6.0f);
        return true;
    }

    private void applyRevealPrepare(List<Setting> settings) {
        String name = pendingReveal;
        pendingReveal = null;
        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            if (!name.equalsIgnoreCase(s.getName()) && !name.equalsIgnoreCase(s.getId())) continue;
            filter = "";
            if (secOf[i] >= 0) {
                secCollapsed[secOf[i]] = false;
                secAnim[secOf[i]] = 1f;
            }
            highlight = s;
            highlightAnim = 1f;
            scrollPending = true;
            return;
        }
    }

    private void markTooltip(Setting s, int kind, float mx, float my) {
        if (s != tooltipSetting || kind != tooltipKind) {
            tooltipSetting = s;
            tooltipKind = kind;
            tooltipSince = System.nanoTime();
        }
        tooltipMx = mx;
        tooltipMy = my;
    }

    /** Draws the hover tooltip near the cursor; call outside the panel clip. */
    void renderTooltip(float areaX, float areaY, float areaW, float areaH, float alpha, float scale,
                       TextRenderer regular) {
        String text = null;
        boolean ready = (System.nanoTime() - tooltipSince) >= TOOLTIP_DELAY_NANOS;
        if (tooltipKind == 1 && tooltipSetting != null) {
            text = ClickGuiI18n.tr("clickgui.settings.reset_hint", "Click to reset to default");
            ready = true;
        } else if (tooltipKind == 2) {
            text = ClickGuiI18n.tr("clickgui.settings.reset_module_hint", "Reset all changed settings of this module");
        } else if (tooltipSetting != null && ready) {
            String d = tooltipSetting.getDescription();
            if (d != null && !d.isBlank()) text = d;
        }
        float target = text != null && ready ? 1f : 0f;
        tooltipAnim = AnimationUtility.approach(tooltipAnim, target, AnimationUtility.deltaTime(), 14f);
        if (text == null || tooltipAnim <= 0.01f) return;

        float size = 6.8f * scale;
        float maxW = 130.0f * scale;
        List<String> lines = ClickGuiRenderer.wrapText(regular, text, size, maxW, 6);
        float lineH = ClickGuiRenderer.textHeight(regular, size) + scale;
        float w = 0f;
        for (String l : lines) w = Math.max(w, ClickGuiRenderer.textWidth(regular, l, size));
        float pad = 5.0f * scale;
        float bw = w + pad * 2f;
        float bh = lines.size() * lineH + pad * 2f - scale;
        float bx = Math.min(tooltipMx + 10.0f * scale, areaX + areaW - bw - 4.0f * scale);
        float by = tooltipMy + 12.0f * scale;
        if (by + bh > areaY + areaH - 4.0f * scale) by = tooltipMy - bh - 8.0f * scale;
        bx = Math.max(areaX + 4.0f * scale, bx);
        float a = alpha * tooltipAnim;
        int bg = withAlpha(0xF0101216, a);
        LayoutRender2D.roundedQuad(bx, by, bw, bh, 5.0f * scale, bg, bg, bg, bg);
        LayoutRender2D.roundedStrokeQuad(bx, by, bw, bh, 5.0f * scale, 0.5f * scale,
                withAlpha(ModulesMenuStyle.textMuted(), a * 0.4f), withAlpha(ModulesMenuStyle.text(), a * 0.2f),
                withAlpha(ModulesMenuStyle.text(), a * 0.15f), withAlpha(ModulesMenuStyle.textMuted(), a * 0.3f));
        float ty = by + pad;
        for (String l : lines) {
            ClickGuiRenderer.drawText(regular, l, bx + pad, ty, size, withAlpha(ModulesMenuStyle.text(), a), false);
            ty += lineH;
        }
        ClickGuiRenderer.flushRenderer();
    }

    // ---------------------------------------------------------------- input

    boolean mousePressed(ModulesMenuPanel panel, float mx, float my, int button) {
        boolean overFilter = ModulesMenuScreen.inside(mx, my, filterX, filterY, filterW, filterH);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (overFilter) {
                filterFocused = true;
                return true;
            }
            filterFocused = false;
        }
        if (resetW > 0f && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && ModulesMenuScreen.inside(mx, my, resetX, resetY, resetW, resetH)) {
            for (Setting s : panel.selectedSettings) s.resetToDefault();
            lastChangedCheck = 0L;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        for (int i = 0; i < hitCount; i++) {
            int o = i * 4;
            if (!ModulesMenuScreen.inside(mx, my, hitRect[o], hitRect[o + 1], hitRect[o + 2], hitRect[o + 3])) continue;
            if (hitKind[i] == 0) {
                ((Setting) hitRef[i]).resetToDefault();
                lastChangedCheck = 0L;
                return true;
            }
            if (hitKind[i] == 1 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int sec = (Integer) hitRef[i];
                secCollapsed[sec] = !secCollapsed[sec];
                return true;
            }
        }
        return false;
    }

    boolean keyPressed(int keyCode) {
        if (!filterFocused) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (filter.isEmpty()) filterFocused = false;
                else filter = "";
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> filterFocused = false;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!filter.isEmpty()) filter = filter.substring(0, filter.length() - 1);
            }
            default -> {
            }
        }
        return true;
    }

    boolean charTyped(char chr) {
        if (!filterFocused) return false;
        if (chr >= 32 && chr != 127 && filter.length() < 48) filter += chr;
        return true;
    }

    boolean isFilterFocused() {
        return filterFocused;
    }
}
