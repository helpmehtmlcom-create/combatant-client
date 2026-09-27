/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.gpu;

import combatant.client.render.engine.asset.gltf.GltfAssetRepository;
import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared residency cache for imported immutable glTF/OBJ geometry.
 *
 * <p>Cache identity is the immutable CPU runtime asset object. Resource reload creates a new CPU
 * epoch and invalidates all physical residency. Backend changes are keyed by RHI object identity so
 * GL/Vulkan handles can never cross the backend boundary.</p>
 */
public final class GltfGpuResidencyManager {
    private static final GltfGpuResidencyManager GLOBAL = new GltfGpuResidencyManager();

    private final Map<GltfRuntimeAsset, GltfGpuAssetResidency> cache = new IdentityHashMap<>();
    private CombatantRhi owner;
    private long sourceEpoch = Long.MIN_VALUE;
    private long uploads;
    private long cacheHits;
    private long invalidations;

    public static GltfGpuResidencyManager global() { return GLOBAL; }

    public synchronized GltfGpuAssetResidency acquire(CombatantRhi rhi, GltfRuntimeAsset asset) {
        if (rhi == null) throw new IllegalArgumentException("RHI is required");
        if (asset == null) throw new IllegalArgumentException("glTF runtime asset is required");
        long currentEpoch = GltfAssetRepository.global().epoch();
        if (asset.sourceEpoch() > 0L && asset.sourceEpoch() != currentEpoch) {
            throw new IllegalStateException("Stale imported asset cannot acquire GPU residency after resource reload: "
                    + asset.asset().id() + " assetEpoch=" + asset.sourceEpoch() + " currentEpoch=" + currentEpoch);
        }
        if (owner != rhi) {
            invalidateImmediate();
            owner = rhi;
            sourceEpoch = currentEpoch;
        } else if (sourceEpoch != currentEpoch) {
            retireCached();
            sourceEpoch = currentEpoch;
        }
        GltfGpuAssetResidency existing = cache.get(asset);
        if (existing != null && !existing.isClosed()) {
            cacheHits++;
            return existing;
        }
        GltfGpuAssetResidency created = GltfGpuAssetResidency.upload(rhi, asset);
        cache.put(asset, created);
        uploads++;
        return created;
    }

    public synchronized void release(GltfRuntimeAsset asset) {
        if (asset == null) return;
        GltfGpuAssetResidency residency = cache.remove(asset);
        if (residency != null) retire(residency);
    }

    /** Called before an RHI owner is destroyed/switched. */
    public synchronized void releaseBackend(CombatantRhi rhi) {
        if (owner != rhi) return;
        invalidateImmediate();
        owner = null;
        sourceEpoch = Long.MIN_VALUE;
    }

    public synchronized void invalidateAll() {
        retireCached();
        sourceEpoch = GltfAssetRepository.global().epoch();
    }

    public synchronized GltfGpuResidencyStats statsSnapshot() {
        int primitiveCount = 0;
        int textureVariants = 0;
        long textureBytes = 0L;
        for (GltfGpuAssetResidency residency : cache.values()) {
            if (!residency.isClosed()) {
                primitiveCount += residency.primitiveCount();
                textureVariants += residency.textureVariantCount();
                textureBytes += residency.textureGpuBytesEstimate();
            }
        }
        return new GltfGpuResidencyStats(cache.size(), primitiveCount, textureVariants, textureBytes,
                uploads, cacheHits, invalidations);
    }

    private void retireCached() {
        if (cache.isEmpty()) return;
        invalidations++;
        for (GltfGpuAssetResidency residency : cache.values()) retire(residency);
        cache.clear();
    }

    private void invalidateImmediate() {
        if (cache.isEmpty()) return;
        invalidations++;
        for (GltfGpuAssetResidency residency : cache.values()) {
            try { residency.close(); } catch (Throwable ignored) { }
        }
        cache.clear();
    }

    private void retire(GltfGpuAssetResidency residency) {
        if (residency == null || residency.isClosed()) return;
        CombatantRhi currentOwner = owner;
        if (currentOwner == null) {
            residency.close();
            return;
        }
        // Imported persistent buffers may still be referenced by commands already submitted in the
        // current frame. Use the existing RHI retirement queue instead of destroying them eagerly.
        currentOwner.resources().retire(residency);
    }

    @AssetLoad(value = {AssetLoadPhase.INITIALIZE, AssetLoadPhase.RELOAD}, order = 60)
    public static void invalidateImportedGpuResidency() {
        GLOBAL.invalidateAll();
    }
}
