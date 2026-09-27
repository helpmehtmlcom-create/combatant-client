/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf;

import combatant.client.render.engine.asset.gltf.geometry.GltfCompiledPrimitive;
import combatant.client.render.engine.asset.gltf.geometry.GltfGeometryCompiler;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfAsset;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.asset.gltf.model.GltfPrimitive;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneLayout;

import java.util.List;
import java.util.Objects;

/**
 * Immutable imported asset plus Combatant material bindings. This deliberately stops before GPU
 * residency: OpenGL and Vulkan upload the same compiled primitive through the shared RHI layer.
 */
public final class GltfRuntimeAsset {
    private final GltfAsset asset;
    private final List<GltfMaterialBinding> materials;
    private final List<GltfSceneLayout> sceneLayouts;
    private final long sourceEpoch;

    public GltfRuntimeAsset(GltfAsset asset, List<GltfMaterialBinding> materials) {
        this(asset, materials, 0L);
    }

    public GltfRuntimeAsset(GltfAsset asset, List<GltfMaterialBinding> materials, long sourceEpoch) {
        this.asset = Objects.requireNonNull(asset, "asset");
        this.materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        this.sourceEpoch = Math.max(0L, sourceEpoch);
        if (this.materials.size() != asset.materials().size()) {
            throw new IllegalArgumentException("Material binding count does not match imported asset");
        }
        java.util.ArrayList<GltfSceneLayout> layouts = new java.util.ArrayList<>(asset.scenes().size());
        for (int sceneIndex = 0; sceneIndex < asset.scenes().size(); sceneIndex++) {
            layouts.add(GltfSceneLayout.build(asset, sceneIndex));
        }
        this.sceneLayouts = List.copyOf(layouts);
    }

    public GltfAsset asset() {
        return asset;
    }

    public List<GltfMaterialBinding> materials() {
        return materials;
    }

    public GltfMaterialBinding material(int index) {
        return materials.get(index);
    }

    public GltfSceneLayout sceneLayout(int sceneIndex) {
        return sceneLayouts.get(sceneIndex);
    }

    public GltfSceneLayout defaultSceneLayout() {
        int sceneIndex = asset.defaultScene();
        if (sceneIndex < 0) throw new IllegalStateException("glTF asset has no default scene: " + asset.id());
        return sceneLayouts.get(sceneIndex);
    }

    public List<GltfSceneLayout> sceneLayouts() {
        return sceneLayouts;
    }

    /** Zero means caller-managed/non-repository asset; positive values are resource-pack epochs. */
    public long sourceEpoch() {
        return sourceEpoch;
    }

    public GltfCompiledPrimitive compilePrimitive(int meshIndex, int primitiveIndex) {
        GltfMesh mesh = asset.meshes().get(meshIndex);
        GltfPrimitive primitive = mesh.primitives().get(primitiveIndex);
        int normalTexCoordSet = material(primitive.material()).normalTexture().texCoord();
        return GltfGeometryCompiler.compile(primitive, normalTexCoordSet);
    }
}
