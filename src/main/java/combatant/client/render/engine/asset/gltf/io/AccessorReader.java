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
package combatant.client.render.engine.asset.gltf.io;

import de.javagl.jgltf.model.*;

final class AccessorReader {
    private AccessorReader() {
    }

    static float[] floats(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        int size = data.getTotalNumComponents();
        float[] result = new float[size];
        if (data instanceof AccessorFloatData values) {
            for (int i = 0; i < size; i++) result[i] = values.get(i);
            return result;
        }
        if (data instanceof AccessorByteData values) {
            for (int i = 0; i < size; i++) {
                int value = values.getInt(i);
                result[i] = accessor.isNormalized() ? normalize(value, values.isUnsigned(), 8) : value;
            }
            return result;
        }
        if (data instanceof AccessorShortData values) {
            for (int i = 0; i < size; i++) {
                int value = values.getInt(i);
                result[i] = accessor.isNormalized() ? normalize(value, values.isUnsigned(), 16) : value;
            }
            return result;
        }
        if (data instanceof AccessorIntData values) {
            for (int i = 0; i < size; i++) {
                long value = values.getLong(i);
                result[i] = accessor.isNormalized()
                    ? normalizeLong(value, values.isUnsigned()) : (float) value;
            }
            return result;
        }
        throw new IllegalArgumentException("Unknown accessor data type " + data.getClass().getName());
    }

    static int[] integers(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        int size = data.getTotalNumComponents();
        int[] result = new int[size];
        if (data instanceof AccessorByteData values) {
            for (int i = 0; i < size; i++) result[i] = values.getInt(i);
        } else if (data instanceof AccessorShortData values) {
            for (int i = 0; i < size; i++) result[i] = values.getInt(i);
        } else if (data instanceof AccessorIntData values) {
            for (int i = 0; i < size; i++) result[i] = Math.toIntExact(values.getLong(i));
        } else {
            throw new IllegalArgumentException("Integer accessor required, got " + data.getClass().getSimpleName());
        }
        return result;
    }

    private static float normalize(int value, boolean unsigned, int bits) {
        if (unsigned) return value / (float) ((1L << bits) - 1L);
        int max = (1 << (bits - 1)) - 1;
        return Math.max(-1.0f, value / (float) max);
    }

    private static float normalizeLong(long value, boolean unsigned) {
        if (unsigned) return (float) (value / 4294967295.0);
        return Math.max(-1.0f, (float) (value / 2147483647.0));
    }
}
