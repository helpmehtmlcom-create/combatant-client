# Shaderpack Patches

Combatant includes Iris shaderpack patch manifests for a small set of shaderpacks. These patches are used to integrate Combatant visual features with shaderpack pipelines without requiring users to edit shaderpack files manually.

Patch manifests are registered from:

```text
src/main/resources/assets/combatant/shaders/iris-patches/index.json
```

Patch files live under:

```text
src/main/resources/assets/combatant/shaders/iris-patches/
```

## Supported Patch Manifests

| Shaderpack | Manifest id | Targeted shaderpack version | Manifest verified Minecraft | Manifest verified Iris | Modrinth |
| --- | --- | --- | --- | --- | --- |
| Complementary Shaders - Reimagined | `complementary_reimagined.r5` | `r5.6.1` | `26.2` | `1.11.4+mc26.2` | https://modrinth.com/shader/complementary-reimagined/version/r5.6.1 |
| Photon Shaders | `photon.v1_3b` | `v1.3b` | `26.2` | `1.11.4+mc26.2` | https://modrinth.com/shader/photon-shader/version/v1.3b |

The client currently targets Minecraft 26.2. The manifest verification fields describe the shaderpack versions and loader environment the patch payloads were last structurally checked against. A shaderpack may advertise broader Minecraft compatibility on Modrinth than the specific manifest verification entry inside Combatant.

## Patched Features

Complementary Reimagined `r5.6.1` patches currently cover:

- fullbright;
- WorldTweaks fog;
- translucent SSR fog;
- underwater fog;
- shaderpack motion blur suppression.

Photon `v1.3b` patches currently cover:

- fullbright;
- WorldTweaks fog;
- underwater fog;
- shaderpack motion blur suppression;
- Combatant-owned TAA at the manifest-declared HDR temporal pass, including native temporal bypass and raster jitter;
- Combatant-owned world MSAA with resolve before single-sample shaderpack stages;
- non-destructive AA, Motion Blur, and DoF option ownership metadata.

Runtime integration is shaderpack-agnostic. Java code reads pass names, render-target mappings,
replacement capabilities, ownership anchors, and option IDs from the selected manifest. A new
shaderpack is supported by adding its manifest and shader patches; pack-specific buffer/program
names do not belong in the core Iris integration.

## How Patch Matching Works

Combatant first gates patch application by the active Iris shaderpack name, then checks structural probes against the loaded expanded GLSL.

This means:

- the shaderpack name must match the manifest's `packNameRegex`;
- the expected GLSL target paths must exist;
- patch markers prevent duplicate injection;
- `ownershipCritical` targets decide when a replacement may claim runtime ownership, while other
  preflight-verified targets may still compile lazily in Iris;
- newer shaderpack versions may work if their structure is still compatible, but they are not treated as verified until the manifest is updated.

## Validation

Use the Gradle validation task to compile bundled patch manifests against a shaderpack zip:

```powershell
.\gradlew.bat validateIrisPatches -Pshaderpack=<path-to-shaderpack.zip>
```

Run this when updating a patch manifest, changing patch payloads, or bumping the verified shaderpack version.

## Adding Or Updating A Patch

1. Add or update the manifest under `src/main/resources/assets/combatant/shaders/iris-patches/<shaderpack>/<version>/manifest.json`.
2. Add the manifest path to `src/main/resources/assets/combatant/shaders/iris-patches/index.json`.
3. Keep `verifiedShaderpackVersions`, `verifiedMinecraftVersions`, and `verifiedIrisVersions` aligned with the shaderpack zip actually tested.
4. Run `validateIrisPatches` against the target shaderpack zip.
5. Test in-game with Iris enabled.
