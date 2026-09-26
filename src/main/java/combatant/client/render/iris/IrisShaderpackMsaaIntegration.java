/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import combatant.client.config.MainConfig;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Generic Iris gbuffer MSAA attachment/resolve bridge enabled by manifest capability. */
public enum IrisShaderpackMsaaIntegration {
    ;
    private static final ThreadLocal<Boolean> CREATING_GBUFFER = ThreadLocal.withInitial(() -> false);
    private static final Map<Integer, TexturePair> TEXTURES = new LinkedHashMap<>();
    private static final Map<Integer, FramebufferState> FRAMEBUFFERS = new HashMap<>();
    private static long epoch = -1L;
    private static int colorSeedProgram;
    private static int depthSeedProgram;
    private static int seedVao;

    public static void beginGbufferFramebuffer() {
        // Capture Photon/Iris gbuffer attachment metadata for runtime AA switching, but do not
        // mutate framebuffer topology merely because the adapter supports MSAA. The previous
        // implementation replaced every gbuffer attachment even while Combatant TAA/OFF owned
        // AA, which left Photon writing world geometry into multisample textures that its
        // single-sample composite chain never consumed.
        syncConstructionEpoch();
        CREATING_GBUFFER.set(adapter().msaaReplacement());
    }

    public static void endGbufferFramebuffer() {
        CREATING_GBUFFER.set(false);
    }

    public static boolean redirectColor(GlFramebuffer framebuffer, int attachmentIndex, int texture) {
        if (!CREATING_GBUFFER.get() || framebuffer == null || texture <= 0) return false;
        try {
            FramebufferState state = framebuffer(framebuffer.getId());
            state.colorTextures.put(attachmentIndex, texture);
            if (!wantsMsaa()) return false;

            TexturePair pair = pair(texture, false);
            attach(state.msaaFbo, GL30C.GL_COLOR_ATTACHMENT0 + attachmentIndex,
                    GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
            attach(state.singleSampleFbo, GL30C.GL_COLOR_ATTACHMENT0 + attachmentIndex,
                    GL11C.GL_TEXTURE_2D, texture);
            state.active = true;
            return true;
        } catch (Throwable throwable) {
            fail("MSAA color attachment failed", throwable);
            return false;
        }
    }

    public static boolean redirectDepth(GlFramebuffer framebuffer, GpuTexture texture) {
        if (!CREATING_GBUFFER.get() || framebuffer == null || !(texture instanceof GlTexture glTexture)) return false;
        try {
            int textureId = glTexture.glId();
            FramebufferState state = framebuffer(framebuffer.getId());
            state.depthTexture = textureId;
            if (!wantsMsaa()) return false;

            TexturePair pair = pair(textureId, true);
            attach(state.msaaFbo, GL30C.GL_DEPTH_ATTACHMENT,
                    GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
            attach(state.singleSampleFbo, GL30C.GL_DEPTH_ATTACHMENT,
                    GL11C.GL_TEXTURE_2D, textureId);
            state.active = true;
            return true;
        } catch (Throwable throwable) {
            fail("MSAA depth attachment failed", throwable);
            return false;
        }
    }

    public static void configureDrawBuffers(GlFramebuffer framebuffer, int[] buffers) {
        FramebufferState state = state(framebuffer);
        if (state == null || buffers == null) return;
        state.drawBuffers = buffers.clone();
        state.noDrawBuffers = false;
        if (state.active) configureTwinDrawBuffers(state);
    }

    public static void configureReadBuffer(GlFramebuffer framebuffer, int buffer) {
        FramebufferState state = state(framebuffer);
        if (state == null) return;
        state.readBuffer = buffer;
        if (state.active) configureTwinReadBuffer(state);
    }

    public static void configureNoDrawBuffers(GlFramebuffer framebuffer) {
        FramebufferState state = state(framebuffer);
        if (state == null) return;
        state.drawBuffers = null;
        state.noDrawBuffers = true;
        if (state.active) configureTwinDrawBuffers(state);
    }

    /** Returns true when the normal Iris bind must be cancelled in favor of the single-sample twin. */
    public static boolean bindFallback(GlFramebuffer framebuffer, int target) {
        FramebufferState state = state(framebuffer);
        // In TAA/OFF the original Iris framebuffer must remain completely native. The fallback
        // twin is only a short transition safety net if the AA owner changed mid-frame before
        // beginFrame() had a chance to restore the original attachments.
        if (state == null || !state.active || wantsMsaa()) return false;
        GL30C.glBindFramebuffer(target, state.singleSampleFbo);
        return true;
    }

    public static void beginFrame(long integrationEpoch) {
        if (epoch < 0L) {
            epoch = integrationEpoch;
        } else if (epoch != integrationEpoch) {
            shutdown();
            epoch = integrationEpoch;
        }

        boolean requestedMsaa = wantsMsaa();
        try {
            if (!requestedMsaa) {
                deactivateAll(true);
                return;
            }
            activateAll();
            refreshTextures();
            verifyFramebuffers();
            blitReferenced(false);
        } catch (Throwable throwable) {
            fail("MSAA frame seed failed", throwable);
        }
    }


    /** Refreshes multisample targets after an Iris prepare/deferred stage changed single-sample contents. */
    public static void seedWorld() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            blitReferenced(false);
        } catch (Throwable throwable) {
            fail("MSAA world seed failed", throwable);
        }
    }

