/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.engine.material.MaterialTessellationMode;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sodium-meshing-time extraction of patch-topology surfaces.
 *
 * <p>Water is extracted only from explicit WATER producers. Ordinary terrain is extracted only
 * when an explicit material descriptor requests HEIGHT_DISPLACEMENT. A height map by itself never
 * changes Sodium's shared triangle topology.</p>
 */
public final class WaterSurfaceExtractor {
    private static final ThreadLocal<BuildCapture> BUILD = new ThreadLocal<>();
    private static final Map<Long, SectionPatchMesh> SECTIONS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();

    private WaterSurfaceExtractor() {
    }

    public static void beginSection(long sectionKey) {
        BUILD.remove();
        BUILD.set(new BuildCapture(sectionKey, new ArrayList<>(), new ArrayList<>()));
    }

    public static void capture(FluidSurfaceData surface) {
        if (surface == null || !surface.isTopSurface() || surface.domain() != MaterialDomain.WATER) return;
        if (surface.material() == null
                || surface.material().tessellation().mode() != MaterialTessellationMode.WATER_SURFACE) return;
        BuildCapture capture = BUILD.get();
        if (capture == null) return;
        if (SectionPos.asLong(surface.blockPos()) != capture.sectionKey) return;
        capture.waterPatches.add(WaterPatch.from(surface));
    }

    /** Extracts a normal block-model quad only when topology was explicitly requested by its descriptor. */
    public static boolean captureHeight(BlockPos worldPos,
                                        BlockPos renderOrigin,
                                        MutableQuadViewImpl quad,
                                        MaterialSurfaceDescriptor material,
                                        net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder.Vertex[] vertices) {
        if (worldPos == null || renderOrigin == null || quad == null || material == null) return false;
        if (material.tessellation().mode() != MaterialTessellationMode.HEIGHT_DISPLACEMENT) return false;
        BuildCapture capture = BUILD.get();
        if (capture == null || SectionPos.asLong(worldPos) != capture.sectionKey) return false;

        float[] positions = new float[12];
        float[] uvs = new float[8];
        int[] color = new int[4];
        float[] ao = new float[4];
        int[] light = new int[4];
        for (int i = 0; i < 4; i++) {
            positions[i * 3] = worldPos.getX() + quad.getX(i);
            positions[i * 3 + 1] = worldPos.getY() + quad.getY(i);
            positions[i * 3 + 2] = worldPos.getZ() + quad.getZ(i);
            uvs[i * 2] = quad.getTexU(i);
            uvs[i * 2 + 1] = quad.getTexV(i);
            if (vertices != null && i < vertices.length && vertices[i] != null) {
                color[i] = vertices[i].color;
                ao[i] = vertices[i].ao;
                light[i] = vertices[i].light;
            } else {
                color[i] = 0xFFFFFFFF;
                ao[i] = 1.0f;
                light[i] = 0x00F000F0;
            }
        }
        var tess = material.tessellation();
        capture.heightPatches.add(new HeightPatch(
                worldPos.asLong(), material.stableId(), positions, uvs, color, ao, light,
                combatant.client.render.engine.material.MaterialRegistry.global().gpuPresenceMask(material),
                material.gpuFeatureMask16(), material.packScalarSurface(),
                tess.displacementScale(), tess.minFactor(), tess.maxFactor(),
                tess.distanceFadeStart(), tess.distanceFadeEnd()
        ));
        return true;
    }

    public static void finishSection(boolean publish) {
        BuildCapture capture = BUILD.get();
        BUILD.remove();
        if (capture == null || !publish) return;
        long generation = GENERATION.incrementAndGet();
        SECTIONS.put(capture.sectionKey, new SectionPatchMesh(
                capture.sectionKey, generation,
                List.copyOf(capture.waterPatches), List.copyOf(capture.heightPatches)
        ));
    }

    public static void discardSection(long sectionKey) {
        SECTIONS.remove(sectionKey);
    }

    public static SectionPatchMesh section(long sectionKey) {
        return SECTIONS.get(sectionKey);
    }

