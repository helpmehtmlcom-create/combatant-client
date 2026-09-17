/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Typed clear payload shared by OpenGL glClearTexSubImage and Vulkan vkCmdClearColorImage. */
public record VolumeClearValue(Kind kind, int x, int y, int z, int w) {
    public enum Kind { FLOAT, SIGNED_INT, UNSIGNED_INT }

    public VolumeClearValue {
        if (kind == null) throw new IllegalArgumentException("kind");
    }

    public static VolumeClearValue floats(float x, float y, float z, float w) {
        return new VolumeClearValue(Kind.FLOAT,
                Float.floatToRawIntBits(x), Float.floatToRawIntBits(y),
                Float.floatToRawIntBits(z), Float.floatToRawIntBits(w));
    }

    public static VolumeClearValue signedInts(int x, int y, int z, int w) {
        return new VolumeClearValue(Kind.SIGNED_INT, x, y, z, w);
    }

    public static VolumeClearValue unsignedInts(int x, int y, int z, int w) {
        return new VolumeClearValue(Kind.UNSIGNED_INT, x, y, z, w);
    }

    public static VolumeClearValue zeroFloat() {
        return floats(0.0f, 0.0f, 0.0f, 0.0f);
    }

    public float floatX() { return Float.intBitsToFloat(x); }
    public float floatY() { return Float.intBitsToFloat(y); }
    public float floatZ() { return Float.intBitsToFloat(z); }
    public float floatW() { return Float.intBitsToFloat(w); }
}
