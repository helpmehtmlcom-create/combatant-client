/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.blaze3d.opengl.GlConst;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiValidation;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import combatant.client.util.logging.DebugLog;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

/** Native GL_TEXTURE_3D allocation owned by the active Mojang OpenGL context. */
final class GlStorageVolume implements RhiStorageVolume {
    private final StorageVolumeDescriptor descriptor;
    private final RhiStats stats;
    final int id;
    private boolean closed;

    GlStorageVolume(StorageVolumeDescriptor descriptor, RhiStats stats) {
        this.descriptor = descriptor;
        this.stats = stats;
        int max3d = GL11C.glGetInteger(GL12C.GL_MAX_3D_TEXTURE_SIZE);
        if (descriptor.width() > max3d || descriptor.height() > max3d || descriptor.depth() > max3d) {
            throw new UnsupportedOperationException("OpenGL 3D texture exceeds GL_MAX_3D_TEXTURE_SIZE=" + max3d
                    + ": " + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth());
        }
        int internalFormat = GlConst.toGlInternalId(descriptor.format());
        if (internalFormat == 0) {
            throw new UnsupportedOperationException("OpenGL has no sized internal format for " + descriptor.format());
        }
        validateInternalFormat(descriptor, internalFormat);

        int previous = GL11C.glGetInteger(GL12C.GL_TEXTURE_BINDING_3D);
        int texture = GL11C.glGenTextures();
        try {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, texture);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_MAX_LEVEL, descriptor.mipLevels() - 1);
            GL42C.glTexStorage3D(GL12C.GL_TEXTURE_3D, descriptor.mipLevels(), internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.depth());
        } catch (RuntimeException | Error t) {
            GL11C.glDeleteTextures(texture);
            throw t;
        } finally {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, previous);
        }
        this.id = texture;
        stats.storageVolumeAllocated(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/GL] allocated storage volume '{}' {}x{}x{} mips={} format={} approxBytes={}",
                descriptor.label(), descriptor.width(), descriptor.height(), descriptor.depth(), descriptor.mipLevels(),
                descriptor.format(), descriptor.approximateByteSize());
    }

    private static void validateInternalFormat(StorageVolumeDescriptor descriptor, int internalFormat) {
        int supported = GL43C.glGetInternalformati(GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_INTERNALFORMAT_SUPPORTED);
        if (supported != GL11C.GL_TRUE) {
            throw new UnsupportedOperationException("OpenGL internal format is unsupported for GL_TEXTURE_3D: "
                    + descriptor.format());
        }
        if (descriptor.access().readable()) {
            int imageLoad = GL43C.glGetInternalformati(GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_SHADER_IMAGE_LOAD);
            if (imageLoad != GL11C.GL_TRUE) {
                throw new UnsupportedOperationException("OpenGL format does not support image load for 3D storage: "
                        + descriptor.format());
            }
        }
        if (descriptor.access().writable()) {
            int imageStore = GL43C.glGetInternalformati(GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_SHADER_IMAGE_STORE);
            if (imageStore != GL11C.GL_TRUE) {
                throw new UnsupportedOperationException("OpenGL format does not support image store for 3D storage: "
                        + descriptor.format());
            }
        }
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            RhiValidation.doubleDestroy("OpenGL storage volume", descriptor.label());
            return;
        }
        closed = true;
        GL11C.glDeleteTextures(id);
        stats.storageVolumeDestroyed(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/GL] destroyed storage volume '{}'", descriptor.label());
    }
}
