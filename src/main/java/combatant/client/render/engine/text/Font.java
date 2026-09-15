/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.text;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.*;
import org.lwjgl.system.MemoryStack;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Font implements GlyphFont {
    private static final int SIZE = 4096;
    private static final int[][] RANGES = {
        {32, 95},      // 32..126
        {160, 96},     // 160..255
        {256, 128},    // 256..383
        {880, 144},    // 880..1023
        {1024, 256},   // 1024..1279
        {8734, 1},     // infinity
        {0xEA00, 64},  // icons
        {0x2000, 112}, // 0x2000..0x206F
        {0x2070, 48},  // 0x2070..0x209F
        {0x20A0, 48},  // 0x20A0..0x20CF
        {0x2100, 80},  // 0x2100..0x214F
        {0x2150, 64},  // 0x2150..0x218F
        {0x2190, 112}, // 0x2190..0x21FF
        {0x2200, 256}, // 0x2200..0x22FF
        {0x2300, 256}, // 0x2300..0x23FF
        {0x2460, 160}, // 0x2460..0x24FF
        {0x2500, 128}, // 0x2500..0x257F
        {0x2580, 32},  // 0x2580..0x259F
        {0x25A0, 96},  // 0x25A0..0x25FF
        {0x2600, 256}, // 0x2600..0x26FF
        {0x2700, 192}, // 0x2700..0x27BF
        {0x2900, 128}, // 0x2900..0x297F
        {0x2B00, 256}, // 0x2B00..0x2BFF
        {0x1F300, 256}, // 0x1F300..0x1F3FF
        {0x1F500, 256}, // 0x1F500..0x1F5FF
        {0x1F600, 256}, // 0x1F600..0x1F6FF
        {0x1FA00, 256}  // 0x1FA00..0x1FAFF
    };
    public final Texture texture;
    private final int height;
    private final float scale;
    private final float ascent;
    private final Int2ObjectOpenHashMap<CharData> charMap = new Int2ObjectOpenHashMap<>();

    public Font(ByteBuffer buffer, int height) {
        this.height = height;

        STBTTFontinfo fontInfo = null;
        ByteBuffer bitmap = null;
        STBTTPackedchar.Buffer[] cdata = null;
        STBTTPackRange.Buffer packRange = null;
        STBTTPackContext packContext = null;
        Texture createdTexture = null;

        try {
            fontInfo = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(fontInfo, buffer)) {
                throw new IllegalArgumentException("Failed to initialize TTF font info");
            }

            bitmap = MemoryUtil.memAlloc(SIZE * SIZE);

            cdata = new STBTTPackedchar.Buffer[RANGES.length];
            for (int i = 0; i < RANGES.length; i++) {
                cdata[i] = STBTTPackedchar.create(RANGES[i][1]);
            }

            packContext = STBTTPackContext.create();
            if (!STBTruetype.stbtt_PackBegin(packContext, bitmap, SIZE, SIZE, 0, 1)) {
                throw new IllegalStateException("Failed to begin font packing");
            }

            packRange = STBTTPackRange.create(RANGES.length);
            for (int i = 0; i < RANGES.length; i++) {
                packRange.get(i).set(height, RANGES[i][0], null, RANGES[i][1], cdata[i], (byte) 2, (byte) 2);
            }

            STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);
            STBTruetype.stbtt_PackEnd(packContext);

            createdTexture = new Texture(SIZE, SIZE, GpuFormat.R8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR);
            createdTexture.upload(bitmap);
            TextRenderSystem.glyphAtlases().registerPage("bitmap:" + height, createdTexture, SIZE, SIZE, false);
            FontDebugStats.noteBitmapAtlasPage();
            scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ascentBuf = stack.mallocInt(1);
                STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, null, null);
                this.ascent = ascentBuf.get(0);
            }

            int registeredGlyphs = 0;
            for (int i = 0; i < cdata.length; i++) {
                STBTTPackedchar.Buffer cbuf = cdata[i];
                int offset = packRange.get(i).first_unicode_codepoint_in_range();

                for (int j = 0; j < cbuf.capacity(); j++) {
                    STBTTPackedchar packedChar = cbuf.get(j);

                    float ipw = 1f / SIZE;
                    float iph = 1f / SIZE;

                    if (packedChar.x0() == packedChar.x1() || packedChar.y0() == packedChar.y1()) {
                        continue;
                    }

                    registeredGlyphs++;
                    charMap.put(j + offset, new CharData(
                            packedChar.xoff(),
                            packedChar.yoff(),
                            packedChar.xoff2(),
                            packedChar.yoff2(),
                            packedChar.x0() * ipw,
                            packedChar.y0() * iph,
                            packedChar.x1() * ipw,
                            packedChar.y1() * iph,
                            packedChar.xadvance()
                    ));
                }
            }
            TextRenderSystem.glyphAtlases().onDirtyRectUpload(registeredGlyphs, SIZE * SIZE);

            this.texture = createdTexture;
            createdTexture = null;
        } catch (Throwable t) {
            if (createdTexture != null) {
                try {
                    createdTexture.close();
                } catch (Throwable ignored) {
                }
            }
            throw t;
        } finally {
            if (cdata != null) {
                for (STBTTPackedchar.Buffer cbuf : cdata) {
                    if (cbuf != null) {
                        try {
                            cbuf.free();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            if (packRange != null) {
                try {
                    packRange.free();
                } catch (Throwable ignored) {
                }
            }
            if (packContext != null) {
                try {
                    packContext.free();
                } catch (Throwable ignored) {
                }
            }
            if (fontInfo != null) {
                try {
                    fontInfo.free();
                } catch (Throwable ignored) {
                }
            }
            if (bitmap != null) {
                try {
                    MemoryUtil.memFree(bitmap);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public double getWidth(String string, int length) {
        double width = 0;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);
            if (c != null) width += c.xAdvance;
            i += Character.charCount(cp);
        }
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public double render(MeshBuilder mesh, String string, double x, double y, RenderColor color, double scale) {
        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);
            if (c == null) {
                i += Character.charCount(cp);
                continue;
            }

            int i1 = mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).raw2(c.u0, c.v0).color(color.r, color.g, color.b, color.a).next();
            int i2 = mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).raw2(c.u0, c.v1).color(color.r, color.g, color.b, color.a).next();
            int i3 = mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).raw2(c.u1, c.v1).color(color.r, color.g, color.b, color.a).next();
            int i4 = mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).raw2(c.u1, c.v0).color(color.r, color.g, color.b, color.a).next();

            mesh.quad(i1, i2, i3, i4);

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    @Override
    public double emitGlyphs(String string, double x, double y, double scale, GlyphConsumer consumer) {
        y += ascent * this.scale * scale;

        int length = string.length();
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);
            if (c == null) {
                i += Character.charCount(cp);
                continue;
            }

            consumer.accept(
                    x + c.x0 * scale,
                    y + c.y0 * scale,
                    x + c.x1 * scale,
                    y + c.y1 * scale,
                    c.u0, c.v0, c.u1, c.v1
            );

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
        }

        return x;
    }

    @Override
    public double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, GlyphGradient gradient) {
        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[2];
        int glyphIndex = 0;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);
            if (c == null) {
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            gradient.colors(glyphIndex, cp, x, colors);
            int leftArgb = colors[0];
            int rightArgb = colors[1];

            int la = (leftArgb >>> 24) & 0xFF;
            int lr = (leftArgb >>> 16) & 0xFF;
            int lg = (leftArgb >>> 8) & 0xFF;
            int lb = leftArgb & 0xFF;

            int ra = (rightArgb >>> 24) & 0xFF;
            int rr = (rightArgb >>> 16) & 0xFF;
            int rg = (rightArgb >>> 8) & 0xFF;
            int rb = rightArgb & 0xFF;

            int i1 = mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).raw2(c.u0, c.v0).color(lr, lg, lb, la).next();
            int i2 = mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).raw2(c.u0, c.v1).color(lr, lg, lb, la).next();
            int i3 = mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).raw2(c.u1, c.v1).color(rr, rg, rb, ra).next();
            int i4 = mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).raw2(c.u1, c.v0).color(rr, rg, rb, ra).next();

            mesh.quad(i1, i2, i3, i4);

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    @Override
    public double renderQuadGradient(MeshBuilder mesh, String string, double x, double y, double scale, GlyphQuadGradient gradient) {
        y += ascent * this.scale * scale;

        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        int[] colors = new int[4];
        int glyphIndex = 0;
        for (int i = 0; i < length; ) {
            int cp = string.codePointAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);
            if (c == null) {
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }

            double x0 = x + c.x0 * scale;
            double y0 = y + c.y0 * scale;
            double x1 = x + c.x1 * scale;
            double y1 = y + c.y1 * scale;

            gradient.colors(glyphIndex, cp, x0, y0, x1, y1, colors);
            int topLeftArgb = colors[0];
            int bottomLeftArgb = colors[1];
            int bottomRightArgb = colors[2];
            int topRightArgb = colors[3];

            int i1 = mesh.vec2(x0, y0).raw2(c.u0, c.v0).color(red(topLeftArgb), green(topLeftArgb), blue(topLeftArgb), alpha(topLeftArgb)).next();
            int i2 = mesh.vec2(x0, y1).raw2(c.u0, c.v1).color(red(bottomLeftArgb), green(bottomLeftArgb), blue(bottomLeftArgb), alpha(bottomLeftArgb)).next();
            int i3 = mesh.vec2(x1, y1).raw2(c.u1, c.v1).color(red(bottomRightArgb), green(bottomRightArgb), blue(bottomRightArgb), alpha(bottomRightArgb)).next();
            int i4 = mesh.vec2(x1, y0).raw2(c.u1, c.v0).color(red(topRightArgb), green(topRightArgb), blue(topRightArgb), alpha(topRightArgb)).next();

            mesh.quad(i1, i2, i3, i4);

            x += c.xAdvance * scale;
            i += Character.charCount(cp);
            glyphIndex++;
        }

        return x;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return charMap.containsKey(codePoint);
    }

    @Override
    public double getAdvance(int codePoint) {
        CharData c = charMap.get(codePoint);
        if (c == null) return 0.0;
        return c.xAdvance;
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

    @Override
    public boolean isReady() {
        return texture != null && texture.isReady();
    }

    @Override
    public void close() {
        if (texture != null) texture.close();
        charMap.clear();
    }

    @FunctionalInterface
    public interface GlyphGradient {
        void colors(int index, int codePoint, double x, int[] out);
    }

    @FunctionalInterface
    public interface GlyphQuadGradient {
        /**
         * Writes colors in vertex order: top-left, bottom-left, bottom-right, top-right.
         */
        void colors(int index, int codePoint, double x0, double y0, double x1, double y1, int[] out);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private record CharData(float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1,
                            float xAdvance) {
    }
}
