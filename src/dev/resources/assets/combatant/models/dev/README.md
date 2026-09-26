# Imported-model dev fixtures

`box_vertex_colors.glb` is the Khronos glTF Sample Assets **BoxVertexColors** model.
It is used only by the dev `glTF Asset Test` module to smoke-test GLB decoding, bounds,
vertex colors, material construction, topology normalization, and generated tangent policy.

- Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoxVertexColors
- License: CC0 / public domain
- SHA-256: `9c48227f33b0ba2fbcf23b98ebf60d1c8ae0c6e6c5281e0aa3cc58affee10382`

For the full PBR + normal + emissive + Photon integration test, use Khronos `BoomBox.glb`
(CC0) as `combatant:models/dev/boombox.glb` and point the dev module's `asset` setting to it.
The large BoomBox payload is intentionally not shipped in the production resources.
