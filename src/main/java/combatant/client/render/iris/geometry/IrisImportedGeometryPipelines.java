/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Generic Iris submission pipelines. Shaderpack programs are selected through the Iris API. */
public final class IrisImportedGeometryPipelines {
    public static final RenderPipeline GBUFFER_CULL = pipeline("iris_imported_geometry_cull", true, false);
    public static final RenderPipeline GBUFFER_DOUBLE_SIDED = pipeline("iris_imported_geometry_double_sided", false, false);
    public static final RenderPipeline SHADOW_CULL = pipeline("iris_imported_shadow_cull", true, true);
    public static final RenderPipeline SHADOW_DOUBLE_SIDED = pipeline("iris_imported_shadow_double_sided", false, true);
    public static final RenderPipeline TRANSLUCENT_CULL = translucent("iris_imported_translucent_cull", true);
    public static final RenderPipeline TRANSLUCENT_DOUBLE_SIDED = translucent("iris_imported_translucent_double_sided", false);

    private IrisImportedGeometryPipelines() {}

    public static boolean owns(RenderPipeline pipeline) {
        return pipeline == GBUFFER_CULL || pipeline == GBUFFER_DOUBLE_SIDED
                || pipeline == SHADOW_CULL || pipeline == SHADOW_DOUBLE_SIDED
                || pipeline == TRANSLUCENT_CULL || pipeline == TRANSLUCENT_DOUBLE_SIDED;
    }

    private static RenderPipeline pipeline(String path, boolean cull, boolean shadow) {
        RenderPipeline pipeline = entityBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(IrisVertexFormats.ENTITY, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
                .withDepthTestFunction(shadow
                        ? DepthTestFunction.LEQUAL_DEPTH_TEST
                        : DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withCull(cull)
                .build();
        return CombatantRenderPipelines.registerAddonPipeline(pipeline);
    }

    private static RenderPipeline translucent(String path, boolean cull) {
        RenderPipeline pipeline = entityBuilder()
                .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/" + path))
                .withDomain(PipelineDomain.WORLD)
                .withVertexFormat(IrisVertexFormats.ENTITY, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.withDefaultNamespace("core/entity"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/entity"))
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(cull)
                .build();
        return CombatantRenderPipelines.registerAddonPipeline(pipeline);
    }

    private static ExtendedRenderPipelineBuilder entityBuilder() {
        return new ExtendedRenderPipelineBuilder()
                .withExternalBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withExternalBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withExternalBindGroupLayout(BindGroupLayouts.FOG)
                .withExternalBindGroupLayout(BindGroupLayouts.LIGHTING)
                .withExternalBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                .withExternalSampler("Sampler0")
                .withExternalSampler("Sampler1")
                .withExternalSampler("Sampler2");
    }
}
