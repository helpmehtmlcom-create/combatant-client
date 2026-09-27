/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.material;

import combatant.client.render.engine.asset.gltf.model.AlphaMode;
import combatant.client.render.engine.asset.gltf.model.GltfTextureInfo;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;

/**
 * Canonical Combatant material plus glTF factors that are not yet part of the shared terrain material ABI.
 * Shaderpack adapters consume this contract; import code never encodes Photon/LabPBR channels itself.
 */
public record GltfMaterialBinding(
        MaterialSurfaceDescriptor surface,
        float[] baseColorFactor,
        float[] emissiveFactor,
        float emissiveStrength,
        float normalScale,
        float occlusionStrength,
        AlphaMode alphaMode,
        float alphaCutoff,
        boolean doubleSided,
        boolean unlit,
        GltfTextureInfo baseColorTexture,
        GltfTextureInfo metallicRoughnessTexture,
        GltfTextureInfo normalTexture,
        GltfTextureInfo occlusionTexture,
        GltfTextureInfo emissiveTexture
) {
    public GltfMaterialBinding {
        baseColorFactor = baseColorFactor.clone();
        emissiveFactor = emissiveFactor.clone();
    }

    @Override public float[] baseColorFactor() { return baseColorFactor.clone(); }
    @Override public float[] emissiveFactor() { return emissiveFactor.clone(); }
}
