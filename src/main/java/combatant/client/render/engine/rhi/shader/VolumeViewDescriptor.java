/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import java.util.EnumSet;
import java.util.Set;

/** Subresource view contract for a native three-dimensional image. */
public record VolumeViewDescriptor(int baseMipLevel,
                                   int mipLevels,
                                   Set<Usage> usages) {
    public enum Usage { SAMPLED, STORAGE }

    public VolumeViewDescriptor {
        if (baseMipLevel < 0) throw new IllegalArgumentException("baseMipLevel");
        if (mipLevels < 1) throw new IllegalArgumentException("mipLevels");
        usages = usages == null || usages.isEmpty() ? Set.of() : Set.copyOf(usages);
        if (usages.isEmpty()) throw new IllegalArgumentException("Volume view requires at least one usage");
        if (usages.contains(Usage.STORAGE) && mipLevels != 1) {
            throw new IllegalArgumentException("Storage volume views must select exactly one mip level");
        }
    }

    public static VolumeViewDescriptor sampled(int baseMipLevel, int mipLevels) {
        return new VolumeViewDescriptor(baseMipLevel, mipLevels, EnumSet.of(Usage.SAMPLED));
    }

    public static VolumeViewDescriptor sampledMip(int mipLevel) {
        return sampled(mipLevel, 1);
    }

    public static VolumeViewDescriptor storage(int mipLevel) {
        return new VolumeViewDescriptor(mipLevel, 1, EnumSet.of(Usage.STORAGE));
    }

    public static VolumeViewDescriptor sampledStorage(int mipLevel) {
        return new VolumeViewDescriptor(mipLevel, 1, EnumSet.of(Usage.SAMPLED, Usage.STORAGE));
    }

    public boolean sampled() {
        return usages.contains(Usage.SAMPLED);
    }

    public boolean storage() {
        return usages.contains(Usage.STORAGE);
    }
}
