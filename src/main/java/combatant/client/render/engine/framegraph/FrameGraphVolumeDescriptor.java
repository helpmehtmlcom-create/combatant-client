/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;

/** Physical requirements for a graph-owned native 3D image. */
public record FrameGraphVolumeDescriptor(StorageVolumeDescriptor volume)
        implements FrameGraphPhysicalResourceDescriptor {
    public FrameGraphVolumeDescriptor {
        if (volume == null) throw new IllegalArgumentException("volume");
    }

    @Override
    public FrameGraphResourceKind kind() {
        return FrameGraphResourceKind.VOLUME;
    }

    @Override
    public long approximateByteSize() {
        return volume.approximateByteSize();
    }

    @Override
    public boolean supports(FrameGraphAccess access) {
        if (access == null) return false;
        boolean readable = volume.sampled() || volume.copySource() || (volume.storage() && volume.access().readable());
        boolean writable = volume.copyDestination() || (volume.storage() && volume.access().writable());
        return (!access.reads() || readable) && (!access.writes() || writable);
    }

    @Override
    public boolean aliasCompatible(FrameGraphPhysicalResourceDescriptor other) {
        if (!(other instanceof FrameGraphVolumeDescriptor value)) return false;
        StorageVolumeDescriptor a = volume;
        StorageVolumeDescriptor b = value.volume;
        return a.width() == b.width() && a.height() == b.height() && a.depth() == b.depth()
                && a.format() == b.format() && a.access() == b.access()
                && a.usage() == b.usage() && a.mipLevels() == b.mipLevels();
    }

    @Override
    public String debugDescription() {
        return volume.width() + "x" + volume.height() + "x" + volume.depth()
                + " mips=" + volume.mipLevels() + " format=" + volume.format()
                + " usage=0x" + Integer.toHexString(volume.usage()) + " access=" + volume.access();
    }
}
