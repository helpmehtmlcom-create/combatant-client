#version 450 core

layout(binding = 0) uniform sampler2D u_BlockAtlas;
layout(binding = 1) uniform sampler2D u_AlbedoAtlas;
layout(binding = 2) uniform sampler2D u_NormalHeightAtlas;
layout(binding = 3) uniform sampler2D u_SurfaceAtlas;
layout(binding = 4) uniform sampler2D u_SpecularAtlas;
layout(binding = 5) uniform sampler2D u_ReflectionColor;
layout(binding = 6) uniform sampler2D u_SceneRadiance;
layout(std430, binding = 7) readonly buffer PatchCamera {
    mat4 u_ViewRotation;
    mat4 u_Projection;
    vec4 u_CameraTime;
    vec4 u_Viewport;
};

layout(location = 0) in vec2 te_Uv;
layout(location = 1) in vec4 te_Color;
layout(location = 2) in vec4 te_Params;
layout(location = 3) in vec3 te_ViewPosition;
layout(location = 4) in vec3 te_WorldPosition;
layout(location = 5) flat in uint te_MaterialId;
layout(location = 6) flat in uint te_MapMask;
layout(location = 7) flat in uint te_Surface;

layout(location = 0) out vec4 outColor;

float unpack8(uint packed, uint shift) {
    return float((packed >> shift) & 255u) / 255.0;
}

void main() {
    vec4 texel = texture(u_BlockAtlas, te_Uv);
    if ((te_MapMask & (1u << 7u)) != 0u) {
        vec4 overrideAlbedo = texture(u_AlbedoAtlas, te_Uv);
        texel.rgb = overrideAlbedo.rgb;
        texel.a *= overrideAlbedo.a;
    }

    vec3 geometricNormal = normalize(cross(dFdx(te_ViewPosition), dFdy(te_ViewPosition)));
    if (!gl_FrontFacing) geometricNormal = -geometricNormal;
    vec3 normal = geometricNormal;
    if ((te_MapMask & 1u) != 0u) {
        vec3 tangentNormal = normalize(texture(u_NormalHeightAtlas, te_Uv).rgb * 2.0 - 1.0);
        vec3 dpdx = dFdx(te_ViewPosition);
        vec3 dpdy = dFdy(te_ViewPosition);
        vec2 duvdx = dFdx(te_Uv);
        vec2 duvdy = dFdy(te_Uv);
        float det = duvdx.x * duvdy.y - duvdx.y * duvdy.x;
        if (abs(det) > 1.0e-7) {
            vec3 tangent = normalize((dpdx * duvdy.y - dpdy * duvdx.y) / det);
            vec3 bitangent = normalize(cross(geometricNormal, tangent));
            normal = normalize(mat3(tangent, bitangent, geometricNormal) * tangentNormal);
        }
    }

    float roughness = unpack8(te_Surface, 0u);
    if ((te_MapMask & (1u << 2u)) != 0u) roughness = texture(u_SurfaceAtlas, te_Uv).g;
    float f0 = unpack8(te_Surface, 16u);
    if ((te_MapMask & (1u << 4u)) != 0u) f0 = texture(u_SpecularAtlas, te_Uv).r;

    vec3 viewDir = normalize(-te_ViewPosition);
    float ndv = clamp(dot(normal, viewDir), 0.0, 1.0);
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - ndv, 5.0);
    vec2 screenUv = gl_FragCoord.xy * u_Viewport.zw;
    vec3 reflection = texture(u_ReflectionColor, screenUv).rgb;
    vec3 opaque = texture(u_SceneRadiance, screenUv).rgb;
    vec3 tint = texel.rgb * te_Color.rgb;

    float reflectionWeight = clamp(fresnel * mix(1.0, 0.42, roughness), 0.08, 0.92);
    vec3 transmission = mix(opaque, tint, 0.16);
    vec3 color = mix(transmission, reflection, reflectionWeight);
    color *= mix(0.82, 1.0, clamp(te_Params.z, 0.0, 1.0));

    float alpha = clamp(texel.a * te_Color.a * (0.34 + fresnel * 0.42), 0.12, 0.78);
    outColor = vec4(color, alpha);
}
