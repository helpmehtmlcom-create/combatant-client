/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.gltf.model;

/**
 * Internal zero-copy bridge for Combatant glTF's hot render paths.
 *
 * <p>The returned arrays are the immutable asset's backing storage. They are exposed only so the
 * renderer can avoid cloning complete vertex streams every frame. Treat every returned array as
 * strictly read-only. Normal integrations should keep using {@link GltfPrimitive}'s defensive-copy
 * accessors.</p>
 */
public final class GltfPrimitiveAccess {
    private GltfPrimitiveAccess() {}

    public static float[] positions(GltfPrimitive primitive) { return primitive.positionsView(); }
    public static float[] normals(GltfPrimitive primitive) { return primitive.normalsView(); }
    public static float[] tangents(GltfPrimitive primitive) { return primitive.tangentsView(); }
    public static float[] texCoords0(GltfPrimitive primitive) { return primitive.texCoords0View(); }
    public static float[] texCoords1(GltfPrimitive primitive) { return primitive.texCoords1View(); }
    public static float[] colors(GltfPrimitive primitive) { return primitive.colorsView(); }
    public static int[] joints(GltfPrimitive primitive) { return primitive.jointsView(); }
    public static float[] weights(GltfPrimitive primitive) { return primitive.weightsView(); }
    public static int[] indices(GltfPrimitive primitive) { return primitive.indicesView(); }
}
