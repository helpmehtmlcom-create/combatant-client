/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Non-owning typed subresource view of a native 3D image. Native view handles remain volume-owned. */
public record RhiVolumeView(RhiStorageVolume volume, VolumeViewDescriptor descriptor) {
    public RhiVolumeView {
        if (volume == null) throw new IllegalArgumentException("volume");
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (volume.isClosed()) throw new IllegalStateException("Storage volume is closed: " + volume.descriptor().label());
        StorageVolumeDescriptor resource = volume.descriptor();
        if (descriptor.baseMipLevel() >= resource.mipLevels()
                || descriptor.mipLevels() > resource.mipLevels() - descriptor.baseMipLevel()) {
            throw new IllegalArgumentException("Volume view mip range [" + descriptor.baseMipLevel() + ", "
                    + (descriptor.baseMipLevel() + descriptor.mipLevels()) + ") exceeds resource mip count "
                    + resource.mipLevels() + ": " + resource.label());
        }
        if (descriptor.sampled() && !resource.sampled()) {
            throw new IllegalArgumentException("Volume was not created with sampled usage: " + resource.label());
        }
        if (descriptor.storage() && !resource.storage()) {
            throw new IllegalArgumentException("Volume was not created with storage usage: " + resource.label());
        }
    }

    public boolean isValid() {
        return !volume.isClosed();
    }

    public int width() {
        requireValid();
        return volume.descriptor().mipWidth(descriptor.baseMipLevel());
    }

    public int height() {
        requireValid();
        return volume.descriptor().mipHeight(descriptor.baseMipLevel());
    }

    public int depth() {
        requireValid();
        return volume.descriptor().mipDepth(descriptor.baseMipLevel());
    }

    public void requireValid() {
        if (!isValid()) throw new IllegalStateException("Volume view references a closed resource: " + volume.descriptor().label());
    }
}
