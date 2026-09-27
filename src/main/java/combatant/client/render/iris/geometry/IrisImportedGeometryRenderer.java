/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.AlphaMode;
import combatant.client.render.engine.asset.gltf.runtime.GltfPrimitivePlacement;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstanceAsset;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneViewType;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisRuntimeSnapshot;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;

/** Generic Iris geometry submission; shaderpack interpretation is supplied only by patch manifests. */
public final class IrisImportedGeometryRenderer {
    public static final String GBUFFER_CAPABILITY = "custom_geometry_gbuffer";
    public static final String TRANSLUCENT_CAPABILITY = "custom_geometry_translucent";
    public static final String SHADOW_CAPABILITY = "custom_geometry_shadows";

    private IrisImportedGeometryRenderer() {}

    public static boolean ownsPipeline(RenderPipeline pipeline) {
        return IrisImportedGeometryPipelines.owns(pipeline);
    }

    public static void renderPrimary() {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, GBUFFER_CAPABILITY) || iris.renderingShadowPass()) return;
        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null || view.type() != SceneViewType.PRIMARY) return;
        submit(view, SceneVisibilityMode.SECTION_AND_FRUSTUM, Route.GBUFFER);
    }

    public static void renderTranslucent() {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, TRANSLUCENT_CAPABILITY) || iris.renderingShadowPass()) return;
        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null || view.type() != SceneViewType.PRIMARY) return;
        submit(view, SceneVisibilityMode.SECTION_AND_FRUSTUM, Route.TRANSLUCENT);
    }

    public static void renderShadow(Matrix4f modelView, Matrix4f projection,
                                    double cameraX, double cameraY, double cameraZ, float tickDelta) {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, SHADOW_CAPABILITY)) return;
        long frameId = CombatantRenderSystem.currentContext() != null
                ? CombatantRenderSystem.currentContext().frameId() : 0L;
        int resolution = Math.max(1, ShadowRenderer.RESOLUTION);
        SceneViewContext view = SceneViewContext.secondary(
                frameId, SceneViewType.SHADOW, new Vec3(cameraX, cameraY, cameraZ),
                modelView, projection, resolution, resolution, false);
        submit(view, SceneVisibilityMode.FRUSTUM_ONLY, Route.SHADOW);
    }

    public static void releaseBackend(CombatantRhi rhi) {
        IrisImportedGeometryResidency.releaseBackend(rhi);
    }

    private static void submit(SceneViewContext view, SceneVisibilityMode visibility, Route route) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc != null ? mc.gameRenderer : null;
        if (renderer == null || renderer.mainRenderTarget() == null) return;
        var target = renderer.mainRenderTarget();
        var color = target.getColorTextureView();
        var depth = target.getDepthTextureView();
        if (color == null || depth == null) return;
        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        ArrayList<VisibleInstance> visible = new ArrayList<>();
        CombatantRenderSystem.sceneInstances().visitVisible(view, visibility, (instance, state) -> {
            if (instance.asset() instanceof GltfSceneInstanceAsset source) {
                visible.add(new VisibleInstance(instance, source, distanceSquared(instance, view.cameraPosition())));
            }
            return true;
        });
        if (route == Route.TRANSLUCENT) {
            visible.sort(Comparator.comparingDouble(VisibleInstance::distanceSquared).reversed());
        }
        for (VisibleInstance entry : visible) {
            submitInstance(commands, entry.instance(), entry.source(), view.cameraPosition(), color, depth, route);
        }
        Matrix4f worldView = route == Route.SHADOW ? null : CombatantWorldMatrices.positionMatrix();
        boolean installedWorldView = false;
        if (!commands.isEmpty()) {
            /*
             * The imported command transform is camera-relative translation * object transform.
             * Iris' ExtendedShader reads the current RenderSystem model-view and multiplies the
             * per-command transform into it. This callback runs from Iris beginTranslucents(), not
             * from Combatant's ordinary WORLD scope, so the global model-view is not guaranteed to
             * contain the captured camera rotation. Without that base view, the same mesh that is
             * visible on the compatibility path is projected in the wrong camera space.
             *
             * Keep Iris authoritative for the actual shaderpack framebuffer. safeToMultiply only
             * prevents the temporary Blaze3D RenderPass descriptor from rebinding mainRenderTarget;
             * ExtendedShader then binds its own before/after-translucent framebuffer as usual.
             */
            var modelView = RenderSystem.getModelViewStack();
            boolean previousSafeToMultiply = ImmediateState.safeToMultiply;
            boolean previousRendering3D = RenderState.rendering3D;
            modelView.pushMatrix();
            try {
                if (worldView != null) {
                    modelView.identity();
                    modelView.mul(worldView);
                    installedWorldView = true;
                }
                RenderState.rendering3D = true;
                ImmediateState.safeToMultiply = true;
                CombatantRenderSystem.rhi().drawMeshes(commands);
            } finally {
                ImmediateState.safeToMultiply = previousSafeToMultiply;
                RenderState.rendering3D = previousRendering3D;
                modelView.popMatrix();
            }
        }
        DebugLog.infoOnChange(
                "iris.imported.geometry.submit." + route.name().toLowerCase(java.util.Locale.ROOT),
                route + "|" + visible.size() + "|" + commands.size() + "|" + installedWorldView,
                "[IrisGeometry] route=%s visible=%d commands=%d worldView=%s rendering3D=%s",
                route, visible.size(), commands.size(), installedWorldView, RenderState.rendering3D
        );
    }

    private static void submitInstance(ArrayList<RhiDrawCommand> commands,
                                       SceneAssetInstance<?> instance,
                                       GltfSceneInstanceAsset source,
                                       Vec3 camera,
                                       com.mojang.blaze3d.textures.GpuTextureView color,
                                       com.mojang.blaze3d.textures.GpuTextureView depth,
                                       Route route) {
        IrisImportedGeometryResidency residency = IrisImportedGeometryResidency.acquire(
                CombatantRenderSystem.rhi(), source.asset());
        Matrix4f cameraRelative = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z)
                .mul(instance.currentWorldTransform());
        for (GltfPrimitivePlacement placement : source.layout().primitives()) {
            IrisImportedGeometryResidency.Primitive primitive =
                    residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) continue;
            GltfMaterialBinding material = source.asset().material(primitive.material());
            boolean blend = material.alphaMode() == AlphaMode.BLEND;
            if (route == Route.TRANSLUCENT ? !blend : blend) continue;
            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            RenderPipeline pipeline = switch (route) {
                case GBUFFER -> cull ? IrisImportedGeometryPipelines.GBUFFER_CULL
                        : IrisImportedGeometryPipelines.GBUFFER_DOUBLE_SIDED;
                case TRANSLUCENT -> cull ? IrisImportedGeometryPipelines.TRANSLUCENT_CULL
                        : IrisImportedGeometryPipelines.TRANSLUCENT_DOUBLE_SIDED;
                case SHADOW -> cull ? IrisImportedGeometryPipelines.SHADOW_CULL
                        : IrisImportedGeometryPipelines.SHADOW_DOUBLE_SIDED;
            };
            Matrix4f model = new Matrix4f(cameraRelative).mul(placement.localToAsset());
            IrisCanonicalMaterialAtlas atlas = residency.material(primitive.material());
            commands.add(RhiDrawCommand.builder("Iris imported asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .sampler("Sampler0", atlas.view(), atlas.sampler())
                    .sampler("Sampler1", atlas.view(), atlas.sampler())
                    .sampler("Sampler2", atlas.view(), atlas.sampler())
                    .build());
        }
    }

    private static double distanceSquared(SceneAssetInstance<?> instance, Vec3 camera) {
        var bounds = instance.worldBounds();
        double x = (bounds.minX + bounds.maxX) * 0.5 - camera.x;
        double y = (bounds.minY + bounds.maxY) * 0.5 - camera.y;
        double z = (bounds.minZ + bounds.maxZ) * 0.5 - camera.z;
        return x * x + y * y + z * z;
    }

    private static boolean active(IrisRuntimeSnapshot snapshot, String capability) {
        if (!snapshot.modLoaded() || !snapshot.apiAvailable() || !snapshot.shadersEnabled()
                || !snapshot.shaderpackInUse() || !snapshot.patchFeatures().contains(capability)) return false;
        return ShaderPatchEngine.applicationState(snapshot.patchManifestId()).preflightAccepted();
    }

    private enum Route {
        GBUFFER,
        TRANSLUCENT,
        SHADOW
    }

    private record VisibleInstance(SceneAssetInstance<?> instance,
                                   GltfSceneInstanceAsset source,
                                   double distanceSquared) {}
}
