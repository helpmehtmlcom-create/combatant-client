/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.runtime;

import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.model.GltfBounds;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.instance.SceneInstanceRegistry;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;

/**
 * Bridge from imported glTF CPU assets to the generic instance/spatial runtime.
 * The default class is {@link SceneDrawClass#ENTITY}; callers must opt into
 * {@link SceneDrawClass#WORLD_OVERLAY} (no depth) or {@link SceneDrawClass#HAND} explicitly.
 */
public final class GltfSceneInstances {
    private GltfSceneInstances() {}

    public static SceneAssetInstance<GltfSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry,
            GltfRuntimeAsset asset,
            Matrix4fc worldTransform,
            SceneLodProfile lodProfile) {
        if (asset == null) throw new IllegalArgumentException("glTF runtime asset is required");
        int sceneIndex = asset.asset().defaultScene();
        if (sceneIndex < 0) throw new IllegalArgumentException("glTF asset has no default scene: " + asset.asset().id());
        return spawn(registry, asset, sceneIndex, worldTransform, SceneDrawClass.ENTITY, lodProfile);
    }

    public static SceneAssetInstance<GltfSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry,
            GltfRuntimeAsset asset,
            Matrix4fc worldTransform,
            SceneDrawClass drawClass,
            SceneLodProfile lodProfile) {
        if (asset == null) throw new IllegalArgumentException("glTF runtime asset is required");
        int sceneIndex = asset.asset().defaultScene();
        if (sceneIndex < 0) throw new IllegalArgumentException("glTF asset has no default scene: " + asset.asset().id());
        return spawn(registry, asset, sceneIndex, worldTransform, drawClass, lodProfile);
    }

    public static SceneAssetInstance<GltfSceneInstanceAsset> spawn(
            SceneInstanceRegistry registry,
            GltfRuntimeAsset asset,
            int sceneIndex,
            Matrix4fc worldTransform,
            SceneLodProfile lodProfile) {
        return spawn(registry, asset, sceneIndex, worldTransform, SceneDrawClass.ENTITY, lodProfile);
    }

    public static SceneAssetInstance<GltfSceneInstanceAsset> spawn(
            SceneInstanceRegistry registry,
            GltfRuntimeAsset asset,
            int sceneIndex,
            Matrix4fc worldTransform,
            SceneDrawClass drawClass,
            SceneLodProfile lodProfile) {
        if (registry == null) throw new IllegalArgumentException("scene instance registry is required");
        if (asset == null) throw new IllegalArgumentException("glTF runtime asset is required");
        GltfSceneLayout layout = asset.sceneLayout(sceneIndex);
        if (!layout.hasGeometry()) {
            throw new IllegalArgumentException("glTF scene contains no renderable geometry: "
                    + asset.asset().id() + " scene=" + sceneIndex);
        }
        GltfBounds bounds = layout.bounds();
        AABB localBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        GltfSceneInstanceAsset source = new GltfSceneInstanceAsset(asset, sceneIndex, layout);
        return registry.register(source, localBounds, worldTransform,
                drawClass == null ? SceneDrawClass.ENTITY : drawClass, lodProfile);
    }
}
