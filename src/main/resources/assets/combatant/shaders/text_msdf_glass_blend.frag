#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;      // MSDF glyph atlas
uniform sampler2D u_SceneTexture; // aptured scene
uniform sampler2D u_BlurTexture;  // prepared backdrop blur

layout (std140) uniform MsdfText {
    vec4 u_Msdf; // x = pxRange, y = atlasWidth, z = atlasHeight
};

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

layout (std140) uniform UIBlend {
    vec4 uBlendParams; // x = mode, y = strength, z = pivot, w = softness
    vec4 uBlendTone0;
    vec4 uBlendTone1;
};

#moj_import <combatant:ui_backdrop_blend.glsl>

in vec2 v_TexCoord;
in vec4 v_Color;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec2 safeNormalize(vec2 v) {
    float len2 = dot(v, v);
    return len2 > 1e-7 ? v * inversesqrt(len2) : vec2(0.0, 1.0);
}

void main() {
    vec3 atlas = texture(u_Texture, v_TexCoord).rgb;
    float sd = median(atlas.r, atlas.g, atlas.b);

    vec2 atlasSize = max(u_Msdf.yz, vec2(1.0));
    vec2 unitRange = vec2(max(u_Msdf.x, 0.5)) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / max(fwidth(v_TexCoord), vec2(1e-6));
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    // Positive inside the glyph, measured in approximately screen pixels.
    float signedPx = screenPxRange * (sd - 0.5);
    float glyphAlpha = smoothstep(-0.62, 0.54, signedPx);
    if (glyphAlpha * v_Color.a <= 0.001) discard;

    // The SDF itself is the glass boundary. Derivatives give a stable screen-space edge normal,
    // so refraction/rim stay attached to the actual glyph instead of a surrounding rectangle.
    vec2 sdfGradient = vec2(dFdx(signedPx), dFdy(signedPx));
    vec2 normal = safeNormalize(sdfGradient);
    float edgeDistance = abs(signedPx);
    float rim = 1.0 - smoothstep(0.18, 2.20, edgeDistance);
    float hairline = 1.0 - smoothstep(0.00, 0.72, edgeDistance);
    float body = smoothstep(0.25, 3.25, signedPx);

    vec2 fbSize = max(uScreen.xy, vec2(1.0));
    vec2 uv = clamp(gl_FragCoord.xy / fbSize, vec2(0.001), vec2(0.999));

    // Keep the body optically calm; most displacement belongs to the rim, as with a thin glass
    // surface. This avoids fuzzy letter interiors while retaining visible refraction at the edge.
    float refractPx = 0.34 + rim * 1.84 + hairline * 0.60;
    vec2 refractedUv = clamp(uv + normal * (refractPx / fbSize), vec2(0.001), vec2(0.999));

    vec3 cleanScene = texture(u_SceneTexture, refractedUv).rgb;
    vec3 blurredScene = texture(u_BlurTexture, refractedUv).rgb;
    float clarity = 0.05 + body * 0.05;
    vec3 glass = mix(blurredScene, cleanScene, clarity);

    // Keep the optical body neutral. Blend modes must operate on the actual refracted/blurred
    // material, not on a white-painted source, otherwise NEGATIVE loses the glass information.
    float glassLuma = max(luminance(glass), 1e-4);
    glass *= mix(1.0, 0.72 / glassLuma, smoothstep(0.72, 1.0, glassLuma) * 0.08);
    vec3 tint = clamp(v_Color.rgb, 0.0, 1.0);
    glass = mix(glass, tint, 0.025 + rim * 0.025);
    glass = mix(glass, blurredScene, 0.10 + (1.0 - body) * 0.14);
    glass = clamp(glass, 0.0, 1.0);

    vec3 backdrop = texture(u_SceneTexture, uv).rgb;
    vec3 blended = combatantResolveBackdropBlend(
            backdrop,
            glass,
            tint,
            uBlendParams,
            uBlendTone0,
            uBlendTone1
    );

    // Glass cues are layered after the color operator. This avoids the gray c <-> (1-c) mix while
    // keeping a bright SDF rim/specular and a visible refractive edge on top of the negative body.
    float directional = clamp(dot(normal, safeNormalize(vec2(-0.42, 0.91))) * 0.5 + 0.5, 0.0, 1.0);
    float rimLight = rim * (0.17 + 0.30 * directional);
    float specular = hairline * (0.12 + 0.24 * directional);
    vec3 rimColor = mix(vec3(0.78), vec3(1.0), directional);
    blended += rimColor * rimLight;
    blended += vec3(1.0) * specular;

    // A tiny edge-only chromatic split makes refraction legible without tinting the glyph body.
    vec2 chromaOffset = normal * ((0.42 + rim * 0.66) / fbSize);
    float chromaMask = rim * 0.16;
    if (chromaMask > 0.001) {
        float r = 1.0 - texture(u_SceneTexture, clamp(refractedUv + chromaOffset, vec2(0.001), vec2(0.999))).r;
        float b = 1.0 - texture(u_SceneTexture, clamp(refractedUv - chromaOffset, vec2(0.001), vec2(0.999))).b;
        vec3 chroma = vec3(r, blended.g, b);
        blended = mix(blended, chroma, chromaMask);
    }

    float denseGlyphAlpha = 1.0 - pow(max(1.0 - glyphAlpha, 0.0), 1.18);
    float coverage = clamp(denseGlyphAlpha * v_Color.a * (0.84 + rim * 0.12 + hairline * 0.04), 0.0, 0.94);
    color = vec4(clamp(blended, 0.0, 1.0), coverage);
}
