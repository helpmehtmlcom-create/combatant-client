/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.asset.gltf.GltfAssetRepository;
import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.geometry.GltfCompiledPrimitive;
import combatant.client.render.engine.asset.gltf.geometry.GltfTriangleIndices;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.asset.gltf.model.GltfPrimitive;
import combatant.client.render.engine.asset.gltf.render.ImportedAssetCompatibilityRenderer;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstanceAsset;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstances;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.material.MaterialTextureSemantic;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import combatant.client.runtime.CombatantBuild;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Dev acceptance harness for imported geometry/material/texture residency and shared scene culling.
 * Shaderpack-off rendering uses the generic compatibility material; pack-owned shading is tested by
 * the later geometry-adapter slice rather than faked here.
 */
@ModuleInfo(id = "gltfassettest", displayName = "glTF Asset Test", category = ModuleCategory.MISC)
public final class GltfAssetTest extends Module {
    private final StringValue assetLocation = text(
            "gltfTestAsset", "asset", "combatant:models/dev/textured_pbr_cube.gltf");
    private final BooleanValue invalidateBeforeLoad = bool(
            "gltfTestInvalidate", "invalidate_cache", false);
    private final NumberValue<Double> distance = num(
            "gltfTestDistance", "distance", 4.0, 1.0, 32.0);
    private final NumberValue<Double> scale = num(
            "gltfTestScale", "scale", 1.0, 0.01, 20.0);
    private final BooleanValue animateRotation = bool(
            "gltfTestRotate", "animate_rotation", true);

    private SceneAssetInstance<GltfSceneInstanceAsset> instance;
    private Vec3 anchor = Vec3.ZERO;
    private float angle;

    public GltfAssetTest() {
        if (!CombatantBuild.isDevelopmentBuild()) {
            throw new IllegalStateException("GltfAssetTest requires a Combatant dev build");
        }
    }

    @Override
    public void onEnable() {
        closeInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.getResourceManager() == null || mc.gameRenderer == null) {
            CommandOutput.error("glTF test: world/resource manager is unavailable");
            return;
        }

        Identifier id = Identifier.tryParse(assetLocation.get().trim());
        if (id == null) {
            CommandOutput.error("glTF test: invalid resource id: " + assetLocation.get());
            return;
        }

        GltfAssetRepository repository = GltfAssetRepository.global();
        if (invalidateBeforeLoad.get()) repository.invalidateAll();

        try {
            GltfRuntimeAsset runtime = repository.load(mc.getResourceManager(), id);
            Diagnostics diagnostics = inspect(runtime);
            Vec3 camera = mc.gameRenderer.mainCamera().position();
            Vec3 forward = Vec3.directionFromRotation(
                    mc.gameRenderer.mainCamera().xRot(), mc.gameRenderer.mainCamera().yRot()).normalize();
            anchor = camera.add(forward.scale(distance.get()));
            angle = 0.0f;
            instance = GltfSceneInstances.spawnDefault(
                    CombatantRenderSystem.sceneInstances(),
                    runtime,
                    transform(),
                    new SceneLodProfile(new float[0], 0.10f)
            );

            String message = "glTF test spawned " + id
                    + " | scenes=" + runtime.asset().scenes().size()
                    + " nodes=" + runtime.asset().nodes().size()
                    + " meshes=" + runtime.asset().meshes().size()
                    + " materials=" + runtime.materials().size()
                    + " textures=" + runtime.asset().textures().size()
                    + " surfacePrimitives=" + diagnostics.surfacePrimitives
                    + " triangles=" + diagnostics.triangles
                    + " generatedNormals=" + diagnostics.generatedNormals
                    + " generatedTangents=" + diagnostics.generatedTangents
                    + " PBR[n=" + diagnostics.normalMaterials
                    + ",mr=" + diagnostics.metallicRoughnessMaterials
                    + ",e=" + diagnostics.emissiveMaterials + "]";
            CommandOutput.success(message);
            DebugLog.info(message);
        } catch (IOException | RuntimeException exception) {
            closeInstance();
            CommandOutput.error("glTF test failed for " + id + ": " + concise(exception));
            DebugLog.error("glTF dev asset test failed for {}", id, exception);
        }
    }

    @Override
    public void onDisable() {
        closeInstance();
    }

    @Override
    public void onFrame(float tickDelta) {
        if (instance == null || instance.isClosed() || !animateRotation.get()) return;
        angle += Math.max(0.0f, tickDelta) * 0.025f;
        instance.setWorldTransform(transform());
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.BEFORE_TRANSLUCENT;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        ImportedAssetCompatibilityRenderer.renderPrimary();
    }

    private Matrix4f transform() {
        float s = scale.get().floatValue();
        return new Matrix4f()
                .translation((float) anchor.x, (float) anchor.y, (float) anchor.z)
                .rotateY(angle)
                .scale(s);
    }

    private void closeInstance() {
        if (instance != null) {
            try { instance.close(); } catch (Throwable ignored) { }
            instance = null;
        }
    }

    private static Diagnostics inspect(GltfRuntimeAsset runtime) {
        int surfacePrimitives = 0;
        int triangles = 0;
        int generatedNormals = 0;
        int generatedTangents = 0;

        for (int meshIndex = 0; meshIndex < runtime.asset().meshes().size(); meshIndex++) {
            GltfMesh mesh = runtime.asset().meshes().get(meshIndex);
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                GltfPrimitive primitive = mesh.primitives().get(primitiveIndex);
                if (!GltfTriangleIndices.supports(primitive.mode())) continue;
                GltfCompiledPrimitive compiled = runtime.compilePrimitive(meshIndex, primitiveIndex);
                surfacePrimitives++;
                triangles += compiled.triangleCount();
                if (compiled.generatedNormals()) generatedNormals++;
                if (compiled.generatedTangents()) generatedTangents++;
            }
        }

        int normalMaterials = 0;
        int metallicRoughnessMaterials = 0;
        int emissiveMaterials = 0;
        for (GltfMaterialBinding material : runtime.materials()) {
            if (material.surface().textures().has(MaterialTextureSemantic.NORMAL)) normalMaterials++;
            if (material.surface().textures().has(MaterialTextureSemantic.METALLIC_ROUGHNESS)) metallicRoughnessMaterials++;
            if (material.surface().textures().has(MaterialTextureSemantic.EMISSIVE)) emissiveMaterials++;
        }
        return new Diagnostics(surfacePrimitives, triangles, generatedNormals, generatedTangents,
                normalMaterials, metallicRoughnessMaterials, emissiveMaterials);
    }

    private static String concise(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record Diagnostics(int surfacePrimitives, int triangles, int generatedNormals,
                               int generatedTangents, int normalMaterials,
                               int metallicRoughnessMaterials, int emissiveMaterials) {}
}
