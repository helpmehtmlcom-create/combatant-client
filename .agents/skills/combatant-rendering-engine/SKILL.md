---
name: combatant-rendering-engine
description: >-
  Deep dive and development guide for Combatant Client's custom rendering engine, RHI
  (OpenGL & Vulkan), compute shaders, deferred lighting, UI pass batching, and MSDF fonts.
  Use when working on graphics, shaders, materials, postprocessing, or Iris shaderpack compatibility.
---

# Combatant Client Rendering Engine & Shader Architecture

This skill provides a comprehensive technical guide to the low-level graphics subsystems of **Combatant Client**.

---

## 1. Render Hardware Interface (RHI) Architecture

Combatant decouples rendering logic from the graphics driver using an abstraction layer under `src/main/java/combatant/client/render/engine/rhi/`:

```text
render/engine/rhi/
├── backend/
│   ├── gl/             # OpenGL 4.5+ backend
│   │   ├── GlAdvancedShaderBackend.java      # Pipeline state, program linking, UBO/SSBO
│   │   ├── GlNativeStateTracker.java         # GL capability caching & redundant bind elision
│   │   ├── GlValidation.java                 # Debug checks & driver leak detection
│   │   └── clip/GlStencilShapeClipBackend.java # Stenciled arbitrary rounded clipping
│   └── vulkan/         # Vulkan backend
│       ├── VulkanAdvancedShaderBackend.java  # Descriptor sets, pipeline layouts, sync
│       ├── VulkanStorageBuffer.java          # Host-visible / device-local SSBOs
│       └── clip/VulkanShapeClipBackend.java   # Vulkan stencil & dynamic scissor bounds
├── resource/
│   ├── FramebufferPool.java                  # Recycling transient render targets by spec
│   ├── TexturePool.java                      # Temporary G-Buffer texture reuse
│   └── TransientTargetDescriptor.java        # Size, format, sample count, mip levels
├── shader/
│   ├── ComputeDispatchCommand.java           # Dispatches compute shaders (X, Y, Z workgroups)
│   ├── RhiResourceBarrier.java               # Memory barriers (SSBO, texture, UAV)
│   └── Std430Writer.java                     # Memory-aligned buffer packing for uniforms/SSBOs
└── upload/
    └── Blaze3dDynamicMeshBackend.java        # High-throughput vertex data streaming
```

### Backend Selection
- **OpenGL:** Default on Fabric with Sodium (`SodiumGlBackend`). Fully supports both custom compute shaders and Iris shaderpacks.
- **Vulkan:** Experimental high-efficiency pipeline when running on VulkanMod/Vulkan backends. Note: Iris shaderpack patching is disabled on Vulkan due to upstream Iris limitations.

---

## 2. Deferred Rendering Pipeline (`render/engine/deferred/`)

The deferred pipeline replaces vanilla forward shading with multi-stage compute and raster passes:

### Primary Stages & Shaders (`assets/combatant/shaders/deferred/`)
1. **G-Buffer Geometry Publishing:**
   - `deferred_opaque_publish.frag` / `deferred_terrain_publish.frag`: Renders albedo, roughness, metallic, emissive, and normal vectors into MRTs (Multiple Render Targets).
2. **Atmosphere & Sky Simulation:**
   - `atmosphere_transmittance.comp`: Precomputes optical depth through atmosphere.
   - `atmosphere_multiscatter.comp`: Multi-scattering approximation for realistic sky glow.
   - `atmosphere_sky_radiance.comp`: Rayleigh and Mie scattering for time of day.
   - `atmosphere_aerial_perspective.comp`: Volumetric distance haze.
3. **Volumetric Cloud Simulation:**
   - `cloud_render.comp`: Raymarched volumetric 3D noise cloud fields.
   - `cloud_temporal.comp`: Temporal anti-aliasing and reprojection to eliminate noise.
   - `cloud_shadow_map.comp` & `cloud_shadow_resolve.comp`: Ground shadows cast by clouds.
4. **Dynamic & Colored Block Lighting:**
   - `block_light_seed.comp`: Injects colored light sources into voxel 3D grid.
   - `block_light_propagate.comp`: Flood-fills photon bounce through chunk voxels.
   - `local_light_cull.comp`: Tile-based light culling against frustum.
   - `local_light_shade.comp`: Evaluates point/spot lights per pixel.
