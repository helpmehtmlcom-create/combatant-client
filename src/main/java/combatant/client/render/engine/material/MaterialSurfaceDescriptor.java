/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import net.minecraft.resources.Identifier;

/**
 * Producer-side material contract. Scalar values are fallbacks and remain distinct from maps:
 * a missing map never means that Combatant should fabricate one from another channel.
 */
public record MaterialSurfaceDescriptor(
        int stableId,
        Identifier spriteId,
        MaterialDomain domain,
        int traitMask,
        MaterialSubmissionRoute submissionRoute,
        MaterialResolutionSource resolutionSource,
        MaterialTextureSet textures,
        float ambientOcclusion,
        float roughness,
        float metallic,
        float dielectricF0,
        float emission,
        float heightScale,
        float transmission,
        float subsurface,
        float clearcoat,
        float clearcoatRoughness,
        float porosity,
        float thickness,
        MaterialTessellationProfile tessellation
) {
    public MaterialSurfaceDescriptor {
        if (spriteId == null) throw new IllegalArgumentException("spriteId");
        if (domain == null) domain = MaterialDomain.UNKNOWN;
        if (submissionRoute == null) submissionRoute = MaterialSubmissionRoute.COMPATIBILITY;
        if (resolutionSource == null) resolutionSource = MaterialResolutionSource.UNKNOWN;
        if (textures == null) textures = new MaterialTextureSet(null);
        ambientOcclusion = clamp01(ambientOcclusion);
        roughness = clamp01(roughness);
        metallic = clamp01(metallic);
        dielectricF0 = clamp01(dielectricF0);
        emission = Math.max(0.0f, emission);
        heightScale = Math.max(0.0f, heightScale);
        transmission = clamp01(transmission);
        subsurface = clamp01(subsurface);
        clearcoat = clamp01(clearcoat);
        clearcoatRoughness = clamp01(clearcoatRoughness);
        porosity = clamp01(porosity);
        thickness = Math.max(0.0f, thickness);
        if (tessellation == null) tessellation = MaterialTessellationProfile.NONE;
    }

    public boolean hasTrait(MaterialTrait trait) {
        return trait != null && (traitMask & trait.bit()) != 0;
    }

    public boolean requiresExtractedPatch() {
        return submissionRoute == MaterialSubmissionRoute.EXTRACTED_PATCH
                || tessellation.mode().requiresPatchMesh();
    }

    public int gpuPresenceMask8() {
        int mask = 0;
        if (textures.has(MaterialTextureSemantic.NORMAL) || textures.has(MaterialTextureSemantic.LABPBR_NORMAL)) mask |= 1 << 0;
        if (textures.has(MaterialTextureSemantic.AMBIENT_OCCLUSION) || textures.has(MaterialTextureSemantic.ORM)) mask |= 1 << 1;
        if (textures.has(MaterialTextureSemantic.ROUGHNESS) || textures.has(MaterialTextureSemantic.ORM) || textures.has(MaterialTextureSemantic.LABPBR_SPECULAR)) mask |= 1 << 2;
        if (textures.has(MaterialTextureSemantic.METALLIC) || textures.has(MaterialTextureSemantic.ORM) || textures.has(MaterialTextureSemantic.LABPBR_SPECULAR)) mask |= 1 << 3;
        if (textures.has(MaterialTextureSemantic.SPECULAR) || textures.has(MaterialTextureSemantic.LABPBR_SPECULAR)) mask |= 1 << 4;
        if (textures.has(MaterialTextureSemantic.EMISSIVE) || textures.has(MaterialTextureSemantic.LABPBR_SPECULAR)) mask |= 1 << 5;
        if (textures.has(MaterialTextureSemantic.HEIGHT) || textures.has(MaterialTextureSemantic.LABPBR_NORMAL)) mask |= 1 << 6;
        if (textures.has(MaterialTextureSemantic.ALBEDO)) mask |= 1 << 7;
        return mask;
    }

    /**
     * Packed feature bits carried with every terrain vertex. These bits express producer-known
     * material policy; the shader never infers tessellation/domain semantics from color/depth.
     */
    public int gpuFeatureMask16() {
        int flags = domain.gpuCode();
        flags |= (tessellation.mode().gpuCode() & 0x3) << 4;
        if (textures.has(MaterialTextureSemantic.HEIGHT) || textures.has(MaterialTextureSemantic.LABPBR_NORMAL)) flags |= 1 << 6;
        if (emission > 0.0f || textures.has(MaterialTextureSemantic.EMISSIVE)
                || textures.has(MaterialTextureSemantic.LABPBR_SPECULAR) || hasTrait(MaterialTrait.EMISSIVE)) flags |= 1 << 7;
        if (transmission > 0.0f || hasTrait(MaterialTrait.TRANSMISSIVE)) flags |= 1 << 8;
        if (subsurface > 0.0f) flags |= 1 << 9;
        if (clearcoat > 0.0f) flags |= 1 << 10;
        if (hasTrait(MaterialTrait.FOLIAGE)) flags |= 1 << 11;
        if (hasTrait(MaterialTrait.GLASS)) flags |= 1 << 12;
        if (hasTrait(MaterialTrait.REFRACTIVE)) flags |= 1 << 13;
        if (hasTrait(MaterialTrait.DOUBLE_SIDED)) flags |= 1 << 14;
        if (hasTrait(MaterialTrait.FLUID)) flags |= 1 << 15;
        return flags & 0xFFFF;
    }

    /** RGBA8 scalar material fallback: roughness, metallic, dielectric F0, emission. */
    public int packScalarSurface() {
        return packUnorm8(roughness)
                | (packUnorm8(metallic) << 8)
                | (packUnorm8(dielectricF0) << 16)
                | (packUnorm8(Math.min(emission, 1.0f)) << 24);
    }

    private static int packUnorm8(float value) {
        return Math.round(clamp01(value) * 255.0f) & 0xFF;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
