/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Native image load/store binding for one mip of a three-dimensional image. */
public record StorageVolumeBinding(int binding,
                                   RhiStorageVolume volume,
                                   StorageAccess access,
                                   int mipLevel) {
    public StorageVolumeBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (volume == null) throw new IllegalArgumentException("volume");
        if (volume.isClosed()) throw new IllegalStateException("Storage volume is closed: " + volume.descriptor().label());
        if (!volume.descriptor().storage()) {
            throw new IllegalArgumentException("Storage volume binding requires storage usage: " + volume.descriptor().label());
        }
        access = access == null ? volume.descriptor().access() : access;
        if (!volume.descriptor().access().allows(access)) {
            throw new IllegalArgumentException("Binding access " + access + " exceeds volume access "
                    + volume.descriptor().access() + ": " + volume.descriptor().label());
        }
        if (mipLevel < 0 || mipLevel >= volume.descriptor().mipLevels()) {
            throw new IllegalArgumentException("Storage volume mip out of range: " + mipLevel
                    + " / " + volume.descriptor().mipLevels());
        }
    }

    public StorageVolumeBinding(int binding, RhiStorageVolume volume, StorageAccess access) {
        this(binding, volume, access, 0);
    }

    public RhiVolumeView view() {
        return volume.storageView(mipLevel);
    }
}
