#version 450 core

layout(binding = 0) uniform sampler2D u_BlockAtlas;
layout(binding = 1) uniform sampler2D u_AlbedoAtlas;
layout(binding = 2) uniform sampler2D u_NormalHeightAtlas;
layout(binding = 3) uniform sampler2D u_SurfaceAtlas;
layout(binding = 4) uniform sampler2D u_SpecularAtlas;
layout(binding = 5) uniform sampler2D u_SceneRadiance;
layout(binding = 6) uniform sampler2D u_ResolvedDepth;
layout(binding = 7) uniform sampler2D u_GbufferDepth;
layout(binding = 8) uniform sampler2D u_DepthPyramid;
layout(binding = 9) uniform sampler2D u_GbufferGeometry;
layout(binding = 12) uniform sampler2D u_CascadeColor;
layout(binding = 13) uniform sampler2D u_CascadeDepth;

layout(std430, binding = 11) readonly buffer WaterFrame {
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

layout(std430, binding = 14) readonly buffer ReflectionTraceData {
    mat4 inverseProjection;
    mat4 inverseView;
    mat4 projection;
    vec4 depthTransform;
    vec4 traceExtentAndFar;
    vec4 traceParams0;
    vec4 traceParams1;
    vec4 resolveParams;
} u_Trace;

struct CombatantReflectionCascadeFace {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 rangeFace;
    vec4 originDelta;
};

layout(std430, binding = 15) readonly buffer ReflectionCascadeData {
    CombatantReflectionCascadeFace faces[];
} u_Cascade;

#define COMBATANT_REFLECTION_CASCADE_FACE(i) u_Cascade.faces[i]
#moj_import <combatant:deferred_reflection_trace.glsl>
#moj_import <combatant:deferred_reflection_cascade.glsl>
#undef COMBATANT_REFLECTION_CASCADE_FACE

layout(location = 0) in vec2 te_Uv;
layout(location = 2) in vec4 te_Color;
layout(location = 4) in vec4 te_Optical;
layout(location = 5) in vec3 te_ViewPosition;
layout(location = 7) in vec3 te_ViewNormal;
layout(location = 12) flat in uint te_MapMask;
layout(location = 15) flat in uint te_Surface;

layout(location = 0) out vec4 outReflection;
layout(location = 1) out vec4 outConfidence;

float unpack8(uint packedValue, uint shift) {
    return float((packedValue >> shift) & 255u) / 255.0;
}

vec3 resolveNormal(vec3 geometricNormal) {
    vec3 normal = normalize(geometricNormal);
    if ((te_MapMask & 1u) == 0u) return normal;
    vec3 tangentNormal = normalize(texture(u_NormalHeightAtlas, te_Uv).rgb * 2.0 - 1.0);
    vec3 dpdx = dFdx(te_ViewPosition);
    vec3 dpdy = dFdy(te_ViewPosition);
    vec2 duvdx = dFdx(te_Uv);
    vec2 duvdy = dFdy(te_Uv);
    float det = duvdx.x * duvdy.y - duvdx.y * duvdy.x;
    if (abs(det) <= 1.0e-7) return normal;
    vec3 tangent = normalize((dpdx * duvdy.y - dpdy * duvdx.y) / det);
    vec3 bitangent = normalize(cross(normal, tangent));
    return normalize(mat3(tangent, bitangent, normal) * tangentNormal);
}

CombatantReflectionTracePolicy tracePolicy() {
    CombatantReflectionTracePolicy policy;
    policy.inverseProjection = u_Trace.inverseProjection;
    policy.projection = u_Trace.projection;
    policy.depthTransform = u_Trace.depthTransform;
    policy.traceExtentAndFar = u_Trace.traceExtentAndFar;
    policy.traceParams0 = u_Trace.traceParams0;
    policy.traceParams1 = u_Trace.traceParams1;
    return policy;
}

void main() {
    vec4 texel = texture(u_BlockAtlas, te_Uv);
    if ((te_MapMask & (1u << 7u)) != 0u) texel.a *= texture(u_AlbedoAtlas, te_Uv).a;
    if (texel.a * te_Color.a <= 1.0e-4) discard;

    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 extent = textureSize(u_ResolvedDepth, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, extent))) discard;
    float opaqueDepth = texelFetch(u_ResolvedDepth, pixel, 0).r;
    if (opaqueDepth > 0.0 && gl_FragCoord.z + 1.0e-6 < opaqueDepth) discard;

    vec3 normal = resolveNormal(te_ViewNormal);
    vec3 incident = normalize(te_ViewPosition);
    vec3 orientedNormal = dot(incident, normal) > 0.0 ? -normal : normal;
    vec3 rayDirection = normalize(reflect(incident, orientedNormal));
    float roughness = unpack8(te_Surface, 0u);
    if ((te_MapMask & (1u << 2u)) != 0u) roughness = texture(u_SurfaceAtlas, te_Uv).g;
    roughness = clamp(roughness, 0.0, 1.0);

    CombatantReflectionTracePolicy policy = tracePolicy();
    CombatantReflectionTraceResult hit = combatantTraceScreenReflection(
            policy, u_ResolvedDepth, u_GbufferDepth, u_DepthPyramid, u_GbufferGeometry,
            te_ViewPosition, orientedNormal, rayDirection, roughness);

    vec3 resolved = vec3(0.0);
    float confidence = 0.0;
    if (hit.valid > 0.5) {
        resolved = max(textureLod(u_SceneRadiance, hit.hitUv, 0.0).rgb, vec3(0.0));
        confidence = hit.confidence;
    }

    // The off-screen hierarchy is shared with opaque reflections, but evaluated from the water
    // ray/world position instead of reusing the opaque pixel's resolved reflection.
    if (confidence < u_Trace.resolveParams.x && u_ReflectionMeta.y > 0.5) {
        vec3 currentWorldRelative = (u_Trace.inverseView * vec4(te_ViewPosition, 0.0)).xyz;
        vec3 reflectedWorld = normalize((u_Trace.inverseView * vec4(rayDirection, 0.0)).xyz);
        int totalFaces = clamp(int(u_Cascade.faces[0].rangeFace.w + 0.5), 0, 24);
        vec3 cascadeColor;
        if (combatantSampleReflectionCascade(u_CascadeColor, u_CascadeDepth,
                currentWorldRelative, reflectedWorld, length(te_ViewPosition), totalFaces, cascadeColor)) {
            resolved = max(cascadeColor, vec3(0.0));
            confidence = clamp(u_Trace.resolveParams.y, 0.0, 1.0);
        }
    }

    // A miss intentionally publishes confidence=0. Water shading then selects sky/material fallback;
    // zero radiance here is never treated as a valid black reflection.
    outReflection = vec4(resolved, 1.0);
    outConfidence = vec4(clamp(confidence, 0.0, 1.0), 0.0, 0.0, 1.0);
}
