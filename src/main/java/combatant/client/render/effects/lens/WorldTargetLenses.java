/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.lens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.uniform.impl.TargetLensUniforms;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class WorldTargetLenses implements PostProcessPass {
    public static final WorldTargetLenses INSTANCE = new WorldTargetLenses();

    private final List<Request> pending = new ArrayList<>();
    private long pendingFrameId = Long.MIN_VALUE;

    private WorldTargetLenses() {
    }

    public static void submit(Vec3 position, float radius, float strength, boolean depthTest) {
        INSTANCE.enqueue(position, radius, strength, depthTest);
    }

    public static void clear() {
        INSTANCE.clearPending();
    }

    private synchronized void enqueue(Vec3 position, float radius, float strength, boolean depthTest) {
        if (position == null || radius <= 0.001f || strength <= 0.0001f) return;
        AABB bounds = new AABB(
                position.x - radius, position.y - radius, position.z - radius,
                position.x + radius, position.y + radius, position.z + radius
        );
        if (!Renderer3D.Culling.isInFrustum(bounds) || !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingFrameId = frameId;
        }
        if (pending.size() >= TargetLensUniforms.MAX_LENSES) return;
        pending.add(new Request(position, radius, strength, depthTest));
    }

    @Override
    public synchronized boolean isActive() {
        return !pending.isEmpty() && pendingFrameId == currentFrameId();
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public Phase getPhase() {
        return Phase.PRE_HAND;
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (execution == null || execution.context() == null || execution.source() == null || execution.destination() == null) {
            drain();
            return false;
        }

        List<Request> requests = drain();
        if (requests.isEmpty()) return false;

        GpuTextureView depth = execution.context().preTranslucentDepth();
        boolean depthUsable = depth != null
                && depth.getWidth(0) == execution.context().width()
                && depth.getHeight(0) == execution.context().height();

        Vec3 camera = RenderState.cameraPos;
        if (camera == null) return false;

        Matrix4f view = new Matrix4f().rotation(new Quaternionf(RenderState.cameraRotation).conjugate());
        Matrix4f viewProjection = new Matrix4f(RenderState.worldProjection).mul(view);
        boolean zeroToOneDepth = execution.rhi().capabilities().zeroToOneDepth();
        float projectionX = RenderState.worldProjection.m00();
        float projectionY = RenderState.worldProjection.m11();
        float aspect = Math.abs(projectionX) > 1.0e-6f ? projectionY / projectionX : 1.0f;

        float[] lenses = new float[TargetLensUniforms.MAX_LENSES * 4];
        int count = 0;
        float strength = 0.0f;
        Vector4f clip = new Vector4f();

        for (Request request : requests) {
            if (request.depthTest() && !depthUsable) continue;
            Vec3 relative = request.position().subtract(camera);
            clip.set((float) relative.x, (float) relative.y, (float) relative.z, 1.0f);
            viewProjection.transform(clip);
            if (clip.w <= 0.05f) continue;

            float invW = 1.0f / clip.w;
            float u = clip.x * invW * 0.5f + 0.5f;
            float v = clip.y * invW * 0.5f + 0.5f;
            float ndcDepth = clip.z * invW;
            float rawDepth = zeroToOneDepth ? ndcDepth : ndcDepth * 0.5f + 0.5f;
            float radiusUv = Math.min(request.radius() * projectionY * invW * 0.5f, 0.35f);
            if (radiusUv <= 0.0001f) continue;
            if (u < -radiusUv * 2.0f || u > 1.0f + radiusUv * 2.0f
                    || v < -radiusUv * 2.0f || v > 1.0f + radiusUv * 2.0f) continue;

            int base = count * 4;
            lenses[base] = u;
            lenses[base + 1] = v;
            lenses[base + 2] = rawDepth;
            lenses[base + 3] = request.depthTest() ? radiusUv : -radiusUv;
            strength = Math.max(strength, request.strength());
            count++;
            if (count >= TargetLensUniforms.MAX_LENSES) break;
        }

        if (count == 0 || strength <= 0.0001f) return false;

        TargetLensUniforms.update(aspect, strength, depthUsable, count, lenses);
        GpuSampler linear = PostProcessManager.getSampler();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        execution.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant target lenses")
                        .colorAttachment(execution.destination())
                        .pipeline(CombatantRenderPipelines.POSTPROCESS_TARGET_LENS)
                        .uniform("TargetLens", TargetLensUniforms.get())
                        .sampler("u_Texture", execution.source(), linear)
                        .sampler("u_Depth", depthUsable ? depth : execution.source(), nearest)
                        .build()
        );
        return true;
    }

    private synchronized List<Request> drain() {
        long frameId = currentFrameId();
        if (pendingFrameId != frameId || pending.isEmpty()) {
            pending.clear();
            pendingFrameId = frameId;
            return List.of();
        }
        List<Request> out = List.copyOf(pending);
        pending.clear();
        return out;
    }

    private synchronized void clearPending() {
        pending.clear();
        pendingFrameId = Long.MIN_VALUE;
    }

    private static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : CombatantRenderSystem.resources().frameId();
    }

    private record Request(Vec3 position, float radius, float strength, boolean depthTest) {
    }
}
