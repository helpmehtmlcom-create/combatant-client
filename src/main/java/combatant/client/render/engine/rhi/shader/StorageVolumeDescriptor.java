/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.GpuFormat;

/** Native three-dimensional image used by compute/volumetric workloads. */
public record StorageVolumeDescriptor(String label,
                                      int width,
                                      int height,
                                      int depth,
                                      GpuFormat format,
                                      StorageAccess access) {
    public StorageVolumeDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-volume" : label;
        width = Math.max(1, width);
        height = Math.max(1, height);
        depth = Math.max(1, depth);
        if (format == null || !format.hasColorAspect() || format.componentCount() == 3) {
            throw new IllegalArgumentException("Storage volume requires a supported color format: " + format);
        }
        access = access == null ? StorageAccess.READ_WRITE : access;
    }
}
