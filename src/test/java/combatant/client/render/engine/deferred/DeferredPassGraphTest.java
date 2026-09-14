/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import combatant.client.render.engine.framegraph.CompiledFrameGraph;
import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeferredPassGraphTest {
    @Test
    void coreGraphOrdersGeometryBeforeLightingAndForwardStages() {
        DeferredPassGraph graph = new DeferredPassGraph();
        CompiledFrameGraph compiled = graph.compile();
        java.util.List<String> labels = compiled.orderedPasses().stream()
                .map(pass -> pass.label())
                .toList();
        java.util.List<String> externallyDrivenStages = java.util.List.of(
                "world.geometry.opaque",
                "world.geometry.cutout",
                "world.lighting.neutral",
                "world.forward.opaque",
                "world.translucency.forward",
                "world.postprocess.external"
        );

        assertEquals(
                externallyDrivenStages,
                labels.stream().filter(externallyDrivenStages::contains).toList()
        );
        assertTrue(compiled.dependencies().stream().anyMatch(dependency ->
                dependency.hazard() == CompiledFrameGraph.Hazard.READ_AFTER_WRITE
                        && dependency.resource().equals(DeferredResource.GBUFFER_SURFACE.key())));
    }

    @Test
    void extensionPassParticipatesInHazardCompilation() throws Exception {
        DeferredPassGraph graph = new DeferredPassGraph();
        AutoCloseable registration = graph.register(DeferredPassSpec
                .builder("world.ao.compute", DeferredStage.AMBIENT_OCCLUSION)
                .read(DeferredResource.GBUFFER_GEOMETRY)
                .write(DeferredResource.AMBIENT_OCCLUSION)
                .requires(RhiShaderStage.COMPUTE)
                .execute(context -> { })
                .build());

        assertTrue(graph.compile().orderedPasses().stream()
                .anyMatch(pass -> pass.label().equals("world.ao.compute")));
        registration.close();
        assertTrue(graph.compile().orderedPasses().stream()
                .noneMatch(pass -> pass.label().equals("world.ao.compute")));
    }

    @Test
    void rejectsTransientReadWithoutProducer() {
        DeferredPassGraph graph = new DeferredPassGraph();
        graph.register(DeferredPassSpec.builder("world.invalid", DeferredStage.PRE_GEOMETRY_COMPUTE)
                .read(DeferredResource.DEPTH_PYRAMID)
                .execute(context -> { })
                .build());

        assertThrows(IllegalStateException.class, graph::compile);
    }

    @Test
    void builderMergesReadAndWriteIntoReadWrite() {
        DeferredPassSpec spec = DeferredPassSpec.builder("world.history", DeferredStage.TEMPORAL_RESOLVE)
                .read(DeferredResource.HISTORY_COLOR)
                .write(DeferredResource.HISTORY_COLOR)
                .execute(context -> { })
                .build();

        assertEquals(1, spec.resources().size());
        assertEquals(FrameGraphAccess.READ_WRITE, spec.resources().getFirst().access());
    }

    @Test
    void standardResourcesDeclareStablePhysicalRequirements() {
        assertEquals(GpuFormat.RG16_FLOAT, DeferredResource.VELOCITY.textureSpec().format());
        assertTrue(DeferredResource.DEPTH_PYRAMID.textureSpec().mipChain());
        assertTrue(DeferredResource.REFLECTION_COLOR.textureSpec().storageImage());
        assertEquals(960, DeferredResource.REFLECTION_COLOR.textureSpec().resolution().width(1920));
        assertEquals(540, DeferredResource.REFLECTION_COLOR.textureSpec().resolution().height(1080));
    }

    @Test
    void storageImageMipContractRejectsImpossibleChains() {
        StorageImageDescriptor descriptor = new StorageImageDescriptor(
                "depth-pyramid", 1920, 1080, GpuFormat.R32_FLOAT,
                StorageAccess.READ_WRITE, true, false, 11
        );

        assertEquals(11, descriptor.mipLevels());
        assertThrows(IllegalArgumentException.class, () -> new StorageImageDescriptor(
                "invalid", 16, 16, GpuFormat.R32_FLOAT,
                StorageAccess.READ_WRITE, true, false, 6
        ));
    }
}
