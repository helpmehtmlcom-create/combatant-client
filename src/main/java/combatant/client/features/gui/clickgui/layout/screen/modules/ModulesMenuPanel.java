/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.render.engine.animation.AnimationUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModulesMenuPanel {
    final ModulesMenuCategory category;
    final int index;
    final List<ModuleHit> hits = new ArrayList<>();
    final List<SettingHit> settingHits = new ArrayList<>();
    final List<ModuleHit> hitPool = new ArrayList<>();
    final List<SettingHit> settingHitPool = new ArrayList<>();
    int hitCount;
    int settingHitCount;
    final Map<String, Float> enabledAnim = new HashMap<>();
    final Map<String, Float> hoverAnim = new HashMap<>();
    final List<Setting> selectedSettings = new ArrayList<>();

    float x;
    float y;
    float w;
    float h;

    float modulesScroll;
    float modulesSmoothScroll;
    float maxModulesScroll;
    float modulesScrollbarX, modulesScrollbarY, modulesScrollbarW, modulesScrollbarH;
    float modulesScrollbarHandleY, modulesScrollbarHandleH;
    boolean modulesScrollbarDragging;
    float modulesScrollbarDragOffset;

    float settingsScroll;
    float settingsSmoothScroll;
    float maxSettingsScroll;
    float settingsScrollbarX, settingsScrollbarY, settingsScrollbarW, settingsScrollbarH;
    float settingsScrollbarHandleY, settingsScrollbarHandleH;
    boolean settingsScrollbarDragging;
    float settingsScrollbarDragOffset;
    float previewX, previewY, previewW, previewH;

    float swap;
    float anim;

    String selected;
    String selectedTitle;
    String bindingId;

    ModulesMenuPanel(ModulesMenuCategory category, int index) {
        this.category = category;
        this.index = index;
    }

    void reset() {
        selected = null;
        selectedTitle = null;
        selectedSettings.clear();
        settingsScroll = 0.0f;
        settingsSmoothScroll = 0.0f;
        modulesScroll = 0.0f;
        modulesSmoothScroll = 0.0f;
        swap = 0.0f;
        beginLayout();
        enabledAnim.clear();
        hoverAnim.clear();
        previewX = previewY = previewW = previewH = 0.0f;
    }
    void beginLayout() {
        hits.clear();
        settingHits.clear();
        hitCount = 0;
        settingHitCount = 0;
    }

    void addHit(String id, float x, float y, float w, float h, boolean hasSettings, boolean toggleable) {
        ModuleHit hit;
        if (hitCount < hitPool.size()) {
            hit = hitPool.get(hitCount);
            hit.set(id, x, y, w, h, hasSettings, toggleable);
        } else {
            hit = new ModuleHit(id, x, y, w, h, hasSettings, toggleable);
            hitPool.add(hit);
        }
        hits.add(hit);
        hitCount++;
    }

    void addSettingHit(Setting setting, float x, float y, float w, float h) {
        SettingHit hit;
        if (settingHitCount < settingHitPool.size()) {
            hit = settingHitPool.get(settingHitCount);
            hit.set(setting, x, y, w, h);
        } else {
            hit = new SettingHit(setting, x, y, w, h);
            settingHitPool.add(hit);
        }
        settingHits.add(hit);
        settingHitCount++;
    }


    void update() {
        float dt = AnimationUtility.deltaTime();
        if (!Float.isFinite(dt) || dt < 0.0f) dt = 0.016f;
        float target = selected == null ? 0.0f : 1.0f;

        swap = AnimationUtility.approach(swap, target, dt, 7.2f);
        swap = AnimationUtility.snap(swap, target, 0.0015f);
    }

    float enabledAnim(String id, boolean enabled) {
        Float prev = enabledAnim.get(id);
        float next = AnimationUtility.approach(prev == null ? (enabled ? 1.0f : 0.0f) : prev, enabled ? 1.0f : 0.0f, 0.18f);
        enabledAnim.put(id, next);
        return next;
    }

    float enabledAnimValue(String id) {
        Float value = enabledAnim.get(id);
        return value != null ? value : 0.0f;
    }

    float hoverAnim(String id, boolean hover) {
        Float prev = hoverAnim.get(id);
        float next = AnimationUtility.approach(prev == null ? 0.0f : prev, hover ? 1.0f : 0.0f, 0.18f);
        hoverAnim.put(id, next);
        return next;
    }

    float hoverAnimValue(String id) {
        Float value = hoverAnim.get(id);
        return value != null ? value : 0.0f;
    }

    boolean mousePressed(float mx, float my, int button) {
        if (selected != null && swap > 0.2f) {
            float backY = y + 28.0f * ModulesMenuScreen.computePortScale();
            float backH = 20.0f * ModulesMenuScreen.computePortScale();

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && ModulesMenuScreen.inside(mx, my, x, backY, w, backH)) {
                closeSettings();
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && previewW > 0.0f
                    && ModulesMenuScreen.inside(mx, my, previewX, previewY, previewW, previewH)) {
                return ModulesMenuResolver.openPreview(selected);
            }

            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
                SettingHit target = null;
                for (SettingHit hit : settingHits) {
                    if (ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                        target = hit;
                        break;
                    }
                }
                for (Setting setting : selectedSettings) {
                    if (target != null && target.setting == setting) continue;
                    setting.mouseClickedOutsideSafely(mx, my, button);
                }
                if (target != null) {
                    target.setting.mouseClickedSafely(mx, my, button, target.x, target.y, target.w);
                    return true;
                }
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && settingsScrollbarHit(mx, my)) {
                settingsScrollbarDragging = true;
                settingsScrollbarDragOffset = my - settingsScrollbarHandleY;
                updateSettingsScrollbarDrag(my);
                return true;
            }

            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && modulesScrollbarHit(mx, my)) {
            modulesScrollbarDragging = true;
            modulesScrollbarDragOffset = my - modulesScrollbarHandleY;
            updateModulesScrollbarDrag(my);
            return true;
        }

        for (ModuleHit hit : hits) {
            if (!ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;

            if (ModulesMenuScreen.isModuleListEditHeld()
                    && hit.toggleable
                    && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                ModulesMenuResolver.toggleModuleListVisibility(hit.id);
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit.toggleable) {
                ModulesMenuResolver.toggleEntry(hit.id);
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                bindingId = hit.id;
                ModulesMenuResolver.beginBind(hit.id);
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                ModulesMenuResolver.ResolvedSettings resolved = ModulesMenuResolver.resolveSettings(hit.id);
                if (resolved == null) return true;

                selected = resolved.getId();
                selectedTitle = resolved.title();
                selectedSettings.clear();
                selectedSettings.addAll(resolved.settings());
                settingsScroll = 0.0f;
                settingsSmoothScroll = 0.0f;
                return true;
            }

            return true;
        }

        return true;
    }

    void mouseReleased(float mx, float my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            modulesScrollbarDragging = false;
            settingsScrollbarDragging = false;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                setting.mouseReleasedSafely(mx, my, button);
            }
        }
    }

    void scroll(float mx, float my, double amount) {
        if (!Double.isFinite(amount) || !Float.isFinite(mx) || !Float.isFinite(my)) return;
        float portScale = ModulesMenuScreen.computePortScale();
        if (!Float.isFinite(portScale) || portScale <= 0.0f) portScale = 1.0f;
        float delta = (float) (-amount * 20.0f * portScale);
        if (!Float.isFinite(delta)) return;
        if (selected != null) {
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
                for (SettingHit hit : settingHits) {
                    if (!ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;
                    if (hit.setting.mouseScrolledSafely(mx, my, amount)) return;
                    break;
                }
            }
            settingsScroll = AnimationUtility.clamp(settingsScroll + delta, 0.0f, Math.max(0.0f, maxSettingsScroll));
        } else {
            modulesScroll = AnimationUtility.clamp(modulesScroll + delta, 0.0f, Math.max(0.0f, maxModulesScroll));
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected == null) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                modulesScroll = AnimationUtility.clamp(modulesScroll - 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxModulesScroll);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                modulesScroll = AnimationUtility.clamp(modulesScroll + 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxModulesScroll);
                return true;
            }

            if (bindingId != null && !ClickGuiRenderer.waitingForKey) {
                bindingId = null;
            }

            return false;
        }

        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                if (setting.keyPressedSafely(keyCode, scanCode, modifiers)) return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeSettings();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            settingsScroll = AnimationUtility.clamp(settingsScroll - 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxSettingsScroll);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            settingsScroll = AnimationUtility.clamp(settingsScroll + 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxSettingsScroll);
            return true;
        }

        return false;
    }

    boolean charTyped(char chr, int modifiers) {
        if (selected == null) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                if (setting.charTypedSafely(chr, modifiers)) return true;
            }
        }
        return false;
    }


    void updateScrollbarDrag(float mx, float my) {
        if (modulesScrollbarDragging) updateModulesScrollbarDrag(my);
        if (settingsScrollbarDragging) updateSettingsScrollbarDrag(my);
    }

    boolean isDraggingScrollbar() {
        return modulesScrollbarDragging || settingsScrollbarDragging;
    }

    private boolean modulesScrollbarHit(float mx, float my) {
        return maxModulesScroll > 0.5f && ModulesMenuScreen.inside(mx, my, modulesScrollbarX - 3f * ModulesMenuScreen.computePortScale(), modulesScrollbarY, modulesScrollbarW + 6f * ModulesMenuScreen.computePortScale(), modulesScrollbarH);
    }

    private boolean settingsScrollbarHit(float mx, float my) {
        return maxSettingsScroll > 0.5f && ModulesMenuScreen.inside(mx, my, settingsScrollbarX - 3f * ModulesMenuScreen.computePortScale(), settingsScrollbarY, settingsScrollbarW + 6f * ModulesMenuScreen.computePortScale(), settingsScrollbarH);
    }

    private void updateModulesScrollbarDrag(float my) {
        if (!Float.isFinite(my) || !Float.isFinite(maxModulesScroll)) return;
        float track = Math.max(1f, modulesScrollbarH - modulesScrollbarHandleH);
        float handleY = AnimationUtility.clamp(my - modulesScrollbarDragOffset, modulesScrollbarY, modulesScrollbarY + track);
        float t = track > 0.001f ? (handleY - modulesScrollbarY) / track : 0.0f;
        modulesScroll = AnimationUtility.clamp(t * maxModulesScroll, 0.0f, Math.max(0.0f, maxModulesScroll));
    }

    private void updateSettingsScrollbarDrag(float my) {
        if (!Float.isFinite(my) || !Float.isFinite(maxSettingsScroll)) return;
        float track = Math.max(1f, settingsScrollbarH - settingsScrollbarHandleH);
        float handleY = AnimationUtility.clamp(my - settingsScrollbarDragOffset, settingsScrollbarY, settingsScrollbarY + track);
        float t = track > 0.001f ? (handleY - settingsScrollbarY) / track : 0.0f;
        settingsScroll = AnimationUtility.clamp(t * maxSettingsScroll, 0.0f, Math.max(0.0f, maxSettingsScroll));
    }

    private void closeSettings() {
        selected = null;
        selectedSettings.clear();
        settingsScroll = 0.0f;
        settingsSmoothScroll = 0.0f;
    }

    static final class ModuleHit {
        String id;
        float x;
        float y;
        float w;
        float h;
        boolean hasSettings;
        boolean toggleable;

        ModuleHit(String id, float x, float y, float w, float h, boolean hasSettings, boolean toggleable) {
            set(id, x, y, w, h, hasSettings, toggleable);
        }

        void set(String id, float x, float y, float w, float h, boolean hasSettings, boolean toggleable) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.hasSettings = hasSettings;
            this.toggleable = toggleable;
        }

        String id() { return id; }
        float x() { return x; }
        float y() { return y; }
        float w() { return w; }
        float h() { return h; }
        boolean hasSettings() { return hasSettings; }
        boolean toggleable() { return toggleable; }
    }

    static final class SettingHit {
        Setting setting;
        float x;
        float y;
        float w;
        float h;

        SettingHit(Setting setting, float x, float y, float w, float h) {
            set(setting, x, y, w, h);
        }

        void set(Setting setting, float x, float y, float w, float h) {
            this.setting = setting;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Setting setting() { return setting; }
        float x() { return x; }
        float y() { return y; }
        float w() { return w; }
        float h() { return h; }
    }
}
