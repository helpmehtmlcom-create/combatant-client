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

layout(std140) uniform DeferredLighting {
    vec4 u_FogColor;
    vec4 u_FogRanges;
};

float combatant_decode_distance(float encoded) {
    return exp2(encoded * 17.0 - 1.0) - 1.0;
}

float combatant_linear_fog(float distanceValue, float start, float end) {
    if (distanceValue <= start) return 0.0;
    if (distanceValue >= end) return 1.0;
    return (distanceValue - start) / max(end - start, 0.0001);
}

void main() {
    vec4 surface = texture(u_GbufferSurface, v_TexCoord);
    if (surface.a <= 0.0) {
        discard;
    }

    vec4 geometry = texture(u_GbufferGeometry, v_TexCoord);
    vec4 auxiliary = texture(u_GbufferAuxiliary, v_TexCoord);
    vec4 material = texture(u_GbufferMaterial, v_TexCoord);

    vec3 lightmap = texture(u_LightTex, geometry.ba).rgb;
    float ao = clamp(surface.a, 0.0, 1.0);
    float emission = max(material.a, 0.0);
    vec3 litColor = surface.rgb * lightmap * ao;
    litColor += surface.rgb * emission;

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
