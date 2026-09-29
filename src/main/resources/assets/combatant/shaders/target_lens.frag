#version 330 core

uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

layout (std140) uniform TargetLens {
    vec4 u_Params;
    vec4 u_Lenses[18];
};

in vec2 v_TexCoord;
out vec4 color;

void main() {
    vec2 uv = v_TexCoord;
    float sceneDepth = texture(u_Depth, uv).r;
    int count = int(u_Params.x + 0.5);
    float aspect = u_Params.y;
    float strength = u_Params.z;
    bool depthAvailable = u_Params.w > 0.5;
    vec2 offset = vec2(0.0);

    for (int i = 0; i < 18; i++) {
        if (i >= count) break;
        vec4 lens = u_Lenses[i];
        float radius = abs(lens.w);
        if (radius <= 0.0) continue;

        vec2 d = (uv - lens.xy) * vec2(aspect, 1.0);
        float len = length(d);
        float t = len / radius;
        if (t >= 1.0 || len < 1.0e-5) continue;

        float visibility = 1.0;
        if (lens.w > 0.0 && depthAvailable) {
            float depthDelta = sceneDepth - lens.z;
            visibility = 1.0 - smoothstep(-0.0005, 0.0025, depthDelta);
            if (visibility <= 0.001) continue;
        }

        float profile = t * (1.0 - t) * (1.0 - t) * 6.75 * visibility;
        vec2 dir = d / len;
        offset -= (dir / vec2(aspect, 1.0)) * (profile * radius * 0.85 * strength);
    }

    if (dot(offset, offset) < 1.0e-12) {
        color = vec4(texture(u_Texture, uv).rgb, 1.0);
        return;
    }

    float offsetLength = length(offset);
    if (offsetLength > 0.04) offset *= 0.04 / offsetLength;

    vec2 uvR = clamp(uv + offset * 0.65, vec2(0.0), vec2(1.0));
    vec2 uvG = clamp(uv + offset, vec2(0.0), vec2(1.0));
    vec2 uvB = clamp(uv + offset * 1.35, vec2(0.0), vec2(1.0));
    color = vec4(texture(u_Texture, uvR).r, texture(u_Texture, uvG).g, texture(u_Texture, uvB).b, 1.0);
}
