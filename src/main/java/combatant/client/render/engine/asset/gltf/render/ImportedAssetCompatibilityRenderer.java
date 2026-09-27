/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.engine.uniform.impl.AssetCompatibilityMaterialUniforms;
import combatant.client.render.iris.IrisRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * Shaderpack-off correctness path for imported static assets.
 *
 * <p>This is deliberately not the Photon renderer. It proves geometry/material/texture residency,
 * depth, transforms and visibility before pack-specific G-buffer insertion exists. When Iris owns
 * the world, this path stays off instead of pretending to reproduce the active shaderpack.</p>
 */
public final class ImportedAssetCompatibilityRenderer {
    private static volatile ImportedAssetCompatibilityRenderStats stats = ImportedAssetCompatibilityRenderStats.EMPTY;

    private ImportedAssetCompatibilityRenderer() {}

    public static void renderPrimary() {
        if (IrisRuntime.isShaderpackRendererActive()) return;
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc != null ? mc.gameRenderer : null;
        if (gameRenderer == null || gameRenderer.mainRenderTarget() == null) return;

        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null) return;
        var target = gameRenderer.mainRenderTarget();
        var color = target.getColorTextureView();
        var depth = target.getDepthTextureView();
        if (color == null || depth == null) return;

        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        int[] counters = new int[4]; // visible, submitted, skinned, translucent
        Vec3 camera = view.cameraPosition();

        CombatantRenderSystem.sceneInstances().visitVisible(
                view,
                SceneVisibilityMode.SECTION_AND_FRUSTUM,
                (instance, state) -> {
                    if (!(instance.asset() instanceof GltfSceneInstanceAsset source)) return true;
                    counters[0]++;
                    submitInstance(commands, instance, source, camera, color, depth, counters);
                    return true;
                }
        );

        if (!commands.isEmpty()) CombatantRenderSystem.rhi().drawMeshes(commands);
        stats = new ImportedAssetCompatibilityRenderStats(view.frameId(), counters[0], counters[1], counters[2], counters[3]);
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
            RenderPipeline pipeline = (!material.doubleSided() && !mirrored)
                    ? CombatantRenderPipelines.ASSET_COMPATIBILITY_CULL
                    : CombatantRenderPipelines.ASSET_COMPATIBILITY_DOUBLE_SIDED;

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
