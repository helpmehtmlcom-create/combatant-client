#version 330 core

uniform sampler2D u_BlockAtlas;
uniform sampler2D u_AlbedoAtlas;
uniform sampler2D u_NormalHeightAtlas;
uniform sampler2D u_SurfaceAtlas;
uniform sampler2D u_SpecularAtlas;
uniform sampler2D u_SceneRadiance;
uniform sampler2D u_ResolvedDepth;
uniform sampler2D u_GbufferDepth;
uniform sampler2D u_DepthPyramid;
uniform sampler2D u_GbufferGeometry;
uniform sampler2D u_CascadeColor;
uniform sampler2D u_CascadeDepth;

layout(std140) uniform WaterFrame {
    mat4 u_CurrentView;
    mat4 u_CurrentProjection;
    mat4 u_CurrentInverseProjection;
    mat4 u_CurrentInverseView;
    mat4 u_PreviousView;
    mat4 u_PreviousProjection;
    vec4 u_CurrentCameraTime;
    vec4 u_PreviousCameraTime;
    vec4 u_Viewport;
    vec4 u_DepthTransform;
    vec4 u_Deformation0;
    vec4 u_Deformation1;
    vec4 u_MediumReflection;
    vec4 u_MediumBoundary;
    vec4 u_OpticalAbsorption;
    vec4 u_OpticalScattering;
    vec4 u_ReflectionMeta;
};

struct CombatantReflectionCascadeFace {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 rangeFace;
    vec4 originDelta;
};

layout(std140) uniform WaterReflectionTrace {
    vec4 u_TraceExtentAndFar;
    vec4 u_TraceParams0;
    vec4 u_TraceParams1;
    vec4 u_TraceResolveParams;
    vec4 u_CascadeMeta;
    CombatantReflectionCascadeFace u_CascadeFaces[24];
};

#define COMBATANT_REFLECTION_CASCADE_FACE(i) u_CascadeFaces[i]
#moj_import <combatant:deferred_reflection_trace.glsl>
#moj_import <combatant:deferred_reflection_cascade.glsl>
#undef COMBATANT_REFLECTION_CASCADE_FACE

in vec2 v_Uv;
in vec2 v_LocalSurface;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Optical;
in vec3 v_ViewPosition;
in vec3 v_WorldPosition;
in vec3 v_ViewNormal;
in vec4 v_CurrentClip;
in vec4 v_PreviousClip;
flat in uint v_MaterialId;
flat in uint v_FluidTypeId;
flat in uint v_MapMask;
flat in uint v_FeatureMask;
flat in uint v_SurfaceFlags;
flat in uint v_Surface;

layout(location = 0) out vec4 outReflection;
layout(location = 1) out vec4 outConfidence;

float unpack8(uint packedValue, uint shift) {
    return float((packedValue >> shift) & 255u) / 255.0;
}

vec3 resolveNormal(vec3 geometricNormal) {
    vec3 normal = normalize(geometricNormal);
    if ((v_MapMask & 1u) == 0u) return normal;
    vec3 tangentNormal = normalize(texture(u_NormalHeightAtlas, v_Uv).rgb * 2.0 - 1.0);
    vec3 dpdx = dFdx(v_ViewPosition);
    vec3 dpdy = dFdy(v_ViewPosition);
    vec2 duvdx = dFdx(v_Uv);
    vec2 duvdy = dFdy(v_Uv);
    float det = duvdx.x * duvdy.y - duvdx.y * duvdy.x;
    if (abs(det) <= 1.0e-7) return normal;
    vec3 tangent = normalize((dpdx * duvdy.y - dpdy * duvdx.y) / det);
    vec3 bitangent = normalize(cross(normal, tangent));
    return normalize(mat3(tangent, bitangent, normal) * tangentNormal);
}

CombatantReflectionTracePolicy tracePolicy() {
    CombatantReflectionTracePolicy policy;
    policy.inverseProjection = u_CurrentInverseProjection;
    policy.projection = u_CurrentProjection;
    policy.depthTransform = u_DepthTransform;
    policy.traceExtentAndFar = u_TraceExtentAndFar;
    policy.traceParams0 = u_TraceParams0;
    policy.traceParams1 = u_TraceParams1;
    return policy;
}

void main() {
    vec4 texel = texture(u_BlockAtlas, v_Uv);
    if ((v_MapMask & (1u << 7u)) != 0u) texel.a *= texture(u_AlbedoAtlas, v_Uv).a;
    if (texel.a * v_Color.a <= 1.0e-4) discard;

    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 extent = textureSize(u_ResolvedDepth, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, extent))) discard;
    float opaqueDepth = texelFetch(u_ResolvedDepth, pixel, 0).r;
    if (opaqueDepth > 0.0 && gl_FragCoord.z + 1.0e-6 < opaqueDepth) discard;

    vec3 normal = resolveNormal(v_ViewNormal);
    vec3 incident = normalize(v_ViewPosition);
    vec3 orientedNormal = dot(incident, normal) > 0.0 ? -normal : normal;
    vec3 rayDirection = normalize(reflect(incident, orientedNormal));
    float roughness = unpack8(v_Surface, 0u);
    if ((v_MapMask & (1u << 2u)) != 0u) roughness = texture(u_SurfaceAtlas, v_Uv).g;
    roughness = clamp(roughness, 0.0, 1.0);

    CombatantReflectionTracePolicy policy = tracePolicy();
    CombatantReflectionTraceResult hit = combatantTraceScreenReflection(
            policy, u_ResolvedDepth, u_GbufferDepth, u_DepthPyramid, u_GbufferGeometry,
            v_ViewPosition, orientedNormal, rayDirection, roughness);

    vec3 resolved = vec3(0.0);
    float confidence = 0.0;
    if (hit.valid > 0.5) {
        resolved = max(textureLod(u_SceneRadiance, hit.hitUv, 0.0).rgb, vec3(0.0));
        confidence = hit.confidence;
    }

    int totalFaces = clamp(int(u_CascadeMeta.x + 0.5), 0, 24);
    if (confidence < u_TraceResolveParams.x && u_ReflectionMeta.y > 0.5 && totalFaces > 0) {
        vec3 currentWorldRelative = (u_CurrentInverseView * vec4(v_ViewPosition, 0.0)).xyz;
        vec3 reflectedWorld = normalize((u_CurrentInverseView * vec4(rayDirection, 0.0)).xyz);
        vec3 cascadeColor;
        if (combatantSampleReflectionCascade(u_CascadeColor, u_CascadeDepth,
                currentWorldRelative, reflectedWorld, length(v_ViewPosition), totalFaces, cascadeColor)) {
            resolved = max(cascadeColor, vec3(0.0));
            confidence = clamp(u_TraceResolveParams.y, 0.0, 1.0);
        }
    }

    // Confidence zero is an explicit miss. The forward water shader owns sky/material fallback.
    outReflection = vec4(resolved, 1.0);
    outConfidence = vec4(clamp(confidence, 0.0, 1.0), 0.0, 0.0, 1.0);
}
