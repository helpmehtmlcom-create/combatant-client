#version 450 core

layout(binding = 0) uniform sampler2D u_BlockAtlas;
layout(binding = 1) uniform sampler2D u_AlbedoAtlas;
layout(binding = 2) uniform sampler2D u_NormalHeightAtlas;
layout(binding = 3) uniform sampler2D u_SurfaceAtlas;
layout(binding = 4) uniform sampler2D u_SpecularAtlas;
layout(binding = 5) uniform sampler2D u_WaterReflectionColor;
layout(binding = 6) uniform sampler2D u_WaterReflectionConfidence;
layout(binding = 7) uniform sampler2D u_SceneRadiance;
layout(binding = 8) uniform sampler2D u_ResolvedDepth;
layout(binding = 9) uniform sampler2D u_SkySpecular;

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

layout(location = 0) in vec2 te_Uv;
layout(location = 1) in vec2 te_LocalSurface;
layout(location = 2) in vec4 te_Color;
layout(location = 3) in vec4 te_Params;
layout(location = 4) in vec4 te_Optical;
layout(location = 5) in vec3 te_ViewPosition;
layout(location = 6) in vec3 te_WorldPosition;
layout(location = 7) in vec3 te_ViewNormal;
layout(location = 8) in vec4 te_CurrentClip;
layout(location = 9) in vec4 te_PreviousClip;
layout(location = 10) flat in uint te_MaterialId;
layout(location = 11) flat in uint te_FluidTypeId;
layout(location = 12) flat in uint te_MapMask;
layout(location = 13) flat in uint te_FeatureMask;
layout(location = 14) flat in uint te_SurfaceFlags;
layout(location = 15) flat in uint te_Surface;

layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outVelocity;
layout(location = 2) out vec4 outMotionValidity;
layout(location = 3) out vec4 outTemporalCoverage;
layout(location = 4) out vec4 outReactiveMask;

const float PI = 3.14159265358979323846;
const int THICKNESS_VALID_SCREEN_SPACE = 1;
const int THICKNESS_UNKNOWN_OFFSCREEN = 2;
const int THICKNESS_CAMERA_INSIDE = 3;
const int THICKNESS_MISSING_BACK_SURFACE = 4;

float unpack8(uint packedValue, uint shift) {
    return float((packedValue >> shift) & 255u) / 255.0;
}

vec3 reconstructView(vec2 uv, float depth) {
    vec2 ndcXY = uv * 2.0 - 1.0;
    float ndcZ = depth * u_DepthTransform.x + u_DepthTransform.y;
    vec4 h = u_CurrentInverseProjection * vec4(ndcXY, ndcZ, 1.0);
    if (abs(h.w) < 1.0e-7) return vec3(0.0);
    return h.xyz / h.w;
}

bool projectUv(vec3 viewPosition, out vec2 uv) {
    vec4 clip = u_CurrentProjection * vec4(viewPosition, 1.0);
    if (clip.w <= 1.0e-7) {
        uv = vec2(0.5);
        return false;
    }
    uv = clip.xy / clip.w * 0.5 + 0.5;
    return all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
}

vec2 latLongFromDirection(vec3 d) {
    d = normalize(d);
    return vec2(atan(d.z, d.x) / (2.0 * PI) + 0.5,
                acos(clamp(d.y, -1.0, 1.0)) / PI);
}