    public static void resolveWorld() {
        if (!wantsMsaa()) return;
        try {
            refreshTextures();
            blitReferenced(true);
        } catch (Throwable throwable) {
            fail("MSAA world resolve failed", throwable);
        }
    }

    public static void framebufferDestroyed(GlFramebuffer framebuffer) {
        FramebufferState state = state(framebuffer);
        if (state != null) {
            FRAMEBUFFERS.remove(state.msaaFbo);
            if (state.singleSampleFbo > 0) GL30C.glDeleteFramebuffers(state.singleSampleFbo);
        }
    }

    public static void shutdown() {
        // An integration epoch can change while Iris still owns the framebuffer objects. Always
        // put their original single-sample attachments back before deleting our multisample
        // resources. Otherwise the Iris FBO keeps dangling/foreign attachments after teardown.
        deactivateAll(false);
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.singleSampleFbo > 0) GL30C.glDeleteFramebuffers(state.singleSampleFbo);
        }
        FRAMEBUFFERS.clear();
        for (TexturePair pair : TEXTURES.values()) {
            if (pair.multisampleTexture > 0) GL11C.glDeleteTextures(pair.multisampleTexture);
        }
        TEXTURES.clear();
        if (colorSeedProgram > 0) GL20C.glDeleteProgram(colorSeedProgram);
        if (depthSeedProgram > 0) GL20C.glDeleteProgram(depthSeedProgram);
        if (seedVao > 0) GL30C.glDeleteVertexArrays(seedVao);
        colorSeedProgram = 0;
        depthSeedProgram = 0;
        seedVao = 0;
        CREATING_GBUFFER.remove();
        epoch = -1L;
    }

    private static TexturePair pair(int singleTexture, boolean depth) {
        TexturePair current = TEXTURES.get(singleTexture);
        if (current != null) return current;
        TextureInfo info = inspect(singleTexture);
        int samples = samples();
        int multisample = GL11C.glGenTextures();
        allocate(multisample, samples, info);
        TexturePair created = new TexturePair(singleTexture, multisample, depth, samples,
                info.width, info.height, info.internalFormat);
        TEXTURES.put(singleTexture, created);
        return created;
    }

    private static void syncConstructionEpoch() {
        long current = IrisRuntime.integrationEpoch();
        if (epoch < 0L) {
            epoch = current;
            return;
        }
        if (epoch != current) {
            shutdown();
            epoch = current;
        }
    }

    private static void activateAll() {
        boolean activated = false;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.active) continue;
            for (Map.Entry<Integer, Integer> entry : state.colorTextures.entrySet()) {
                TexturePair pair = pair(entry.getValue(), false);
                attach(state.msaaFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                attach(state.singleSampleFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                        GL11C.GL_TEXTURE_2D, entry.getValue());
            }
            if (state.depthTexture > 0) {
                TexturePair pair = pair(state.depthTexture, true);
                attach(state.msaaFbo, GL30C.GL_DEPTH_ATTACHMENT,
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture);
                attach(state.singleSampleFbo, GL30C.GL_DEPTH_ATTACHMENT,
                        GL11C.GL_TEXTURE_2D, state.depthTexture);
            }
            state.active = true;
            activated = true;
            configureTwinDrawBuffers(state);
            configureTwinReadBuffer(state);
        }
        if (activated) {
            DebugLog.info("[IrisCompat] shaderpack MSAA framebuffer topology activated (%d FBOs, %dx)",
                    FRAMEBUFFERS.size(), samples());
        }
    }

    private static void deactivateAll(boolean preserveContents) {
        boolean anyActive = false;
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (state.active) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) return;

        if (preserveContents) {
            // Preserve the last multisample world contents before returning ownership to Iris.
            blitReferenced(true);
        }
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            if (!state.active) continue;
            if (GL30C.glIsFramebuffer(state.msaaFbo)) {
                for (Map.Entry<Integer, Integer> entry : state.colorTextures.entrySet()) {
                    attach(state.msaaFbo, GL30C.GL_COLOR_ATTACHMENT0 + entry.getKey(),
                            GL11C.GL_TEXTURE_2D, entry.getValue());
                }
                if (state.depthTexture > 0) {
                    attach(state.msaaFbo, GL30C.GL_DEPTH_ATTACHMENT,
                            GL11C.GL_TEXTURE_2D, state.depthTexture);
                }
            }
            state.active = false;
        }
        DebugLog.info("[IrisCompat] shaderpack MSAA framebuffer topology deactivated (%d FBOs)",
                FRAMEBUFFERS.size());
    }

    private static void configureTwinDrawBuffers(FramebufferState state) {
        if (state == null || !state.active || state.singleSampleFbo <= 0) return;
        if (state.noDrawBuffers) {
            withFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, state.singleSampleFbo,
                    () -> GL11C.glDrawBuffer(GL11C.GL_NONE));
            return;
        }
        if (state.drawBuffers == null) return;
        int[] glBuffers = new int[state.drawBuffers.length];
        for (int i = 0; i < state.drawBuffers.length; i++) {
            glBuffers[i] = GL30C.GL_COLOR_ATTACHMENT0 + state.drawBuffers[i];
        }
        withFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, state.singleSampleFbo,
                () -> GL20C.glDrawBuffers(glBuffers));
    }

    private static void configureTwinReadBuffer(FramebufferState state) {
        if (state == null || !state.active || state.singleSampleFbo <= 0 || state.readBuffer < 0) return;
        withFramebuffer(GL30C.GL_READ_FRAMEBUFFER, state.singleSampleFbo,
                () -> GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + state.readBuffer));
    }

    private static void refreshTextures() {
        int requested = samples();
        Set<Integer> referenced = referencedTextures();
        var iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TexturePair> entry = iterator.next();
            TexturePair pair = entry.getValue();
            if (!referenced.contains(entry.getKey())) {
                GL11C.glDeleteTextures(pair.multisampleTexture);
                iterator.remove();
                continue;
            }
            TextureInfo info = inspect(pair.singleTexture);
            if (info.width <= 0 || info.height <= 0) {
                throw new IllegalStateException("shaderpack target has invalid dimensions");
            }
            if (pair.width != info.width || pair.height != info.height
                    || pair.samples != requested || pair.internalFormat != info.internalFormat) {
                allocate(pair.multisampleTexture, requested, info);
                entry.setValue(new TexturePair(pair.singleTexture, pair.multisampleTexture, pair.depth,
                        requested, info.width, info.height, info.internalFormat));
            }
        }
    }

    private static void blitReferenced(boolean resolve) {
        for (int texture : referencedTextures()) {
            TexturePair pair = TEXTURES.get(texture);
            if (pair == null) continue;
            int source = resolve ? pair.multisampleTexture : pair.singleTexture;
            int destination = resolve ? pair.singleTexture : pair.multisampleTexture;
            int sourceTarget = resolve ? GL32C.GL_TEXTURE_2D_MULTISAMPLE : GL11C.GL_TEXTURE_2D;
            int destinationTarget = resolve ? GL11C.GL_TEXTURE_2D : GL32C.GL_TEXTURE_2D_MULTISAMPLE;
            int mask = pair.depth ? GL11C.GL_DEPTH_BUFFER_BIT : GL11C.GL_COLOR_BUFFER_BIT;
            if (resolve) {
                blit(source, sourceTarget, destination, destinationTarget, pair.width, pair.height, mask);
            } else {
                rasterSeed(pair);
            }
        }
    }

    private static void rasterSeed(TexturePair pair) {
        int framebuffer = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean multisample = GL11C.glIsEnabled(GL13C.GL_MULTISAMPLE);
        boolean framebufferSrgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        boolean rasterizerDiscard = GL11C.glIsEnabled(GL30C.GL_RASTERIZER_DISCARD);
        boolean previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        int previousTexture;
        int previousSampler;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            IntBuffer polygonMode = stack.mallocInt(2);
            ByteBuffer colorMask = stack.malloc(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL20C.GL_POLYGON_MODE, polygonMode);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            previousSampler = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            try {
                GL33C.glBindSampler(0, 0);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, pair.singleTexture);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                int attachment = pair.depth ? GL30C.GL_DEPTH_ATTACHMENT : GL30C.GL_COLOR_ATTACHMENT0;
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment,
                        GL32C.GL_TEXTURE_2D_MULTISAMPLE, pair.multisampleTexture, 0);
                if (pair.depth) {
                    GL11C.glDrawBuffer(GL11C.GL_NONE);
                    GL11C.glReadBuffer(GL11C.GL_NONE);
                } else {
                    GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                }
                if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("incomplete shaderpack MSAA seed framebuffer");
                }

                GL11C.glViewport(0, 0, pair.width, pair.height);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
                GL11C.glDisable(GL30C.GL_RASTERIZER_DISCARD);
                GL11C.glEnable(GL13C.GL_MULTISAMPLE);
                GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
                if (pair.depth) {
                    GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                    GL11C.glDepthFunc(GL11C.GL_ALWAYS);
                    GL11C.glDepthMask(true);
                    GL11C.glColorMask(false, false, false, false);
                } else {
                    GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                    GL11C.glColorMask(true, true, true, true);
                }

                int program = pair.depth ? depthSeedProgram() : colorSeedProgram();
                GL20C.glUseProgram(program);
                GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "sourceTexture"), 0);
                GL30C.glBindVertexArray(seedVao());
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
                GL33C.glBindSampler(0, previousSampler);
                GL13C.glActiveTexture(previousActiveTexture);
                GL20C.glUseProgram(previousProgram);
                GL30C.glBindVertexArray(previousVao);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL11C.glPolygonMode(GL11C.GL_FRONT, polygonMode.get(0));
                GL11C.glPolygonMode(GL11C.GL_BACK, polygonMode.get(1));
                GL11C.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL11C.glDepthMask(previousDepthMask);
                GL11C.glDepthFunc(previousDepthFunc);
                restore(GL11C.GL_DEPTH_TEST, depthTest);
                restore(GL11C.GL_BLEND, blend);
                restore(GL11C.GL_SCISSOR_TEST, scissor);
                restore(GL11C.GL_CULL_FACE, cull);
                restore(GL13C.GL_MULTISAMPLE, multisample);
                restore(GL30C.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
                restore(GL30C.GL_RASTERIZER_DISCARD, rasterizerDiscard);
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(framebuffer);
        }
    }

    private static int colorSeedProgram() {
        if (colorSeedProgram == 0) colorSeedProgram = createSeedProgram(false);
        return colorSeedProgram;
    }

    private static int depthSeedProgram() {
        if (depthSeedProgram == 0) depthSeedProgram = createSeedProgram(true);
        return depthSeedProgram;
    }

    private static int seedVao() {
        if (seedVao == 0) seedVao = GL30C.glGenVertexArrays();
        return seedVao;
    }

    private static int createSeedProgram(boolean depth) {
        String vertex = """
                #version 330 core
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        String fragment = depth ? """
                #version 330 core
                uniform sampler2D sourceTexture;
                void main() {
                    gl_FragDepth = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), 0).r;
                }
                """ : """
                #version 330 core
                uniform sampler2D sourceTexture;
                layout(location = 0) out vec4 outputColor;
                void main() {
                    outputColor = texelFetch(sourceTexture, ivec2(gl_FragCoord.xy), 0);
                }
                """;
        int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, vertex);
        int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int program = GL20C.glCreateProgram();
        try {
            GL20C.glAttachShader(program, vertexShader);
            GL20C.glAttachShader(program, fragmentShader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("MSAA seed program link failed: " + GL20C.glGetProgramInfoLog(program));
            }
            return program;
        } catch (Throwable throwable) {
            GL20C.glDeleteProgram(program);
            throw throwable;
        } finally {
            GL20C.glDeleteShader(vertexShader);
            GL20C.glDeleteShader(fragmentShader);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("MSAA seed shader compile failed: " + log);
        }
        return shader;
    }

    private static void restore(int capability, boolean enabled) {
        if (enabled) GL11C.glEnable(capability); else GL11C.glDisable(capability);
    }

    private static Set<Integer> referencedTextures() {
        Set<Integer> referenced = new HashSet<>();
        for (FramebufferState state : FRAMEBUFFERS.values()) {
            referenced.addAll(state.colorTextures.values());
            if (state.depthTexture > 0) referenced.add(state.depthTexture);
        }
        return referenced;
    }

    private static void verifyFramebuffers() {
        int previous = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        try {
            for (FramebufferState state : FRAMEBUFFERS.values()) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, state.msaaFbo);
                int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("multisample framebuffer incomplete: 0x"
                            + Integer.toHexString(status));
                }
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previous);
        }
    }

    private static void blit(int source, int sourceTarget, int destination, int destinationTarget,
                             int width, int height, int mask) {
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        int attachment = mask == GL11C.GL_DEPTH_BUFFER_BIT
                ? GL30C.GL_DEPTH_ATTACHMENT : GL30C.GL_COLOR_ATTACHMENT0;
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, attachment, sourceTarget, source, 0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, attachment, destinationTarget, destination, 0);
            if (mask == GL11C.GL_COLOR_BUFFER_BIT) {
                GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
                GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            } else {
                GL11C.glReadBuffer(GL11C.GL_NONE);
                GL11C.glDrawBuffer(GL11C.GL_NONE);
            }
            GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, mask, GL11C.GL_NEAREST);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }

    private static TextureInfo inspect(int texture) {
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            return new TextureInfo(
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH),
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT),
                    GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT));
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        }
    }

    private static void allocate(int texture, int samples, TextureInfo info) {
        int previous = GL11C.glGetInteger(GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE);
        try {
            GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, texture);
            GL32C.glTexImage2DMultisample(GL32C.GL_TEXTURE_2D_MULTISAMPLE, samples,
                    info.internalFormat, info.width, info.height, true);
        } finally {
            GL11C.glBindTexture(GL32C.GL_TEXTURE_2D_MULTISAMPLE, previous);
        }
    }

    private static FramebufferState framebuffer(int msaaFbo) {
        return FRAMEBUFFERS.computeIfAbsent(msaaFbo,
                ignored -> new FramebufferState(msaaFbo, GL30C.glGenFramebuffers()));
    }

    private static FramebufferState state(GlFramebuffer framebuffer) {
        return framebuffer == null ? null : FRAMEBUFFERS.get(framebuffer.getId());
    }

    private static void attach(int framebuffer, int attachment, int target, int texture) {
        withFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer,
                () -> GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment, target, texture, 0));
    }

    private static void withFramebuffer(int target, int framebuffer, Runnable operation) {
        int binding = target == GL30C.GL_READ_FRAMEBUFFER
                ? GL30C.GL_READ_FRAMEBUFFER_BINDING
                : target == GL30C.GL_DRAW_FRAMEBUFFER ? GL30C.GL_DRAW_FRAMEBUFFER_BINDING : GL30C.GL_FRAMEBUFFER_BINDING;
        int previous = GL11C.glGetInteger(binding);
        try {
            GL30C.glBindFramebuffer(target, framebuffer);
            operation.run();
        } finally {
            GL30C.glBindFramebuffer(target, previous);
        }
    }

    private static boolean wantsMsaa() {
        return IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode()).owner()
                == IrisAaOwner.COMBATANT_MSAA;
    }

    private static int samples() {
        int requested = Math.max(2, MainConfig.get().getConfiguredMsaa3dSamples());
        return Math.min(requested, Math.max(1, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES)));
    }

    private static ShaderPatchEngine.ShaderpackIntegration adapter() {
        String pack = ShaderPatchEngine.loadingShaderPackName();
        if (pack == null || pack.isBlank()) pack = IrisRuntime.snapshot().shaderpackName();
        return ShaderPatchEngine.profile(pack).integration();
    }

    private static void fail(String action, Throwable throwable) {
        String detail = action + ": " + throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        IrisAaIntegration.failMode("msaa", detail);
        DebugLog.error("[IrisCompat] " + action, throwable);
    }

    private record TextureInfo(int width, int height, int internalFormat) { }
    private record TexturePair(int singleTexture, int multisampleTexture, boolean depth,
                               int samples, int width, int height, int internalFormat) { }
    private static final class FramebufferState {
        private final int msaaFbo;
        private final int singleSampleFbo;
        private final Map<Integer, Integer> colorTextures = new HashMap<>();
        private int depthTexture;
        private int[] drawBuffers;
        private int readBuffer = -1;
        private boolean noDrawBuffers;
        private boolean active;

        private FramebufferState(int msaaFbo, int singleSampleFbo) {
            this.msaaFbo = msaaFbo;
            this.singleSampleFbo = singleSampleFbo;
        }
    }
}
