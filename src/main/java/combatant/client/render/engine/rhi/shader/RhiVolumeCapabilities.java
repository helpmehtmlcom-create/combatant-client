/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/**
 * Format/descriptor-specific native 3D resource capability report.
 * mipViews describes sampled mip-range views; single-mip storage binding is part of storageViews.
 */
public record RhiVolumeCapabilities(RhiFeatureSupport allocation,
                                    RhiFeatureSupport storageViews,
                                    RhiFeatureSupport sampledViews,
                                    RhiFeatureSupport mipViews,
                                    RhiFeatureSupport copy,
                                    RhiFeatureSupport clear) {
    public RhiVolumeCapabilities {
        if (allocation == null || storageViews == null || sampledViews == null
                || mipViews == null || copy == null || clear == null) {
            throw new IllegalArgumentException("Volume capability entries must be explicit");
        }
    }

    public boolean supportsDescriptor(StorageVolumeDescriptor descriptor) {
        if (!allocation.supported()) return false;
        if (descriptor.storage() && !storageViews.supported()) return false;
        if (descriptor.sampled() && !sampledViews.supported()) return false;
        if ((descriptor.copySource() || descriptor.copyDestination()) && !copy.supported()) return false;
        return true;
    }

    public static RhiVolumeCapabilities unsupported(String reason) {
        RhiFeatureSupport value = RhiFeatureSupport.unsupported(reason, RhiFeatureSupport.Fallback.DISABLE_CONSUMER);
        return new RhiVolumeCapabilities(value, value, value, value, value, value);
    }
}
