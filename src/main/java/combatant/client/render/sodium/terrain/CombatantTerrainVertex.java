/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * The terrain vertex layout was implemented with reference to Sodium's
 * CompactChunkVertex packing and Iris-style additional terrain attributes.
 * See THIRD_PARTY_NOTICES.md for the scope of this design reference.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium.terrain;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

// Credit: based on Sodium's CompactChunkVertex packing, with explicit Combatant material attributes.
public final class CombatantTerrainVertex implements ChunkVertexEncoder {
    private static final int POSITION_MAX_VALUE = 1 << 20;
    private static final int TEXTURE_MAX_VALUE = 1 << 15;
    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    private final int stride;
    private final int surfaceFlagsOffset;
    private final int materialDataOffset;
    private final int materialMetaOffset;
    private final int tangentOffset;
    private final int materialSurfaceOffset;

    public CombatantTerrainVertex(int stride,
                                  int surfaceFlagsOffset,
                                  int materialDataOffset,
                                  int materialMetaOffset,
                                  int tangentOffset,
                                  int materialSurfaceOffset) {
        this.stride = stride;
        this.surfaceFlagsOffset = surfaceFlagsOffset;
        this.materialDataOffset = materialDataOffset;
        this.materialMetaOffset = materialMetaOffset;
        this.tangentOffset = tangentOffset;
        this.materialSurfaceOffset = materialSurfaceOffset;
    }

    private static int packPositionHi(int x, int y, int z) {
        return (((x >>> 10) & 0x3FF) << 0) |
                (((y >>> 10) & 0x3FF) << 10) |
                (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        return ((x & 0x3FF) << 0) |
                ((y & 0x3FF) << 10) |
                ((z & 0x3FF) << 20);
    }

    private static int quantizePosition(float position) {
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float v) {
        return (MODEL_ORIGIN + v) / MODEL_RANGE;
    }

    private static int packTexture(int u, int v) {
        return ((u & 0xFFFF) << 0) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float x) {
        int bias = (x < center) ? 1 : -1;
        int quantized = Math.round(x * TEXTURE_MAX_VALUE) + bias;
        return (quantized & 0x7FFF) | (sign(bias) << 15);
    }

    private static int encodeLight(int light) {
        int sky = Mth.clamp(((light >>> 16) & 0xFF) + 8, 8, 248);
        int block = Mth.clamp(((light >>> 0) & 0xFF) + 8, 8, 248);
        return (block << 0) | (sky << 8);
    }

    private static int packLightAndData(int light, int material, int section) {
        return ((light & 0xFFFF) << 0) |
                ((material & 0xFF) << 16) |
                ((section & 0xFF) << 24);
    }

    private static int sign(int x) {
        return (x >>> 31);
    }

    private static int packMaterialMeta(CombatantChunkVertexExtension extension, float ao) {
        int packedAo = Math.round(Mth.clamp(ao, 0.0f, 1.0f) * 255.0f) & 0xFF;
        int mapMask = extension.combatant$getMaterialMapPresenceMask() & 0xFF;
        return packedAo | (mapMask << 8);
    }

    private static int packTangent(Vertex[] vertices) {
        Vertex v0 = vertices[0];
        Vertex v1 = vertices[1];
        Vertex v2 = vertices[2];

        float e1x = v1.x - v0.x;
        float e1y = v1.y - v0.y;
        float e1z = v1.z - v0.z;
        float e2x = v2.x - v0.x;
        float e2y = v2.y - v0.y;
        float e2z = v2.z - v0.z;
        float du1 = v1.u - v0.u;
        float dv1 = v1.v - v0.v;
        float du2 = v2.u - v0.u;
        float dv2 = v2.v - v0.v;

        float determinant = du1 * dv2 - du2 * dv1;
        if (Math.abs(determinant) < 1.0e-8f) {
            return packSnorm4(1.0f, 0.0f, 0.0f, 1.0f);
        }
        float inv = 1.0f / determinant;
        float tx = (e1x * dv2 - e2x * dv1) * inv;
        float ty = (e1y * dv2 - e2y * dv1) * inv;
        float tz = (e1z * dv2 - e2z * dv1) * inv;
        float bx = (e2x * du1 - e1x * du2) * inv;
        float by = (e2y * du1 - e1y * du2) * inv;
        float bz = (e2z * du1 - e1z * du2) * inv;

        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float tangentLength = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (tangentLength < 1.0e-8f || normalLength < 1.0e-8f) {
            return packSnorm4(1.0f, 0.0f, 0.0f, 1.0f);
        }
        tx /= tangentLength;
        ty /= tangentLength;
        tz /= tangentLength;
        nx /= normalLength;
        ny /= normalLength;
        nz /= normalLength;

        float cx = ny * tz - nz * ty;
        float cy = nz * tx - nx * tz;
        float cz = nx * ty - ny * tx;
        float handedness = (cx * bx + cy * by + cz * bz) < 0.0f ? -1.0f : 1.0f;
        return packSnorm4(tx, ty, tz, handedness);
    }

    private static int packSnorm4(float x, float y, float z, float w) {
        return packSnorm8(x)
                | (packSnorm8(y) << 8)
                | (packSnorm8(z) << 16)
                | (packSnorm8(w) << 24);
    }

    private static int packSnorm8(float value) {
        int signed = Math.round(Mth.clamp(value, -1.0f, 1.0f) * 127.0f);
        return signed & 0xFF;
    }

    @Override
    public long write(long ptr, int materialBits, Vertex[] vertices, int sectionIndex) {
        float texCentroidU = 0.0f;
        float texCentroidV = 0.0f;

        for (var vertex : vertices) {
            texCentroidU += vertex.u;
            texCentroidV += vertex.v;
        }

        texCentroidU *= 0.25f;
        texCentroidV *= 0.25f;
        int packedTangent = packTangent(vertices);

        for (int i = 0; i < 4; i++) {
            var vertex = vertices[i];
            CombatantChunkVertexExtension extension = (CombatantChunkVertexExtension) vertex;

            int x = quantizePosition(vertex.x);
            int y = quantizePosition(vertex.y);
            int z = quantizePosition(vertex.z);

            int u = encodeTexture(texCentroidU, vertex.u);
            int v = encodeTexture(texCentroidV, vertex.v);
            int light = encodeLight(vertex.light);

            MemoryUtil.memPutInt(ptr, packPositionHi(x, y, z));
            MemoryUtil.memPutInt(ptr + 4L, packPositionLo(x, y, z));
            // Base/diffuse tint stays unoccluded; AO is a distinct material/lighting signal.
            MemoryUtil.memPutInt(ptr + 8L, vertex.color);
            MemoryUtil.memPutInt(ptr + 12L, packTexture(u, v));
            MemoryUtil.memPutInt(ptr + 16L, packLightAndData(light, materialBits, sectionIndex));
            MemoryUtil.memPutInt(ptr + this.surfaceFlagsOffset, extension.combatant$getSurfaceFlags());
            MemoryUtil.memPutInt(ptr + this.materialDataOffset, extension.combatant$getMaterialId());
            MemoryUtil.memPutInt(ptr + this.materialMetaOffset, packMaterialMeta(extension, vertex.ao));
            MemoryUtil.memPutInt(ptr + this.tangentOffset, packedTangent);
            MemoryUtil.memPutInt(ptr + this.materialSurfaceOffset, extension.combatant$getPackedScalarSurface());

            ptr += this.stride;
        }

        return ptr;
    }
}
