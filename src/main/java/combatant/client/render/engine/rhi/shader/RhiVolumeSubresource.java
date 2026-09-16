/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Non-owning mip-range selector used by transfer/clear commands independently of shader views. */
public record RhiVolumeSubresource(RhiStorageVolume volume,
                                   int baseMipLevel,
                                   int mipLevels) {
    public RhiVolumeSubresource {
        if (volume == null) throw new IllegalArgumentException("volume");
        if (baseMipLevel < 0) throw new IllegalArgumentException("baseMipLevel");
        if (mipLevels < 1) throw new IllegalArgumentException("mipLevels");
        requireValidRange(volume, baseMipLevel, mipLevels);
    }

    public static RhiVolumeSubresource mip(RhiStorageVolume volume, int mipLevel) {
        return new RhiVolumeSubresource(volume, mipLevel, 1);
    }

    public int width() {
        requireValid();
        return volume.descriptor().mipWidth(baseMipLevel);
    }

    public int height() {
        requireValid();
        return volume.descriptor().mipHeight(baseMipLevel);
    }

    public int depth() {
        requireValid();
        return volume.descriptor().mipDepth(baseMipLevel);
    }

    public boolean isValid() {
        if (volume.isClosed()) return false;
        StorageVolumeDescriptor descriptor = volume.descriptor();
        return baseMipLevel < descriptor.mipLevels()
                && mipLevels <= descriptor.mipLevels() - baseMipLevel;
    }

    public void requireValid() {
        if (!isValid()) {
            throw new IllegalStateException("Volume subresource is no longer valid: " + volume.descriptor().label());
        }
    }

    private static void requireValidRange(RhiStorageVolume volume, int baseMipLevel, int mipLevels) {
        if (volume.isClosed()) {
            throw new IllegalStateException("Storage volume is closed: " + volume.descriptor().label());
        }
        StorageVolumeDescriptor descriptor = volume.descriptor();
        if (baseMipLevel >= descriptor.mipLevels() || mipLevels > descriptor.mipLevels() - baseMipLevel) {
            throw new IllegalArgumentException("Volume subresource mip range [" + baseMipLevel + ", "
                    + (baseMipLevel + mipLevels) + ") exceeds resource mip count " + descriptor.mipLevels()
                    + ": " + descriptor.label());
        }
    }
}
