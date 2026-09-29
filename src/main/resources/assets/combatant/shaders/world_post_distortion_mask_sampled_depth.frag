#version 330 core

/* Iris depth copies are sampled textures, not Blaze3D render attachments. */
uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

in vec2 v_TexCoord;
in vec4 v_Color;
out vec4 color;

void main() {
    vec2 depthSize = vec2(textureSize(u_Depth, 0));
    vec2 depthUv = clamp(gl_FragCoord.xy / depthSize, vec2(0.0), vec2(1.0));
    float sceneDepth = texture(u_Depth, depthUv).r;
    // Minecraft 26.2 uses reversed depth: larger window-depth values are closer.
    if (gl_FragCoord.z + 0.00002 < sceneDepth) discard;

    vec4 tex = texture(u_Texture, v_TexCoord);
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

    vec3 encoded = vec3(flow * 0.5 + 0.5, v_Color.r);
    color = vec4(encoded * coverage, coverage);
}
