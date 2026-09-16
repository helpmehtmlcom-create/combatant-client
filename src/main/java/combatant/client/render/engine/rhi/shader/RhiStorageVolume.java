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

    @Override
    void close();
}