5. **Screen Space Reflections & Indirect Illumination:**
   - `reflection_trace.comp`: Raymarches depth buffer for specular reflections.
   - `reflection_denoise.comp`: Spatio-temporal filter removing raymarch artifacts.
   - `reflection_composite.comp`: Blends diffuse and specular reflections into scene.
6. **Ambient Occlusion & Shadow Resolve:**
   - `ambient_occlusion.comp`: High-speed Horizon-Based Ambient Occlusion (HBAO).
   - `shadow_resolve.comp`: Soft shadow cascade filtering (PCSS/CSM).
   - `water_surface.frag`: Specular water rendering with refraction and ripples.

---

## 3. UI Pass Compiler & Liquid Glass Rendering

ClickGUI panels and HUD surfaces use a compiled batching pipeline to achieve blur, rounded borders, and glass effects with zero frame stutter:

### Pipeline Flow
```text
UiNode Tree (JS/Java)
      │
      ▼
UiPassCompiler.java
      │ Compiles into draw batches (OrderedUiBatcher.java)
      ▼
UiBatchPlan.java
      │ Identifies required backdrop blurs & clips
      ├──> SeparableMaskBlurComputeBackend (Computes blurred backdrop texture)
      └──> UiPrimitiveRenderer (Draws rounded boxes, gradients, borders)
            │
            ▼
Shaders (ui_liquid_glass_batch.frag, ui_module_category_surface_batch.frag)
```

### Key UI Shader Files (`assets/combatant/shaders/`)
- `ui_liquid_glass_batch.frag`: Liquid glass material shader. Evaluates frosted glass blur, inner refractions, chromatic border highlights, and surface sheen.
- `ui_module_category_surface_batch.frag`: Category cards with reactive gradient hover crystal highlights (`include/ui_module_hover_crystal.glsl`).
- `mask_blur.comp`: Fast two-pass separable Gaussian/box blur running on compute units.
- `orbiz_ring_batch.frag`: Dynamic animated rings for indicators and loading rings.

---

## 4. Multi-Channel Signed Distance Field (MSDF) Text System

Combatant renders crisp, resolution-independent typography using MSDF glyph atlases:

### Architecture (`render/engine/text/`)
- `TextRenderSystem.java`: Manages vertex allocation, text layout, and draw command batching.
- `MsdfFont.java`: Samples MSDF textures using a dual-threshold screen-space derivative filter:
  $$\text{distance} = \text{median}(R, G, B) - 0.5$$
- `BuiltinFontCatalog.java`: Pre-registered fonts:
  - `MatrixSans`: Primary UI font (clean geometric sans-serif).
  - `Onest`: Modern legible body font.
  - `Iosevka`: Monospace font used for code, coordinates, coordinates HUD, and statistics.
  - `Noto` / `Noto-CJK`: Broad Unicode glyph coverage.

### Generating Custom MSDF Fonts & SVGs
To regenerate or add new font glyphs or SVG icons, run the PowerShell tools in `tools/msdf/`:

```powershell
# Generate MSDF font atlas from TTF/OTF
cd tools/msdf
.\GenerateFontMsdf.ps1 -FontPath "path/to/font.ttf" -OutputName "custom_font"

# Generate MSDF texture for SVGs
.\GenerateSvgMsdf.ps1
```

Generated assets are placed into `src/main/resources/assets/combatant/msdf/`.

---

## 5. Iris Shaderpack Compatibility (`SHADERPACK_PATCHES.md`)

Combatant includes an automated patch engine (`combatant.client.render.iris.patch.ShaderPatchEngine`) that dynamically adapts popular Iris shaderpacks to work with Combatant's custom entity and UI rendering:

- **Supported Shaderpacks:** Targeted profiles exist for Complementary, BSL, and Photon.
- **Manifest Location:** `src/main/resources/assets/combatant/shaders/iris/`.
- **Validation:** When testing shaderpack changes, run `@iris` in chat to inspect shader compilation logs and stage status.
- **Limitation:** Shaderpack support requires the **OpenGL** backend. VulkanMod does not support Iris shaderpacks.
