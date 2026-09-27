/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf;

import combatant.client.render.engine.asset.gltf.io.MinecraftGltfResolver;
import combatant.client.render.engine.asset.gltf.io.ModelImporters;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBridge;
import combatant.client.render.engine.asset.gltf.model.GltfAsset;
import combatant.client.util.resources.ResourceAccess;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resource-pack scoped CPU asset cache. GPU handles intentionally do not live here: a resource
 * reload invalidates this repository, while GL/Vulkan residency is owned by the RHI lifecycle.
 */
public final class GltfAssetRepository {
    private static final GltfAssetRepository GLOBAL = new GltfAssetRepository();

    private final Map<Identifier, GltfRuntimeAsset> cache = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong(1L);

    public static GltfAssetRepository global() {
        return GLOBAL;
    }

    public GltfRuntimeAsset load(ResourceManager resources, Enum<?> asset) throws IOException {
        return load(resources, ResourceAccess.id(asset));
    }

    public GltfRuntimeAsset load(ResourceManager resources, Identifier id) throws IOException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(id, "id");
        long loadEpoch = epoch.get();
        GltfRuntimeAsset cached = cache.get(id);
        if (cached != null && (cached.sourceEpoch() == 0L || cached.sourceEpoch() == loadEpoch)) return cached;

        GltfAsset imported = ModelImporters.load(id, new MinecraftGltfResolver(resources));
        if (epoch.get() != loadEpoch) {
            throw new IOException("Resource epoch changed while loading imported asset " + id);
        }
        GltfRuntimeAsset prepared = new GltfRuntimeAsset(imported, GltfMaterialBridge.build(imported), loadEpoch);
        GltfRuntimeAsset raced = cache.putIfAbsent(id, prepared);
        return raced == null ? prepared : raced;
    }

    public GltfRuntimeAsset cached(Identifier id) {
        return id == null ? null : cache.get(id);
    }

    public boolean isCached(Identifier id) {
        return id != null && cache.containsKey(id);
    }

    public int cachedCount() {
        return cache.size();
    }

    public long epoch() {
        return epoch.get();
    }

    public void invalidateAll() {
        cache.clear();
        epoch.incrementAndGet();
    }

    @AssetLoad(value = {AssetLoadPhase.INITIALIZE, AssetLoadPhase.RELOAD}, order = 50)
    public static void invalidateResourcePackAssets() {
        GLOBAL.invalidateAll();
    }
}
