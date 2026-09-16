#version 450 core

layout(quads, fractional_even_spacing, ccw) in;

layout(location = 0) in vec2 tc_Uv[];
layout(location = 1) in vec2 tc_LocalSurface[];
layout(location = 2) in vec4 tc_Color[];
layout(location = 3) in vec4 tc_Params[];
layout(location = 4) in vec4 tc_Tess[];
layout(location = 5) in vec4 tc_Params2[];
layout(location = 6) in vec4 tc_Optical[];
layout(location = 7) flat in uint tc_MaterialId[];
layout(location = 8) flat in uint tc_FluidTypeId[];
layout(location = 9) flat in uint tc_MapMask[];
layout(location = 10) flat in uint tc_FeatureMask[];
layout(location = 11) flat in uint tc_SurfaceFlags[];
layout(location = 12) flat in uint tc_Surface[];

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
    vec4 u_OpticalAbsorption;
    vec4 u_OpticalScattering;
    vec4 u_ReflectionMeta;
};

layout(location = 0) out vec2 te_Uv;
layout(location = 1) out vec2 te_LocalSurface;
layout(location = 2) out vec4 te_Color;
layout(location = 3) out vec4 te_Params;
layout(location = 4) out vec4 te_Optical;
layout(location = 5) out vec3 te_ViewPosition;
layout(location = 6) out vec3 te_WorldPosition;
layout(location = 7) out vec3 te_ViewNormal;
layout(location = 8) out vec4 te_CurrentClip;
layout(location = 9) out vec4 te_PreviousClip;
layout(location = 10) flat out uint te_MaterialId;
layout(location = 11) flat out uint te_FluidTypeId;
layout(location = 12) flat out uint te_MapMask;
layout(location = 13) flat out uint te_FeatureMask;
layout(location = 14) flat out uint te_SurfaceFlags;
layout(location = 15) flat out uint te_Surface;

vec3 bilerp3(vec3 a, vec3 b, vec3 c, vec3 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}
vec2 bilerp2(vec2 a, vec2 b, vec2 c, vec2 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}
vec4 bilerp4(vec4 a, vec4 b, vec4 c, vec4 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}

vec3 baseNormal(vec2 uv) {
    vec3 p0 = gl_in[0].gl_Position.xyz;
    vec3 p1 = gl_in[1].gl_Position.xyz;
    vec3 p2 = gl_in[2].gl_Position.xyz;
    vec3 p3 = gl_in[3].gl_Position.xyz;
    vec3 du = mix(p1 - p0, p2 - p3, uv.y);
    vec3 dv = mix(p3 - p0, p2 - p1, uv.x);
    vec3 crossNormal = cross(dv, du);
    vec3 explicitNormal = normalize(vec3(tc_Params2[0].w, tc_Optical[0].z, tc_Optical[0].w));
    if (dot(crossNormal, crossNormal) <= 1.0e-10) return explicitNormal;
    vec3 n = normalize(crossNormal);
    return n.y < 0.0 ? -n : n;
}

vec3 deformation(vec3 world, vec2 flow, float flowStrength, float scale, float time, out vec2 gradient) {
    gradient = vec2(0.0);
    float amplitude = max(scale, 0.0) * max(u_Deformation0.x, 0.0);
    float spatial = max(u_Deformation0.y, 1.0e-4);
    float temporal = u_Deformation0.z;
    float flowCoupling = max(u_Deformation0.w, 0.0);
    float height = 0.0;

    if (flowStrength > 1.0e-5 && amplitude > 0.0) {
        vec2 direction = flow / flowStrength;
        float phase = dot(world.xz, direction) * spatial
                    - time * temporal * (1.0 + flowStrength * flowCoupling);
        float response = sin(phase) * amplitude;
        height += response;
        gradient += direction * cos(phase) * amplitude * spatial;
    }

    vec2 wind = u_Deformation1.xy;
    float windLength = length(wind);
    float windCoupling = max(u_Deformation1.z, 0.0);
    if (windLength > 1.0e-5 && windCoupling > 0.0 && amplitude > 0.0) {
        vec2 direction = wind / windLength;
        float phase = dot(world.xz, direction) * spatial - time * temporal * windLength;
        float windAmplitude = amplitude * windCoupling;
        height += sin(phase) * windAmplitude;
        gradient += direction * cos(phase) * windAmplitude * spatial;
    }

    // Rain is a separate input term. Foundation profile keeps it at zero until a weather profile owns it.
    float rain = max(u_OpticalScattering.w, 0.0) * max(u_Deformation1.w, 0.0);
    if (rain > 0.0 && amplitude > 0.0) {
        float phase = (world.x + world.z) * spatial * 1.7 - time * temporal * 2.0;
        float rainAmplitude = amplitude * rain;
        height += sin(phase) * rainAmplitude;
        gradient += vec2(1.0) * cos(phase) * rainAmplitude * spatial * 1.7;
    }
    return vec3(0.0, height, 0.0);
}

