/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.asset.gltf.GltfAssetRepository;
import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.geometry.GltfCompiledPrimitive;
import combatant.client.render.engine.asset.gltf.geometry.GltfTriangleIndices;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.asset.gltf.model.GltfPrimitive;
import combatant.client.render.engine.material.MaterialTextureSemantic;
import combatant.client.runtime.CombatantBuild;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;

/** Dev-only vertical probe for OBJ/glTF/GLB import and backend-neutral geometry preparation. */
@ModuleInfo(id = "gltfassettest", displayName = "glTF Asset Test", category = ModuleCategory.MISC)
public final class GltfAssetTest extends Module {
    private final StringValue assetLocation = text(
            "gltfTestAsset", "asset", "combatant:models/dev/box_vertex_colors.glb");
    private final BooleanValue invalidateBeforeLoad = bool(
            "gltfTestInvalidate", "invalidate_cache", true);

    public GltfAssetTest() {
        if (!CombatantBuild.isDevelopmentBuild()) {
            throw new IllegalStateException("GltfAssetTest requires a Combatant dev build");
        }
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            CommandOutput.error("glTF test: resource manager is unavailable");
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
            String message = "glTF test loaded " + id
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
            CommandOutput.error("glTF test failed for " + id + ": " + concise(exception));
            DebugLog.error("glTF dev asset test failed for {}", id, exception);
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
