/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Native image load/store binding for a three-dimensional image. */
public record StorageVolumeBinding(int binding,
                                   RhiStorageVolume volume,
                                   StorageAccess access) {
    public StorageVolumeBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (volume == null) throw new IllegalArgumentException("volume");
        access = access == null ? volume.descriptor().access() : access;
    }
}