float iorFromF0(float f0) {
    float root = sqrt(clamp(f0, 1.0e-5, 0.98));
    return clamp((1.0 + root) / max(1.0 - root, 1.0e-4), 1.0001, 4.0);
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

vec3 reflectionHierarchy(vec2 screenUv, vec3 normal, vec3 viewDir, float roughness) {
    bool hasWaterReflection = u_MediumReflection.z > 0.5;
    bool hasSky = u_MediumReflection.w > 0.5;

    vec3 reflectedView = normalize(reflect(-viewDir, normal));
    vec3 reflectedWorld = normalize(mat3(u_CurrentInverseView) * reflectedView);
    vec2 envUv = latLongFromDirection(reflectedWorld);

    // Explicit hierarchy tail. The dedicated water trace pass already resolves its own SSR ray
    // through the shared off-screen cascade when possible. A confidence miss falls through here.
    vec3 fallback = vec3(0.0); // Explicit material fallback: no invented environment radiance.
    if (hasSky) {
        float mipCount = max(u_ReflectionMeta.x, 1.0);
        float skyLod = roughness * max(mipCount - 1.0, 0.0);
        fallback = max(textureLod(u_SkySpecular, envUv, skyLod).rgb, vec3(0.0));
    }
    if (!hasWaterReflection) return fallback;

    vec3 traced = max(textureLod(u_WaterReflectionColor, screenUv, 0.0).rgb, vec3(0.0));
    float confidence = clamp(textureLod(u_WaterReflectionConfidence, screenUv, 0.0).r, 0.0, 1.0);
    // Confidence selects between semantically valid hierarchy levels; zero never means black hit.
    return mix(fallback, traced, confidence);
}

void main() {
    vec4 texel = texture(u_BlockAtlas, te_Uv);
    if ((te_MapMask & (1u << 7u)) != 0u) {
        vec4 overrideAlbedo = texture(u_AlbedoAtlas, te_Uv);
        texel.a *= overrideAlbedo.a;
    }
    if (texel.a * te_Color.a <= 1.0e-4) discard;

    vec3 normal = resolveNormal(te_ViewNormal);
    vec3 viewDir = normalize(-te_ViewPosition);
    float roughness = unpack8(te_Surface, 0u);
    if ((te_MapMask & (1u << 2u)) != 0u) roughness = texture(u_SurfaceAtlas, te_Uv).g;
    float f0 = unpack8(te_Surface, 16u);
    if ((te_MapMask & (1u << 4u)) != 0u) f0 = texture(u_SpecularAtlas, te_Uv).r;
    f0 = clamp(f0, 1.0e-4, 0.98);

    // Water is dielectric/transmissive. Metallic payload, even if present in a generic material map,
    // has no meaning in this domain and is intentionally ignored.
    float ndv = clamp(abs(dot(normal, viewDir)), 0.0, 1.0);
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - ndv, 5.0);
    float ior = iorFromF0(f0);
    bool cameraInsideWater = u_MediumReflection.y > 0.5;

    vec2 screenUv = gl_FragCoord.xy * u_Viewport.zw;
    vec3 incident = normalize(te_ViewPosition);
    vec3 boundaryNormal = cameraInsideWater ? -normal : normal;
    float eta = cameraInsideWater ? ior : 1.0 / ior;
    vec3 refracted = refract(incident, boundaryNormal, eta);
    bool totalInternalReflection = dot(refracted, refracted) <= 1.0e-8;

    float fallbackThickness = max(te_Optical.y, 0.0);
    float thickness = fallbackThickness;
    float thicknessConfidence = 0.0;
    int thicknessStatus = cameraInsideWater ? THICKNESS_CAMERA_INSIDE : THICKNESS_MISSING_BACK_SURFACE;

    vec2 refractedUv = screenUv;
    bool refractedUvValid = false;
    if (!totalInternalReflection) {
        float probeDistance = max(u_OpticalAbsorption.w, 0.01);
        if (fallbackThickness > 0.0) probeDistance = min(probeDistance, fallbackThickness);
        refractedUvValid = projectUv(te_ViewPosition + normalize(refracted) * probeDistance, refractedUv);
        if (!refractedUvValid) thicknessStatus = THICKNESS_UNKNOWN_OFFSCREEN;
    }

    if (!cameraInsideWater && refractedUvValid) {
        float opaqueDepth = textureLod(u_ResolvedDepth, refractedUv, 0.0).r;
        if (opaqueDepth > 0.0) {
            vec3 opaqueView = reconstructView(refractedUv, opaqueDepth);
            float surfaceDistance = length(te_ViewPosition);
            float opaqueDistance = length(opaqueView);
            if (opaqueDistance > surfaceDistance + 1.0e-3) {
                thickness = max(opaqueDistance - surfaceDistance, 0.0);
                thicknessConfidence = 1.0;
                thicknessStatus = THICKNESS_VALID_SCREEN_SPACE;
            }
        } else {
            thicknessStatus = THICKNESS_MISSING_BACK_SURFACE;
        }
    }

    // Invalid/off-screen refraction never samples undefined texels or returns black: it uses the
    // undistorted current opaque scene. Fallback thickness remains explicitly confidence=0.
    vec2 backgroundUv = refractedUvValid && !totalInternalReflection ? refractedUv : screenUv;
    vec3 background = max(textureLod(u_SceneRadiance, backgroundUv, 0.0).rgb, vec3(0.0));

    vec3 absorption = max(u_OpticalAbsorption.rgb, vec3(0.0));
    vec3 scattering = max(u_OpticalScattering.rgb, vec3(0.0));
    // Screen-space thickness is exact only when confidence is 1. Unknown/missing cases retain the
    // material fallback explicitly as a fallback rather than masquerading it as measured geometry.
    float resolvedThickness = mix(fallbackThickness, thickness, clamp(thicknessConfidence, 0.0, 1.0));
    float opticalDistance = cameraInsideWater ? 0.0 : max(resolvedThickness, 0.0);
    vec3 transmittanceBeer = exp(-absorption * opticalDistance);
    vec3 transmittedRadiance = background * transmittanceBeer
                             + scattering * (vec3(1.0) - transmittanceBeer);

    vec3 reflectedRadiance = reflectionHierarchy(screenUv, normal, viewDir, roughness);
    float materialTransmission = clamp(te_Optical.x, 0.0, 1.0);
    float transmissionWeight = totalInternalReflection ? 0.0 : materialTransmission * (1.0 - fresnel);
    vec3 color = reflectedRadiance * fresnel + transmittedRadiance * transmissionWeight;

    // The shader has already composited transmission against SCENE_RADIANCE, therefore the target
    // is overwritten (alpha=1) instead of blending the background a second time.
    outColor = vec4(max(color, vec3(0.0)), 1.0);

    vec2 velocity = vec2(0.0);
    if (u_ReflectionMeta.w > 0.5 && abs(te_CurrentClip.w) > 1.0e-7 && abs(te_PreviousClip.w) > 1.0e-7) {
        vec2 currentUv = te_CurrentClip.xy / te_CurrentClip.w * 0.5 + 0.5;
        vec2 previousUv = te_PreviousClip.xy / te_PreviousClip.w * 0.5 + 0.5;
        velocity = currentUv - previousUv;
    }
    outVelocity = vec4(velocity, 0.0, 1.0);
    outMotionValidity = vec4(u_ReflectionMeta.w > 0.5 ? 1.0 : 0.0, 0.0, 0.0, 1.0);
    outTemporalCoverage = vec4(1.0, 0.0, 0.0, 1.0);
    // Water is an explicit responsive material domain. Final TAA policy decides the actual history
    // weight later; this attachment only communicates that ordinary accumulation is unsafe here.
    outReactiveMask = vec4(1.0, 0.0, 0.0, 1.0);

}
