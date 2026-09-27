/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.runtime;

import combatant.client.render.engine.asset.gltf.model.GltfAsset;
import combatant.client.render.engine.asset.gltf.model.GltfBounds;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.asset.gltf.model.GltfNode;
import combatant.client.render.engine.asset.gltf.model.GltfPrimitive;
import combatant.client.render.engine.asset.gltf.model.GltfScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Precomputed static node/primitive layout for one imported scene.
 *
 * <p>This is CPU metadata only. It prevents every world instance from rebuilding the immutable glTF
 * hierarchy and gives future geometry submission a stable node->mesh primitive placement list.</p>
 */
public final class GltfSceneLayout {
    private final int sceneIndex;
    private final GltfBounds bounds;
    private final List<GltfPrimitivePlacement> primitives;

    private GltfSceneLayout(int sceneIndex, GltfBounds bounds, List<GltfPrimitivePlacement> primitives) {
        this.sceneIndex = sceneIndex;
        this.bounds = bounds;
        this.primitives = List.copyOf(primitives);
    }

    public static GltfSceneLayout build(GltfAsset asset, int sceneIndex) {
        if (asset == null) throw new IllegalArgumentException("glTF asset is required");
        if (sceneIndex < 0 || sceneIndex >= asset.scenes().size()) {
            throw new IllegalArgumentException("glTF scene index is out of range: " + sceneIndex);
        }
        GltfScene scene = asset.scenes().get(sceneIndex);
        ArrayList<GltfPrimitivePlacement> placements = new ArrayList<>();
        BoundsAccumulator accumulator = new BoundsAccumulator();
        for (int root : scene.roots()) {
            appendNode(asset, root, new Matrix4f(), placements, accumulator);
        }
        return new GltfSceneLayout(sceneIndex, accumulator.bounds, placements);
    }

    private static void appendNode(GltfAsset asset,
                                   int nodeIndex,
                                   Matrix4f parentToAsset,
                                   ArrayList<GltfPrimitivePlacement> out,
                                   BoundsAccumulator accumulator) {
        GltfNode node = asset.nodes().get(nodeIndex);
        Matrix4f nodeToAsset = new Matrix4f(parentToAsset).mul(localMatrix(node));

        for (int meshIndex : node.meshes()) {
            GltfMesh mesh = asset.meshes().get(meshIndex);
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                GltfPrimitive primitive = mesh.primitives().get(primitiveIndex);
                GltfBounds transformed = primitive.bounds().transform(nodeToAsset);
                out.add(new GltfPrimitivePlacement(nodeIndex, meshIndex, primitiveIndex, nodeToAsset, transformed));
                accumulator.include(transformed);
            }
        }
        for (int child : node.children()) appendNode(asset, child, nodeToAsset, out, accumulator);
    }

    private static Matrix4f localMatrix(GltfNode node) {
        if (node.hasMatrix()) {
            float[] matrix = node.matrix();
            return new Matrix4f().set(matrix);
        }
        float[] translation = node.translation();
        float[] rotation = node.rotation();
        float[] scale = node.scale();
        return new Matrix4f()
                .translate(translation[0], translation[1], translation[2])
                .rotate(new Quaternionf(rotation[0], rotation[1], rotation[2], rotation[3]))
                .scale(scale[0], scale[1], scale[2]);
    }

    public int sceneIndex() { return sceneIndex; }
    public GltfBounds bounds() { return bounds; }
    public List<GltfPrimitivePlacement> primitives() { return primitives; }
    public boolean hasGeometry() { return bounds.valid() && !primitives.isEmpty(); }

    private static final class BoundsAccumulator {
        GltfBounds bounds = GltfBounds.EMPTY;
        void include(GltfBounds next) { bounds = bounds.union(next); }
    }
}
