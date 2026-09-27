# Imported-model dev fixtures

`box_vertex_colors.glb` is the Khronos glTF Sample Assets **BoxVertexColors** model.
It is used only by the dev `glTF Asset Test` module to smoke-test GLB decoding, bounds,
vertex colors, material construction, topology normalization, and generated tangent policy.

- Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoxVertexColors
- License: CC0 / public domain
- SHA-256: `9c48227f33b0ba2fbcf23b98ebf60d1c8ae0c6e6c5281e0aa3cc58affee10382`

`boombox.gltf` and its external buffer/textures are the Khronos glTF Sample Assets **BoomBox**
model. This is the default full PBR + normal + emissive + Photon integration fixture. Its original
centimetre-scale coordinates are preserved; `GltfAssetTest` defaults to scale `100` so it appears
at a useful Minecraft-world size. Resource file names were normalized to lowercase for Minecraft
identifier compatibility; model and image contents are otherwise the upstream glTF variant.

- Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoomBox
- License: CC0-1.0 / public domain
- Upstream payload SHA-256:
  - `boombox.bin`: `f6c61a95af3e0e2462a9ac01d1e681706149452e0191930e0591a81d50b5b944`
  - `boombox_base_color.png`: `099816a7afc5f6690494313ac8039806fd6d5b84179126a808b2678aaab3563a`
  - `boombox_normal.png`: `c9a7904e7f25246ac47f86c337cfd4ec8e103fff83e07d3af472e5c620ec6f27`
  - `boombox_occlusion_roughness_metallic.png`: `496704a4836ff364dc4441f41651edb25178498926c859933e33df42d6361412`
  - `boombox_emissive.png`: `e9970da7010591b73070151fe5039a158413499e38300d14106e367472c03b5b`

`textured_pbr_cube.gltf` is a deterministic Combatant-authored development fixture. It exercises
external glTF buffers/images, authored tangents, base-color sRGB, normal data, packed glTF
metallic/roughness channels, AO, emissive sRGB, mip generation, and imported texture residency.
The five 16x16 PNGs and binary vertex/index buffer are generated specifically for this repository
and are covered by Combatant's project license. The dev module uses this fixture by default so
texture/material bring-up does not depend on a large external sample asset.
