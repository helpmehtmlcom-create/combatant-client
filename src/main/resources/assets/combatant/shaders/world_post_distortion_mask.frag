#version 330 core

/*
 * Texture-shaped world mask for local post refraction.
 * R/G encode signed screen flow, B local strength, A coverage.
 */

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;
out vec4 color;

void main() {
    vec4 tex = texture(u_Texture, v_TexCoord);
    // Treat the texture itself as the mask. Alpha gates the source, while RGB/luminance
    // carves detail even for assets stored with an opaque alpha channel.
    float luminance = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float shape = tex.a * smoothstep(0.015, 0.92, max(luminance, max(tex.r, max(tex.g, tex.b))));
    float coverage = shape * v_Color.a;
    if (coverage <= 0.003) discard;

    vec2 p = v_TexCoord * 2.0 - 1.0;
    float len = max(length(p), 1.0e-4);
    vec2 radial = p / len;
    vec2 tangent = vec2(-radial.y, radial.x);

    float swirl = v_Color.g * 2.0 - 1.0;
    float textureFlow = (tex.r - tex.b) * 1.35 + (tex.g - 0.5) * 0.55;
    vec2 flow = radial + tangent * (swirl * 1.15 + textureFlow);
    float flowLen = length(flow);
    if (flowLen > 1.0e-5) flow /= flowLen;

    // Premultiplied output keeps accumulated coverage linear instead of squaring alpha under
    // straight-alpha blending. Clear RG is neutral 0.5, so partial coverage also blends flow
    // naturally toward the no-displacement vector.
    vec3 encoded = vec3(flow * 0.5 + 0.5, v_Color.r);
    color = vec4(encoded * coverage, coverage);
}
