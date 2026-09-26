/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.gltf.model;

public enum PrimitiveMode {
    POINTS(0), LINES(1), LINE_LOOP(2), LINE_STRIP(3), TRIANGLES(4),
    TRIANGLE_STRIP(5), TRIANGLE_FAN(6);

    private final int gltfCode;

    PrimitiveMode(int gltfCode) {
        this.gltfCode = gltfCode;
    }

    public int gltfCode() {
        return gltfCode;
    }

    public static PrimitiveMode fromGltfCode(int value) {
        for (PrimitiveMode mode : values()) {
            if (mode.gltfCode == value) return mode;
        }
        throw new IllegalArgumentException("Unsupported glTF primitive mode: " + value);
    }
}
