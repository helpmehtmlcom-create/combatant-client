/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.gpu;

import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.asset.gltf.model.GltfTextureInfo;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent GPU residency for immutable geometry and imported images of one asset.
 * Mesh/image data is shared by every world instance of the asset.
 */
public final class GltfGpuAssetResidency implements AutoCloseable {
    private final CombatantRhi owner;
    private final GltfRuntimeAsset asset;
    private final List<List<GltfGpuPrimitive>> meshes;
    private final List<Map<GltfGpuTextureUsage, GltfGpuTexture>> textures;
    private final GltfGpuTexture fallbackWhiteColor;
    private final GltfGpuTexture fallbackWhiteData;
    private final GltfGpuTexture fallbackNormal;
    private final GltfGpuTexture fallbackBlackColor;
    private boolean closed;

    private GltfGpuAssetResidency(CombatantRhi owner,
                                  GltfRuntimeAsset asset,
                                  List<List<GltfGpuPrimitive>> meshes,
                                  List<Map<GltfGpuTextureUsage, GltfGpuTexture>> textures,
                                  GltfGpuTexture fallbackWhiteColor,
                                  GltfGpuTexture fallbackWhiteData,
                                  GltfGpuTexture fallbackNormal,
                                  GltfGpuTexture fallbackBlackColor) {
        this.owner = owner;
        this.asset = asset;
        this.meshes = List.copyOf(meshes);
        this.textures = List.copyOf(textures);
        this.fallbackWhiteColor = fallbackWhiteColor;
        this.fallbackWhiteData = fallbackWhiteData;
        this.fallbackNormal = fallbackNormal;
        this.fallbackBlackColor = fallbackBlackColor;
    }

