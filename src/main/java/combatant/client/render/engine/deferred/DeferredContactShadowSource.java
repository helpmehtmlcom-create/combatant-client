/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hi-Z screen-space directional contact visibility. Produces visibility only; no softness/composite policy. */
final class DeferredContactShadowSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/contact_shadow");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("lightDirectionAndDistance", Std430Type.VEC4)
            .member("traceParams0", Std430Type.VEC4)
            .member("traceParams1", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer data;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.shadow.contact", DeferredStage.CONTACT_SHADOW)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.DEPTH_PYRAMID,
                        DeferredResource.GBUFFER_GEOMETRY)
                .write(DeferredResource.CONTACT_SHADOW)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.settings().shadowsEnabled()
                        && context.settings().contactShadowsEnabled()
                        && context.primaryView().current() != null
                        && context.worldState().directionalLight().shadowValid()
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.DEPTH_PYRAMID)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null)
                .execute(this::trace)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void trace(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DirectionalLightDescriptor directional = context.worldState().directionalLight();
        if (current == null || !directional.shadowValid()) return;

        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView pyramid = requireTexture(context, DeferredResource.DEPTH_PYRAMID);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        RhiStorageImage output = requireImage(context, DeferredResource.CONTACT_SHADOW);

        Vector3f worldLight = directional.direction(new Vector3f());
        Vector3f viewLight = transformDirection(current.view(), worldLight, new Vector3f()).normalize();
        DeferredRuntimeConfig.Snapshot settings = context.settings();
        boolean zeroToOne = isVulkan(context);
        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "projection", current.projection())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "lightDirectionAndDistance",
                        viewLight.x, viewLight.y, viewLight.z, settings.contactShadowMaxDistance())
                .putVec4(0, "traceParams0",
                        settings.contactShadowMaxSteps(),
                        settings.contactShadowMinStep(),
                        settings.contactShadowStepGrowth(),
                        settings.contactShadowThickness())
                .putVec4(0, "traceParams1",
                        settings.contactShadowNormalBias(),
                        settings.contactShadowEdgeMargin(),
                        settings.contactShadowMipStepScale(),
                        0.0f);
        RhiStorageBuffer data = data();
        data.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant contact shadow trace",
                pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(4, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, depth, nearest),
                        new SampledTextureBinding(1, pyramid, nearest),
                        new SampledTextureBinding(2, geometry, nearest)
                ),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Contact shadow source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-contact-shadow", SHADER, LAYOUT
            ));
        }
        return pipeline;
    }

    private RhiStorageBuffer data() {
        if (owner == null) throw new IllegalStateException("Contact shadow source has no RHI owner");
        if (data == null) {
            data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-contact-shadow-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return data;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (data != null) {
            try { data.close(); } catch (Throwable ignored) { }
            data = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static Vector3f transformDirection(Matrix4f matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z,
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z,
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z
        );
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static boolean isVulkan(DeferredPassContext context) {
        String backendName = context.rhi().capabilities().backendName();
        return backendName != null && backendName.toLowerCase(Locale.ROOT).contains("vulkan");
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
