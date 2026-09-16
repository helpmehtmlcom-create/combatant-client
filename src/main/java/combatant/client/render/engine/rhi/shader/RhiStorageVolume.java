/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Backend-owned native 3D image. The logical XYZ layout is backend independent. */
public interface RhiStorageVolume extends AutoCloseable {
    StorageVolumeDescriptor descriptor();

    boolean isClosed();

    String backendName();

    default RhiVolumeDebugInfo debugInfo() {
        StorageVolumeDescriptor descriptor = descriptor();
        return new RhiVolumeDebugInfo(backendName(), descriptor.label(),
                descriptor.width(), descriptor.height(), descriptor.depth(), descriptor.mipLevels(),
                descriptor.format(), descriptor.usage(), descriptor.approximateByteSize(), isClosed());
    }

    default RhiVolumeView view(VolumeViewDescriptor descriptor) {
        return new RhiVolumeView(this, descriptor);
    }

    default RhiVolumeView storageView(int mipLevel) {
        return view(VolumeViewDescriptor.storage(mipLevel));
    }

    default RhiVolumeSubresource subresources(int baseMipLevel, int mipLevels) {
        return new RhiVolumeSubresource(this, baseMipLevel, mipLevels);
    }

    default RhiVolumeSubresource mip(int mipLevel) {
        return RhiVolumeSubresource.mip(this, mipLevel);
    }

    default RhiVolumeView sampledView() {
        return view(VolumeViewDescriptor.sampled(0, descriptor().mipLevels()));
    }

    default RhiVolumeView sampledMipView(int mipLevel) {
        return view(VolumeViewDescriptor.sampledMip(mipLevel));
    }

    @Override
    void close();
}