void main() {
    vec2 patchUv = gl_TessCoord.xy;
    vec3 baseRelative = bilerp3(gl_in[0].gl_Position.xyz, gl_in[1].gl_Position.xyz,
                                gl_in[2].gl_Position.xyz, gl_in[3].gl_Position.xyz, patchUv);
    vec3 baseWorld = baseRelative + u_CurrentCameraTime.xyz;
    vec2 uv = bilerp2(tc_Uv[0], tc_Uv[1], tc_Uv[2], tc_Uv[3], patchUv);
    vec2 localSurface = bilerp2(tc_LocalSurface[0], tc_LocalSurface[1], tc_LocalSurface[2], tc_LocalSurface[3], patchUv);
    vec4 params = bilerp4(tc_Params[0], tc_Params[1], tc_Params[2], tc_Params[3], patchUv);
    vec4 color = bilerp4(tc_Color[0], tc_Color[1], tc_Color[2], tc_Color[3], patchUv);
    vec4 optical = bilerp4(tc_Optical[0], tc_Optical[1], tc_Optical[2], tc_Optical[3], patchUv);
    float flowStrength = max(tc_Params2[0].z, 0.0);

    vec2 currentGradient;
    vec2 previousGradient;
    vec3 currentWorld = baseWorld + deformation(baseWorld, params.xy, flowStrength, tc_Tess[0].x,
                                                 u_CurrentCameraTime.w, currentGradient);
    vec3 previousWorld = baseWorld + deformation(baseWorld, params.xy, flowStrength, tc_Tess[0].x,
                                                  u_PreviousCameraTime.w, previousGradient);

    vec3 currentRelative = currentWorld - u_CurrentCameraTime.xyz;
    vec3 previousRelative = previousWorld - u_PreviousCameraTime.xyz;
    vec4 currentView = u_CurrentView * vec4(currentRelative, 1.0);
    vec4 previousView = u_PreviousView * vec4(previousRelative, 1.0);
    vec4 currentClip = u_CurrentProjection * currentView;
    vec4 previousClip = u_PreviousProjection * previousView;

    vec3 normalWorld = baseNormal(patchUv);
    normalWorld = normalize(normalWorld + vec3(-currentGradient.x, 0.0, -currentGradient.y));

    gl_Position = currentClip;
    te_Uv = uv;
    te_LocalSurface = localSurface;
    te_Color = color;
    te_Params = params;
    te_Optical = optical;
    te_ViewPosition = currentView.xyz;
    te_WorldPosition = currentWorld;
    te_ViewNormal = normalize(mat3(u_CurrentView) * normalWorld);
    te_CurrentClip = currentClip;
    te_PreviousClip = previousClip;
    te_MaterialId = tc_MaterialId[0];
    te_FluidTypeId = tc_FluidTypeId[0];
    te_MapMask = tc_MapMask[0];
    te_FeatureMask = tc_FeatureMask[0];
    te_SurfaceFlags = tc_SurfaceFlags[0];
    te_Surface = tc_Surface[0];
}