    public static GltfGpuAssetResidency upload(CombatantRhi rhi, GltfRuntimeAsset asset) {
        Objects.requireNonNull(rhi, "rhi");
        Objects.requireNonNull(asset, "asset");
        ArrayList<List<GltfGpuPrimitive>> uploadedMeshes = new ArrayList<>(asset.asset().meshes().size());
        ArrayList<GltfGpuPrimitive> primitiveRollback = new ArrayList<>();
        ArrayList<GltfGpuTexture> textureRollback = new ArrayList<>();
        try {
            for (int meshIndex = 0; meshIndex < asset.asset().meshes().size(); meshIndex++) {
                GltfMesh mesh = asset.asset().meshes().get(meshIndex);
                ArrayList<GltfGpuPrimitive> primitives = new ArrayList<>(mesh.primitives().size());
                for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                    String label = "gltf:" + asset.asset().id() + "/mesh_" + meshIndex + "/primitive_" + primitiveIndex;
                    GltfGpuPrimitive primitive = GltfGpuPrimitiveCompiler.upload(
                            rhi, label, asset.compilePrimitive(meshIndex, primitiveIndex));
                    primitives.add(primitive);
                    primitiveRollback.add(primitive);
                }
                uploadedMeshes.add(List.copyOf(primitives));
            }

            boolean[][] required = requiredTextureUsages(asset);
            ArrayList<Map<GltfGpuTextureUsage, GltfGpuTexture>> uploadedTextures =
                    new ArrayList<>(asset.asset().textures().size());
            for (int textureIndex = 0; textureIndex < asset.asset().textures().size(); textureIndex++) {
                EnumMap<GltfGpuTextureUsage, GltfGpuTexture> variants = new EnumMap<>(GltfGpuTextureUsage.class);
                for (GltfGpuTextureUsage usage : GltfGpuTextureUsage.values()) {
                    if (!required[textureIndex][usage.ordinal()]) continue;
                    String label = "gltf:" + asset.asset().id() + "/texture_" + textureIndex
                            + "/" + usage.name().toLowerCase(java.util.Locale.ROOT);
                    GltfGpuTexture texture = GltfGpuTextureCompiler.upload(
                            rhi, label, asset.asset().textures().get(textureIndex), usage);
                    variants.put(usage, texture);
                    textureRollback.add(texture);
                }
                uploadedTextures.add(Map.copyOf(variants));
            }

            GltfGpuTexture whiteColor = GltfGpuTextureCompiler.solid(rhi,
                    "gltf:" + asset.asset().id() + "/fallback_white_color",
                    0xFFFFFFFF, GltfGpuTextureUsage.SRGB_COLOR);
            textureRollback.add(whiteColor);
            GltfGpuTexture whiteData = GltfGpuTextureCompiler.solid(rhi,
                    "gltf:" + asset.asset().id() + "/fallback_white_data",
                    0xFFFFFFFF, GltfGpuTextureUsage.LINEAR_DATA);
            textureRollback.add(whiteData);
            GltfGpuTexture normal = GltfGpuTextureCompiler.solid(rhi,
                    "gltf:" + asset.asset().id() + "/fallback_normal",
                    0xFF8080FF, GltfGpuTextureUsage.NORMAL_DATA);
            textureRollback.add(normal);
            GltfGpuTexture blackColor = GltfGpuTextureCompiler.solid(rhi,
                    "gltf:" + asset.asset().id() + "/fallback_black_color",
                    0xFF000000, GltfGpuTextureUsage.SRGB_COLOR);
            textureRollback.add(blackColor);

            return new GltfGpuAssetResidency(rhi, asset, uploadedMeshes, uploadedTextures,
                    whiteColor, whiteData, normal, blackColor);
        } catch (RuntimeException | Error failure) {
            for (int i = textureRollback.size() - 1; i >= 0; i--) {
                try { textureRollback.get(i).close(); } catch (Throwable ignored) { }
            }
            for (int i = primitiveRollback.size() - 1; i >= 0; i--) {
                try { primitiveRollback.get(i).close(); } catch (Throwable ignored) { }
            }
            throw failure;
        }
    }

    public CombatantRhi owner() { return owner; }
    public GltfRuntimeAsset asset() { return asset; }
    public int meshCount() { return meshes.size(); }
    public boolean isClosed() { return closed; }

    public GltfGpuPrimitive primitive(int meshIndex, int primitiveIndex) {
        ensureOpen();
        return meshes.get(meshIndex).get(primitiveIndex);
    }

    public List<GltfGpuPrimitive> mesh(int meshIndex) {
        ensureOpen();
        return meshes.get(meshIndex);
    }

    /** Returns the exact semantic variant uploaded for this texture, or null when unused by the asset. */
    public GltfGpuTexture texture(int textureIndex, GltfGpuTextureUsage usage) {
        ensureOpen();
        if (textureIndex < 0 || textureIndex >= textures.size() || usage == null) return null;
        return textures.get(textureIndex).get(usage);
    }

    public GltfGpuTexture colorOrWhite(GltfTextureInfo info) {
        if (info == null || !info.present()) return fallbackWhiteColor;
        GltfGpuTexture texture = texture(info.texture(), GltfGpuTextureUsage.SRGB_COLOR);
        return texture != null ? texture : fallbackWhiteColor;
    }

    public GltfGpuTexture emissiveOrBlack(GltfTextureInfo info) {
        if (info == null || !info.present()) return fallbackBlackColor;
        GltfGpuTexture texture = texture(info.texture(), GltfGpuTextureUsage.SRGB_COLOR);
        return texture != null ? texture : fallbackBlackColor;
    }

    public GltfGpuTexture dataOrWhite(GltfTextureInfo info) {
        if (info == null || !info.present()) return fallbackWhiteData;
        GltfGpuTexture texture = texture(info.texture(), GltfGpuTextureUsage.LINEAR_DATA);
        return texture != null ? texture : fallbackWhiteData;
    }

    public GltfGpuTexture normalOrFlat(GltfTextureInfo info) {
        if (info == null || !info.present()) return fallbackNormal;
        GltfGpuTexture texture = texture(info.texture(), GltfGpuTextureUsage.NORMAL_DATA);
        return texture != null ? texture : fallbackNormal;
    }

    public int primitiveCount() {
        int count = 0;
        for (List<GltfGpuPrimitive> mesh : meshes) count += mesh.size();
        return count;
    }

    public int textureVariantCount() {
        int count = 4; // fallback textures are real resident resources.
        for (Map<GltfGpuTextureUsage, GltfGpuTexture> variants : textures) count += variants.size();
        return count;
    }

    public long textureGpuBytesEstimate() {
        long bytes = fallbackWhiteColor.gpuBytesEstimate() + fallbackWhiteData.gpuBytesEstimate()
                + fallbackNormal.gpuBytesEstimate() + fallbackBlackColor.gpuBytesEstimate();
        for (Map<GltfGpuTextureUsage, GltfGpuTexture> variants : textures) {
            for (GltfGpuTexture texture : variants.values()) bytes += texture.gpuBytesEstimate();
        }
        return bytes;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map<GltfGpuTextureUsage, GltfGpuTexture> variants : textures) {
            for (GltfGpuTexture texture : variants.values()) {
                try { texture.close(); } catch (Throwable ignored) { }
            }
        }
        try { fallbackWhiteColor.close(); } catch (Throwable ignored) { }
        try { fallbackWhiteData.close(); } catch (Throwable ignored) { }
        try { fallbackNormal.close(); } catch (Throwable ignored) { }
        try { fallbackBlackColor.close(); } catch (Throwable ignored) { }
        for (List<GltfGpuPrimitive> mesh : meshes) {
            for (GltfGpuPrimitive primitive : mesh) {
                try { primitive.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("glTF GPU residency is closed");
    }

    private static boolean[][] requiredTextureUsages(GltfRuntimeAsset asset) {
        int textureCount = asset.asset().textures().size();
        boolean[][] required = new boolean[textureCount][GltfGpuTextureUsage.values().length];
        for (GltfMaterialBinding material : asset.materials()) {
            require(required, material.baseColorTexture(), GltfGpuTextureUsage.SRGB_COLOR);
            require(required, material.emissiveTexture(), GltfGpuTextureUsage.SRGB_COLOR);
            require(required, material.normalTexture(), GltfGpuTextureUsage.NORMAL_DATA);
            require(required, material.metallicRoughnessTexture(), GltfGpuTextureUsage.LINEAR_DATA);
            require(required, material.occlusionTexture(), GltfGpuTextureUsage.LINEAR_DATA);
        }
        return required;
    }

    private static void require(boolean[][] required, GltfTextureInfo info, GltfGpuTextureUsage usage) {
        if (info == null || !info.present()) return;
        if (info.texture() < 0 || info.texture() >= required.length) {
            throw new IllegalArgumentException("Material references glTF texture outside asset range: " + info.texture());
        }
        required[info.texture()][usage.ordinal()] = true;
    }
}
