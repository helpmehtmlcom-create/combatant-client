/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuAssetResidency;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuPrimitive;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuResidencyManager;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuTexture;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.AlphaMode;
import combatant.client.render.engine.asset.gltf.runtime.GltfPrimitivePlacement;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstanceAsset;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.engine.uniform.impl.AssetCompatibilityMaterialUniforms;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * Shaderpack-off world-entity path for imported static assets.
 *
 * <p>This is deliberately not the Photon renderer. It proves geometry/material/texture residency,
 * depth, transforms and visibility before pack-specific G-buffer insertion exists. HAND instances
 * are intentionally excluded: first-person replacements require a dedicated hand submission.
 * When Iris owns the world, this path stays off instead of pretending to reproduce the active
 * shaderpack.</p>
 */
public final class ImportedAssetCompatibilityRenderer {
    private static volatile ImportedAssetCompatibilityRenderStats stats = ImportedAssetCompatibilityRenderStats.EMPTY;

    private ImportedAssetCompatibilityRenderer() {}

    /** Submits opaque imported assets in vanilla's solid-entity section of the main world pass. */
    public static void renderWorldEntities(RenderTarget worldTarget) {
        if (IrisRuntime.isShaderpackRendererActive()) {
            diagnostic("shaderpack-active", 0, 0);
            return;
        }
        if (worldTarget == null) {
            diagnostic("no-world-target", 0, 0);
            return;
        }

        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null) {
            diagnostic("no-scene-view", 0, 0);
            return;
        }
        var color = worldTarget.getColorTextureView();
        var depth = worldTarget.getDepthTextureView();
        if (color == null || depth == null) {
            diagnostic("missing-world-color-or-depth", 0, 0);
            return;
        }

        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        int[] counters = new int[4]; // visible, submitted, skinned, translucent
        Vec3 camera = view.cameraPosition();

        CombatantRenderSystem.sceneInstances().visitVisible(
                view,
                SceneVisibilityMode.SECTION_AND_FRUSTUM,
                (instance, state) -> {
                    if (!(instance.asset() instanceof GltfSceneInstanceAsset source)) return true;
                    // First-person assets have their own hand/item submission and must never leak
                    // into the world entity layer.
                    if (instance.drawClass() == SceneDrawClass.HAND) return true;
                    counters[0]++;
                    submitInstance(commands, instance, source, camera, color, depth, counters);
                    return true;
                }
        );

        if (!commands.isEmpty()) CombatantRenderSystem.rhi().drawMeshes(commands);
        stats = new ImportedAssetCompatibilityRenderStats(view.frameId(), counters[0], counters[1], counters[2], counters[3]);
        diagnostic("draw", counters[0], counters[1]);
    }

    private static void diagnostic(String state, int visible, int submitted) {
        int registered = CombatantRenderSystem.sceneInstances().size();
        DebugLog.renderThreadOnChange(
                "gltf.compatibility.submit",
                state + "|" + registered + "|" + visible + "|" + submitted,
                "[GltfCompat] state=%s registered=%d visible=%d submitted=%d rendering3D=%s",
                state, registered, visible, submitted,
                combatant.client.render.engine.RenderState.rendering3D
        );
    }

    public static ImportedAssetCompatibilityRenderStats statsSnapshot() {
        return stats;
    }

    private static void submitInstance(ArrayList<RhiDrawCommand> commands,
                                       SceneAssetInstance<?> instance,
                                       GltfSceneInstanceAsset source,
                                       Vec3 camera,
                                       com.mojang.blaze3d.textures.GpuTextureView color,
                                       com.mojang.blaze3d.textures.GpuTextureView depth,
                                       int[] counters) {
        GltfGpuAssetResidency residency = GltfGpuResidencyManager.global().acquire(
                CombatantRenderSystem.rhi(), source.asset());
        Matrix4f cameraRelative = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z)
                .mul(instance.currentWorldTransform());

        for (GltfPrimitivePlacement placement : source.layout().primitives()) {
            GltfGpuPrimitive primitive = residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) {
                counters[2]++;
                continue;
            }
            GltfMaterialBinding material = source.asset().material(primitive.material());
            if (material.alphaMode() == AlphaMode.BLEND) {
                counters[3]++;
                continue;
            }

            Matrix4f model = new Matrix4f(cameraRelative).mul(placement.localToAsset());
            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            boolean noDepth = instance.drawClass() == SceneDrawClass.WORLD_OVERLAY;
            RenderPipeline pipeline = noDepth
                    ? (cull
                        ? CombatantRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_CULL
                        : CombatantRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED)
                    : (cull
                        ? CombatantRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_CULL
                        : CombatantRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_DOUBLE_SIDED);

            GltfGpuTexture base = residency.colorOrWhite(material.baseColorTexture());
            GltfGpuTexture mr = residency.dataOrWhite(material.metallicRoughnessTexture());
            GltfGpuTexture normal = residency.normalOrFlat(material.normalTexture());
            GltfGpuTexture ao = residency.dataOrWhite(material.occlusionTexture());
            GltfGpuTexture emissive = residency.emissiveOrBlack(material.emissiveTexture());

            RhiDrawCommand command = RhiDrawCommand.builder(
                            "Combatant imported asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .uniform("AssetMaterial", AssetCompatibilityMaterialUniforms.write(material))
                    .sampler("u_BaseColor", base.view(), base.sampler())
                    .sampler("u_MetallicRoughness", mr.view(), mr.sampler())
                    .sampler("u_Normal", normal.view(), normal.sampler())
                    .sampler("u_Occlusion", ao.view(), ao.sampler())
                    .sampler("u_Emissive", emissive.view(), emissive.sampler())
                    .build();
            commands.add(command);
            counters[1]++;
        }
    }
}
