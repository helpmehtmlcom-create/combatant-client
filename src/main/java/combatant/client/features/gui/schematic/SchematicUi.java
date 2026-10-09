/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Drawing primitives for the schematic menu, styled after the proxy manager's matte panels. */
final class SchematicUi {

    static final int TEXT = 0xFFEEF2F8;
    static final int MUTED = 0xFF8A93A3;
    static final int FAINT = 0xFF5C6474;
    static final int ACCENT = 0xFF4FC3F7;
    static final int GOOD = 0xFF66BB6A;
    static final int BAD = 0xFFEF5350;
    static final int WARN = 0xFFFF9800;
    static final int SURFACE = 0xFF151922;
    static final int SURFACE_HOVER = 0xFF1E2532;
    static final int STROKE = 0xFF2A3140;

    static final float TITLE = 0.95f;
    static final float BODY = 0.68f;
    static final float SMALL = 0.56f;
    static final float TINY = 0.50f;

    private final Map<String, String> ellipsized = new HashMap<>();
    private TextRenderer bold;
    private TextRenderer body;

    void init() {
        bold = BuiltinFontCatalog.INTER_BOLD.renderer(TextRenderer.get());
        body = BuiltinFontCatalog.INTER_MEDIUM.renderer(bold);
    }

    void clearCaches() {
        ellipsized.clear();
    }

    static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    static int scaleAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0f, 1f)));
    }

    static int mix(int a, int b, float t) {
        return HudRenderUtil.mixColor(a, b, Mth.clamp(t, 0f, 1f));
    }

    static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    void panel(float x, float y, float w, float h, float radius, float alpha) {
        int top = withAlpha(0x12161E, Math.round(alpha * 236f));
        int bottom = withAlpha(0x0B0E14, Math.round(alpha * 242f));
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, radius, 26f, 0.03f * alpha, withAlpha(0x000000, Math.round(alpha * 90f)));
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, 1f, top, top, bottom, bottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, radius, 1f, 1f,
                withAlpha(0x343C4C, Math.round(alpha * 200f)), withAlpha(STROKE, Math.round(alpha * 160f)), 90f);
    }

    void section(float x, float y, float w, float h, float alpha) {
        Renderer2D.COLOR.roundedRect(x, y, w, h, 8f, withAlpha(SURFACE, Math.round(alpha * 200f)));
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, 8f, 1f, withAlpha(STROKE, Math.round(alpha * 140f)));
    }

    void button(float x, float y, float w, float h, String label, float hover, float press, boolean active, boolean enabled, float alpha) {
        float lift = hover * 0.6f + press * 0.4f;
        int base = enabled ? SURFACE : 0xFF101318;
        int fill = mix(base, active ? 0xFF1C3442 : SURFACE_HOVER, enabled ? (active ? 0.75f : 0f) + lift * 0.6f : 0f);
        int stroke = mix(STROKE, ACCENT, active ? 0.65f : hover * 0.45f);
        int text = !enabled ? FAINT : active ? mix(TEXT, ACCENT, 0.35f) : mix(MUTED, TEXT, 0.35f + hover * 0.65f);
        Renderer2D.COLOR.roundedRect(x, y, w, h, 6f, scaleAlpha(fill, alpha * 0.92f));
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, 6f, 1f, scaleAlpha(stroke, alpha));
        String shown = ellipsize(label, BODY * 0.92f, w - 12f);
        textCentered(shown, x + w * 0.5f, y + (h - height(BODY * 0.92f)) * 0.5f - press, BODY * 0.92f, scaleAlpha(text, alpha), false);
    }

    void text(String text, float x, float y, float size, int argb, boolean strong) {
        if (text == null || text.isEmpty()) return;
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    void textCentered(String text, float centerX, float y, float size, int argb, boolean strong) {
        text(text, centerX - width(text, size, strong) * 0.5f, y, size, argb, strong);
    }

    void textRight(String text, float right, float y, float size, int argb, boolean strong) {
        text(text, right - width(text, size, strong), y, size, argb, strong);
    }

    float width(String text, float size, boolean strong) {
        TextRenderer renderer = strong ? bold : body;
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
    }

    float height(float size) {
        if (body == null) return size * 20f;
        body.begin(size, true, false);
        float h = (float) body.getHeight();
        body.end();
        return h;
    }

    /** Ellipsized strings are cached per (text, size, width) because measuring glyph runs every frame adds up. */
    String ellipsize(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        String key = text + '\u0000' + Math.round(size * 100f) + '\u0000' + Math.round(maxWidth);
        String cached = ellipsized.get(key);
        if (cached != null) return cached;
        String out = text;
        if (width(text, size, false) > maxWidth) {
            out = text;
            while (out.length() > 1 && width(out + "...", size, false) > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            out = out + "...";
        }
        if (ellipsized.size() > 2048) ellipsized.clear();
        ellipsized.put(key, out);
        return out;
    }
}
