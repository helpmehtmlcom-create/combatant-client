#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 color;

uniform sampler2D u_GbufferSurface;
uniform sampler2D u_GbufferGeometry;
uniform sampler2D u_GbufferAuxiliary;
uniform sampler2D u_GbufferMaterial;
uniform sampler2D u_LightTex;
uniform sampler2D u_ResolvedDepth;
uniform sampler2D u_ShadowVisibility;
uniform sampler2D u_AmbientVisibility;

layout(std140) uniform DeferredLighting {
    mat4 u_InverseProjection;
    vec4 u_FogColor;
    vec4 u_FogRanges;
    // xyz: view-space direction from receiver toward the directional light, w: valid.
    vec4 u_DirectionalDirection;
    // rgb: neutral producer-provided radiance.
    vec4 u_DirectionalRadiance;
    // xy: depth -> NDC, z: shadow valid, w: ambient visibility valid.
    vec4 u_DepthAndFlags;
};

const float PI = 3.14159265358979323846;

float combatant_decode_distance(float encoded) {
    return exp2(encoded * 17.0 - 1.0) - 1.0;
}

float combatant_linear_fog(float distanceValue, float start, float end) {
    if (distanceValue <= start) return 0.0;
    if (distanceValue >= end) return 1.0;
    return (distanceValue - start) / max(end - start, 0.0001);
}

vec3 combatant_decode_octahedral(vec2 encoded) {
    vec2 f = encoded * 2.0 - 1.0;
    vec3 n = vec3(f, 1.0 - abs(f.x) - abs(f.y));
    if (n.z < 0.0) {
        n.xy = (1.0 - abs(n.yx)) * sign(n.xy);
    }
    return normalize(n);
}

vec3 combatant_reconstruct_view(vec2 uv, float depth) {
    vec2 ndcXY = uv * 2.0 - 1.0;
    float ndcZ = depth * u_DepthAndFlags.x + u_DepthAndFlags.y;
    vec4 h = u_InverseProjection * vec4(ndcXY, ndcZ, 1.0);
    if (abs(h.w) < 1e-7) return vec3(0.0, 0.0, -1.0);
    return h.xyz / h.w;
}

float combatant_ggx_distribution(float ndoth, float roughness) {
    float a = max(roughness * roughness, 1e-4);
    float a2 = a * a;
    float d = ndoth * ndoth * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1e-6);
}

float combatant_smith_ggx_correlated(float ndotv, float ndotl, float roughness) {
    float a = max(roughness * roughness, 1e-4);
    float a2 = a * a;
    float gv = ndotl * sqrt(max(ndotv * ndotv * (1.0 - a2) + a2, 1e-6));
    float gl = ndotv * sqrt(max(ndotl * ndotl * (1.0 - a2) + a2, 1e-6));
    return 0.5 / max(gv + gl, 1e-6);
}

vec3 combatant_fresnel_schlick(float vdoth, vec3 f0) {
    float factor = pow(clamp(1.0 - vdoth, 0.0, 1.0), 5.0);
    return f0 + (vec3(1.0) - f0) * factor;
}

float combatant_burley_diffuse(float ndotv, float ndotl, float ldoth, float roughness) {
    float fd90 = 0.5 + 2.0 * roughness * ldoth * ldoth;
    float lightScatter = 1.0 + (fd90 - 1.0) * pow(1.0 - ndotl, 5.0);
    float viewScatter = 1.0 + (fd90 - 1.0) * pow(1.0 - ndotv, 5.0);
    return lightScatter * viewScatter / PI;
}

void main() {
    vec4 surface = texture(u_GbufferSurface, v_TexCoord);
    if (surface.a <= 0.0) {
        discard;
    }

    vec4 geometry = texture(u_GbufferGeometry, v_TexCoord);
    vec4 auxiliary = texture(u_GbufferAuxiliary, v_TexCoord);
    vec4 material = texture(u_GbufferMaterial, v_TexCoord);

    vec3 albedo = max(surface.rgb, vec3(0.0));
    float materialAo = clamp(surface.a, 0.0, 1.0);
    float roughness = clamp(material.r, 0.045, 1.0);
    float metallic = clamp(material.g, 0.0, 1.0);
    float dielectricF0 = clamp(material.b, 0.0, 1.0);
    float emission = max(material.a, 0.0);

    vec3 normal = combatant_decode_octahedral(geometry.rg);
    float depth = texture(u_ResolvedDepth, v_TexCoord).r;
    vec3 viewPosition = combatant_reconstruct_view(v_TexCoord, depth);
    vec3 viewDirection = normalize(-viewPosition);

    vec3 f0 = mix(vec3(dielectricF0), albedo, metallic);
    vec3 lightmapRadiance = max(texture(u_LightTex, geometry.ba).rgb, vec3(0.0));
    float ambientVisibility = u_DepthAndFlags.w > 0.5
            ? clamp(texture(u_AmbientVisibility, v_TexCoord).r, 0.0, 1.0)
            : 1.0;

    // The Minecraft lightmap is currently the neutral ambient/local-light producer. It remains
    // separate from the directional BRDF so later sky SH and colored block light can replace it.
    vec3 ambientDiffuseWeight = (vec3(1.0) - f0) * (1.0 - metallic);
    vec3 litColor = albedo * ambientDiffuseWeight
            * lightmapRadiance * materialAo * ambientVisibility;

    if (u_DirectionalDirection.w > 0.5) {
        vec3 lightDirection = normalize(u_DirectionalDirection.xyz);
        vec3 halfVector = normalize(viewDirection + lightDirection);
        float ndotv = max(dot(normal, viewDirection), 0.0);
        float ndotl = max(dot(normal, lightDirection), 0.0);
        float ndoth = max(dot(normal, halfVector), 0.0);
        float vdoth = max(dot(viewDirection, halfVector), 0.0);

        if (ndotv > 0.0 && ndotl > 0.0) {
            vec3 fresnel = combatant_fresnel_schlick(vdoth, f0);
            float distribution = combatant_ggx_distribution(ndoth, roughness);
            float visibility = combatant_smith_ggx_correlated(ndotv, ndotl, roughness);
            vec3 specularBrdf = fresnel * distribution * visibility;
            float ldoth = max(dot(lightDirection, halfVector), 0.0);
            float diffuseTerm = combatant_burley_diffuse(ndotv, ndotl, ldoth, roughness);
            vec3 diffuseBrdf = (vec3(1.0) - fresnel) * (1.0 - metallic) * albedo * diffuseTerm;
            float shadowVisibility = u_DepthAndFlags.z > 0.5
                    ? clamp(texture(u_ShadowVisibility, v_TexCoord).r, 0.0, 1.0)
                    : 1.0;
            litColor += (diffuseBrdf + specularBrdf)
                    * max(u_DirectionalRadiance.rgb, vec3(0.0))
                    * ndotl * shadowVisibility;
        }
    }

    litColor += albedo * emission;

    float sphericalDistance = combatant_decode_distance(auxiliary.r);
    float cylindricalDistance = combatant_decode_distance(auxiliary.g);
    float environmentalFog = combatant_linear_fog(
        sphericalDistance, u_FogRanges.x, u_FogRanges.y
    );
    float renderFog = combatant_linear_fog(
        cylindricalDistance, u_FogRanges.z, u_FogRanges.w
    );
    float fogAmount = max(1.0 - auxiliary.b, max(environmentalFog, renderFog));
    color = vec4(mix(litColor, u_FogColor.rgb, fogAmount * u_FogColor.a), 1.0);
}