    public static Map<Long, SectionPatchMesh> snapshot() {
        return Map.copyOf(SECTIONS);
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static boolean hasWaterPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (!section.waterPatches().isEmpty()) return true;
        }
        return false;
    }

    public static boolean hasHeightPatches() {
        for (SectionPatchMesh section : SECTIONS.values()) {
            if (!section.heightPatches().isEmpty()) return true;
        }
        return false;
    }


    /** Exact base extracted top-surface sample for a producer-known fluid column, when available. */
    public static SurfaceSample sampleTopSurface(BlockPos blockPos, int fluidTypeId, double worldX, double worldZ) {
        if (blockPos == null) return SurfaceSample.UNKNOWN;
        SectionPatchMesh section = SECTIONS.get(SectionPos.asLong(blockPos));
        if (section == null) return SurfaceSample.UNKNOWN;
        long packedPos = blockPos.asLong();
        for (WaterPatch patch : section.waterPatches()) {
            if (patch.blockPos() != packedPos || patch.fluidTypeId() != fluidTypeId) continue;
            float localX = (float) (worldX - blockPos.getX());
            float localZ = (float) (worldZ - blockPos.getZ());
            float y = samplePatchHeight(patch, localX, localZ);
            if (Float.isFinite(y)) return new SurfaceSample(y, true);
        }
        return SurfaceSample.UNKNOWN;
    }

    private static float samplePatchHeight(WaterPatch patch, float localX, float localZ) {
        float[] local = patch.localSurfaceCoordinates();
        float[] positions = patch.positions();
        float h00 = nearestCornerHeight(local, positions, 0.0f, 0.0f);
        float h10 = nearestCornerHeight(local, positions, 1.0f, 0.0f);
        float h11 = nearestCornerHeight(local, positions, 1.0f, 1.0f);
        float h01 = nearestCornerHeight(local, positions, 0.0f, 1.0f);
        float u = Math.max(0.0f, Math.min(1.0f, localX));
        float v = Math.max(0.0f, Math.min(1.0f, localZ));
        float a = h00 + (h10 - h00) * u;
        float b = h01 + (h11 - h01) * u;
        return a + (b - a) * v;
    }

    private static float nearestCornerHeight(float[] local, float[] positions, float targetX, float targetZ) {
        int best = 0;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float dx = local[i * 2] - targetX;
            float dz = local[i * 2 + 1] - targetZ;
            float d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return positions[best * 3 + 1];
    }

    public record SurfaceSample(float worldY, boolean valid) {
        public static final SurfaceSample UNKNOWN = new SurfaceSample(0.0f, false);
    }

    public static void clear() {
        BUILD.remove();
        SECTIONS.clear();
        GENERATION.incrementAndGet();
    }

    private record BuildCapture(long sectionKey,
                                ArrayList<WaterPatch> waterPatches,
                                ArrayList<HeightPatch> heightPatches) {
    }

    /** Patch-compatible water geometry: four control points in world space plus exact atlas UVs. */
    public record WaterPatch(
            long blockPos,
            int materialId,
            int fluidTypeId,
            float flowX,
            float flowZ,
            float flowStrength,
            int surfaceFlags,
            int fluidConnectivity,
            float cellBaseY,
            float surfaceNormalX,
            float surfaceNormalY,
            float surfaceNormalZ,
            float[] positions,
            float[] localSurfaceCoordinates,
            float[] uvs,
            int[] color,
            float[] ao,
            int[] light,
            int mapMask,
            int featureMask,
            int packedSurface,
            float transmission,
            float fallbackThickness,
            float displacementScale,
            float minTessFactor,
            float maxTessFactor,
            float distanceFadeStart,
            float distanceFadeEnd
    ) {
        static WaterPatch from(FluidSurfaceData surface) {
            float[] positions = new float[12];
            float[] localSurfaceCoordinates = new float[8];
            float[] uvs = new float[8];
            for (int i = 0; i < 4; i++) {
                positions[i * 3] = surface.blockPos().getX() + surface.x()[i];
                positions[i * 3 + 1] = surface.blockPos().getY() + surface.y()[i];
                positions[i * 3 + 2] = surface.blockPos().getZ() + surface.z()[i];
                localSurfaceCoordinates[i * 2] = surface.x()[i];
                localSurfaceCoordinates[i * 2 + 1] = surface.z()[i];
                uvs[i * 2] = surface.u()[i];
                uvs[i * 2 + 1] = surface.v()[i];
            }
            var tess = surface.material().tessellation();
            float[] normal = surface.surfaceNormal();
            return new WaterPatch(
                    surface.blockPos().asLong(), surface.materialId(), surface.fluidTypeId(),
                    surface.flowX(), surface.flowZ(), surface.flowStrength(), surface.surfaceFlags(),
                    surface.fluidConnectivity(), surface.blockPos().getY(), normal[0], normal[1], normal[2],
                    positions, localSurfaceCoordinates, uvs, surface.color(), surface.ao(), surface.light(),
                    surface.mapMask(), surface.featureMask(), surface.packedSurface(),
                    surface.material().transmission(), surface.material().thickness(),
                    tess.displacementScale(), tess.minFactor(), tess.maxFactor(),
                    tess.distanceFadeStart(), tess.distanceFadeEnd()
            );
        }
    }

    /** Explicit height-displacement patch. Presence of a height texture alone cannot create this record. */
    public record HeightPatch(
            long blockPos,
            int materialId,
            float[] positions,
            float[] uvs,
            int[] color,
            float[] ao,
            int[] light,
            int mapMask,
            int featureMask,
            int packedSurface,
            float displacementScale,
            float minTessFactor,
            float maxTessFactor,
            float distanceFadeStart,
            float distanceFadeEnd
    ) {
    }

    public record SectionPatchMesh(long sectionKey,
                                   long generation,
                                   List<WaterPatch> waterPatches,
                                   List<HeightPatch> heightPatches) {
        public boolean empty() {
            return waterPatches.isEmpty() && heightPatches.isEmpty();
        }
    }
}
