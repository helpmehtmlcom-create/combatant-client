#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 color;

uniform sampler2D u_Source;
uniform sampler2D u_GbufferSurface;

void main() {
    if (texture(u_GbufferSurface, v_TexCoord).a <= 0.0) discard;
    color = texture(u_Source, v_TexCoord);
}
