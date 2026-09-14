/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Native image load/store binding. */
public record StorageImageBinding(int binding,
                                  RhiStorageImage image,
                                  StorageAccess access,
                                  int mipLevel) {
    public StorageImageBinding {
        if (binding < 0) throw new IllegalArgumentException("binding");
        if (image == null) throw new IllegalArgumentException("image");
        access = access == null ? image.descriptor().access() : access;
        if (mipLevel < 0 || mipLevel >= image.descriptor().mipLevels()) {
            throw new IndexOutOfBoundsException("Storage image mip " + mipLevel
                    + " outside [0, " + image.descriptor().mipLevels() + ")");
        }
    }

    public StorageImageBinding(int binding, RhiStorageImage image, StorageAccess access) {
        this(binding, image, access, 0);
    }
}
