#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#moj_import <sodium:globals.glsl>
#moj_import <sodium:fog.glsl>
#moj_import <sodium:chunk_material.glsl>

in vec4 v_Color;
in vec2 v_TexCoord;
in vec2 v_FragDistance;
in float fadeFactor;
in float v_ViewDistance;
flat in uint v_CombatantSurfaceFlags;

#ifdef COMBATANT_DEFERRED_GBUFFER
in vec4 v_CombatantBaseColor;
in vec2 v_CombatantLightCoord;
in vec3 v_CombatantViewPosition;
flat in uint v_CombatantMaterialParams;
#endif

uniform sampler2D u_BlockTex;

layout(location = 0) out vec4 fragColor;

#ifdef COMBATANT_DEFERRED_GBUFFER
layout(location = 1) out vec4 combatantGbufferSurface;
layout(location = 2) out vec4 combatantGbufferGeometry;
layout(location = 3) out vec4 combatantGbufferAuxiliary;
#endif

const uint COMBATANT_SURFACE_SOFT_FADE = 1u << 0u;

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    const vec2 offsets[4] = vec2[](
    vec2(0.125, 0.375),
    vec2(-0.125, -0.375),
    vec2(0.375, -0.125),
    vec2(-0.375, 0.125)
    );

    vec4 rgssColor = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColor += textureLod(source, sampleUV, mipLevelExact);
    }
    rgssColor *= 0.25;

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}

#ifdef COMBATANT_DEFERRED_GBUFFER
vec2 combatant_encode_octahedral(vec3 normal) {
    normal /= abs(normal.x) + abs(normal.y) + abs(normal.z);
    vec2 encoded = normal.xy;
    if (normal.z < 0.0) {
        encoded = (1.0 - abs(encoded.yx)) * sign(encoded.xy);
    }
    return encoded * 0.5 + 0.5;
}

float combatant_encode_distance(float distanceValue) {
    return clamp((log2(1.0 + max(distanceValue, 0.0)) + 1.0) / 17.0, 1.0 / 255.0, 1.0);
}
#endif

void main() {
#ifdef COMBATANT_SHADOW_PASS
#ifdef ALPHA_CUTOUT
    vec4 shadowTexel = texture(u_BlockTex, v_TexCoord) * v_Color;
    if (shadowTexel.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = vec4(0.0);
    return;
#endif

    vec4 texel = u_UseRGSS ? sampleRGSS(u_BlockTex, v_TexCoord, u_TexelSize) : sampleNearest(u_BlockTex, v_TexCoord, u_TexelSize);
    vec4 color = texel * v_Color;

    if ((v_CombatantSurfaceFlags & COMBATANT_SURFACE_SOFT_FADE) != 0u) {
        float obstructionAlpha = smoothstep(0.18, 0.85, v_ViewDistance);
        color.a *= obstructionAlpha;
    }

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);

#ifdef COMBATANT_DEFERRED_GBUFFER
    vec3 viewNormal = normalize(cross(dFdx(v_CombatantViewPosition), dFdy(v_CombatantViewPosition)));
    if (!gl_FrontFacing) {
        viewNormal = -viewNormal;
    }
    vec3 albedo = texel.rgb * v_CombatantBaseColor.rgb;
    float material = float(v_CombatantMaterialParams & 255u) / 255.0;
    combatantGbufferSurface = vec4(albedo, 1.0);
    combatantGbufferGeometry = vec4(
        combatant_encode_octahedral(viewNormal),
        clamp(v_CombatantLightCoord, 0.0, 1.0)
    );
    combatantGbufferAuxiliary = vec4(
        combatant_encode_distance(v_FragDistance.y),
        combatant_encode_distance(v_FragDistance.x),
        clamp(fadeFactor, 0.0, 1.0),
        material
    );
#endif
}
