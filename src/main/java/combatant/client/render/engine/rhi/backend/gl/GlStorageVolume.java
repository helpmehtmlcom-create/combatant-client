/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.blaze3d.opengl.GlConst;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL42C;

/** Native GL_TEXTURE_3D allocation owned by the active Mojang OpenGL context. */
final class GlStorageVolume implements RhiStorageVolume {
    private final StorageVolumeDescriptor descriptor;
    final int id;
    private boolean closed;

    GlStorageVolume(StorageVolumeDescriptor descriptor) {
        this.descriptor = descriptor;
        int max3d = GL11C.glGetInteger(GL12C.GL_MAX_3D_TEXTURE_SIZE);
        if (descriptor.width() > max3d || descriptor.height() > max3d || descriptor.depth() > max3d) {
            throw new UnsupportedOperationException("OpenGL 3D texture exceeds GL_MAX_3D_TEXTURE_SIZE=" + max3d
                    + ": " + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth());
        }
        int internalFormat = GlConst.toGlInternalId(descriptor.format());
        if (internalFormat == 0) {
            throw new UnsupportedOperationException("OpenGL has no sized internal format for " + descriptor.format());
        }

        int previous = GL11C.glGetInteger(GL12C.GL_TEXTURE_BINDING_3D);
        int texture = GL11C.glGenTextures();
        try {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, texture);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
            GL42C.glTexStorage3D(GL12C.GL_TEXTURE_3D, 1, internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.depth());
        } catch (RuntimeException | Error t) {
            GL11C.glDeleteTextures(texture);
            throw t;
        } finally {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, previous);
        }
        this.id = texture;
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        GL11C.glDeleteTextures(id);
    }

    boolean closed() {
        return closed;
    }
}
