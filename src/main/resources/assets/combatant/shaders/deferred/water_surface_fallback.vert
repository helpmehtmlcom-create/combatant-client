#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec2 LocalSurface;
layout(location = 3) in vec4 Color;
layout(location = 4) in vec4 Params;
layout(location = 5) in vec4 Tess;
layout(location = 6) in vec4 Params2;
layout(location = 7) in vec4 Optical;
layout(location = 8) in uint MaterialId;
layout(location = 9) in uint FluidTypeId;
layout(location = 10) in uint MaterialMapMask;
layout(location = 11) in uint MaterialFeatureMask;
layout(location = 12) in uint SurfaceFlags;
layout(location = 13) in uint MaterialSurface;

layout(std140) uniform MeshData {
    mat4 u_MeshProjection;
    mat4 u_MeshModelView;
};

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

out vec2 v_Uv;
out vec2 v_LocalSurface;
out vec4 v_Color;
out vec4 v_Params;
out vec4 v_Optical;
out vec3 v_ViewPosition;
out vec3 v_WorldPosition;
out vec3 v_ViewNormal;
out vec4 v_CurrentClip;
out vec4 v_PreviousClip;
flat out uint v_MaterialId;
flat out uint v_FluidTypeId;
flat out uint v_MapMask;
flat out uint v_FeatureMask;
flat out uint v_SurfaceFlags;
flat out uint v_Surface;

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
        height += sin(phase) * amplitude;
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
    vec3 baseWorld = Position + u_CurrentCameraTime.xyz;
    float flowStrength = max(Params2.z, 0.0);
    vec2 currentGradient;
    vec2 previousGradient;
    vec3 currentWorld = baseWorld + deformation(baseWorld, Params.xy, flowStrength, Tess.x,
                                                 u_CurrentCameraTime.w, currentGradient);
    vec3 previousWorld = baseWorld + deformation(baseWorld, Params.xy, flowStrength, Tess.x,
                                                  u_PreviousCameraTime.w, previousGradient);
    vec3 currentRelative = currentWorld - u_CurrentCameraTime.xyz;
    vec3 previousRelative = previousWorld - u_PreviousCameraTime.xyz;
    vec4 currentView = u_CurrentView * vec4(currentRelative, 1.0);
    vec4 previousView = u_PreviousView * vec4(previousRelative, 1.0);
    vec4 currentClip = u_CurrentProjection * currentView;
    vec4 previousClip = u_PreviousProjection * previousView;

    vec3 baseNormal = normalize(vec3(Params2.w, Optical.z, Optical.w));
    if (baseNormal.y < 0.0) baseNormal = -baseNormal;
    vec3 normalWorld = normalize(baseNormal + vec3(-currentGradient.x, 0.0, -currentGradient.y));

    gl_Position = currentClip;
    v_Uv = UV0;
    v_LocalSurface = LocalSurface;
    v_Color = Color;
    v_Params = Params;
    v_Optical = vec4(Optical.xy, 0.0, 0.0);
    v_ViewPosition = currentView.xyz;
    v_WorldPosition = currentWorld;
    v_ViewNormal = normalize(mat3(u_CurrentView) * normalWorld);
    v_CurrentClip = currentClip;
    v_PreviousClip = previousClip;
    v_MaterialId = MaterialId;
    v_FluidTypeId = FluidTypeId;
    v_MapMask = MaterialMapMask;
    v_FeatureMask = MaterialFeatureMask;
    v_SurfaceFlags = SurfaceFlags;
    v_Surface = MaterialSurface;
}
