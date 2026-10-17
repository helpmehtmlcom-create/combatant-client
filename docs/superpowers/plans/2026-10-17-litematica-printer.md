# Litematica Source for SchematicBuilder: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SchematicBuilder can build the Litematica placement the player has selected, honouring Litematica's
placement and sub-region transforms and its layer range. Litematica stays optional.

**Architecture:**
- A pure `LitematicaSnapshot` turns placement and region data into our existing dense `Schematic` plus
  `SchematicPlacement`. The transforms are baked in and cells outside the layer range are cut out. This is
  unit-tested without Litematica.
- `LitematicaBridge` is the only class that imports `fi.dy.masa.*`. `LitematicaCompat` gates it behind
  `isModLoaded`.
- `SchematicState.setExternal` feeds the snapshot to the unchanged builder pipeline.

**Tech Stack:** Java 25, Fabric (Mojang-mapped MC 26.3), Gradle/Loom, JUnit 5, Litematica 0.29.1, MaLiLib 0.30.2.

**Spec:** `docs/superpowers/specs/2026-10-17-litematica-printer-design.md`

## Global Constraints

- Worktree `C:/Users/voidg/kv/printer263`, branch `lb/printer-263`. Use absolute paths for every edit. NEVER
  edit `C:/Users/voidg/Downloads/krypton/main/combatant-client`.
- Litematica mod id `litematica`; MaLiLib mod id `malilib`. Gradle: `maven.modrinth:litematica:0.29.1`,
  `maven.modrinth:malilib:0.30.2` (the Modrinth repo is already declared).
- The release jar must not bundle or require Litematica. The deps are `compileOnly`, and are added to the
  runtime only with `-PwithOptionalModRuntime=true`.
- No mixins into Litematica or MaLiLib.
- Only `combatant.client.compat.litematica.LitematicaBridge` may import `fi.dy.masa.*`.
- Every new setting and notification gets en_us and ru_ru keys, using the existing
  `setting.schematicbuilder.*` / `module.schematicbuilder.notify.*` naming.
- Commit messages must not contain "krypton".
- Deviation from the spec, recorded here: the layer range is applied when the snapshot is taken (cells outside
  it are not wanted, and Y is cropped), instead of through a `LayerMask` passed to `SchematicState`. A layer
  range change is part of the fingerprint, so it re-snapshots. This needs no changes to `SchematicDiff` or the
  builder's layer logic.
- Build after each task with `bash gradlew compileJava` plus that task's tests. Run the full `bash gradlew build`
  once, in Task 7.

---

### Task 1: Build dependencies

**Files:**
- Modify: `gradle.properties`: add `litematica_version=0.29.1` and `malilib_version=0.30.2` next to
  `viafabricplus_version`.
- Modify: `build.gradle`: add a block after the ViaFabricPlus block (around line 186).
- Modify: `src/main/resources/fabric.mod.json`: add an entry to `suggests`.

- [ ] **Step 1: Add the dependency block**

```groovy
    // Litematica (optional): SchematicBuilder can follow its selected placement. Never bundled.
    compileOnly "maven.modrinth:litematica:${project.litematica_version}"
    compileOnly "maven.modrinth:malilib:${project.malilib_version}"
    if (providers.gradleProperty("withOptionalModRuntime").map { it.toBoolean() }.orElse(false).get()) {
        runtimeOnly "maven.modrinth:litematica:${project.litematica_version}"
        runtimeOnly "maven.modrinth:malilib:${project.malilib_version}"
    }
```

- [ ] **Step 2: Add `"litematica": "*"` to the `suggests` object in fabric.mod.json**

- [ ] **Step 3: Verify that the classes resolve**

Run: `bash gradlew compileJava`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**: `git commit -am "Build: optional Litematica/MaLiLib compile dependency"`

---

### Task 2: Pure snapshot builder

**Files:**
- Create: `src/main/java/combatant/client/util/schematic/LitematicaSnapshot.java`
- Test: `src/test/java/combatant/client/util/schematic/LitematicaSnapshotTest.java`

**Interfaces:**
- Produces:
  - `LitematicaSnapshot.Region(BlockPos pos, Vec3i size, Rotation rotation, Mirror mirror, StateSource container, Vec3i containerSize)`
  - `LitematicaSnapshot.Slab(Direction.Axis axis, int min, int max)`
  - `LitematicaSnapshot.Result(Schematic schematic, SchematicPlacement placement)`
  - `static Result build(String name, String fileName, BlockPos origin, Rotation rotation, Mirror mirror, List<Region> regions, Slab slab)` returns `null` when nothing is left after the slab. It throws `IOException` when the result is over `LitematicReader.MAX_AXIS_SPAN` / `MAX_VOLUME`.
  - `static BlockPos transform(int x, int y, int z, Mirror mirror, Rotation rotation)`

- [ ] **Step 1: Write the failing tests**

```java
package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LitematicaSnapshotTest {
    private static BlockState stone, stairsNorth;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        stone = Blocks.STONE.defaultBlockState();
        stairsNorth = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
    }

    /** Container laid out along +x: x=0 stone, x=1 north stairs. */
    private static LitematicaSnapshot.Region row(BlockPos pos, Vec3i size, Rotation r, Mirror m) {
        return new LitematicaSnapshot.Region(pos, size, r, m,
                (x, y, z) -> x == 0 ? stone : stairsNorth, new Vec3i(2, 1, 1));
    }

    private static BlockState at(LitematicaSnapshot.Result res, int wx, int wy, int wz) {
        BlockPos o = res.placement().origin();
        return res.schematic().state(wx - o.getX(), wy - o.getY(), wz - o.getZ());
    }

    @Test
    void identityPlacementKeepsPositionsAndStates() throws IOException {
        var res = LitematicaSnapshot.build("t", "t.litematic", new BlockPos(10, 64, 10), Rotation.NONE, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)), null);
        assertEquals(new BlockPos(10, 64, 10), res.placement().origin());
        assertEquals(Rotation.NONE, res.placement().rotation());
        assertSame(stone, at(res, 10, 64, 10));
        assertSame(stairsNorth, at(res, 11, 64, 10));
        assertEquals(2, res.schematic().totalBlocks());
    }

    @Test
    void placementRotationMovesCellsAndRotatesStates() throws IOException {
        // CW90 maps local (x,z) -> (-z, x): x=1 lands at z=+1; stairs facing north turn to east.
        var res = LitematicaSnapshot.build("t", "t", new BlockPos(10, 64, 10), Rotation.CLOCKWISE_90, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)), null);
        assertEquals(1, res.schematic().sizeX());
        assertEquals(2, res.schematic().sizeZ());
        assertSame(stone, at(res, 10, 64, 10));
        assertEquals(Direction.EAST, at(res, 10, 64, 11).getValue(StairBlock.FACING));
    }

    @Test
    void placementMirrorFlipsXAndState() throws IOException {
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.FRONT_BACK,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)), null);
        assertSame(stone, at(res, 0, 0, 0));
        // FRONT_BACK mirrors x, so x=1 goes to x=-1. Stairs facing north are unchanged by FRONT_BACK.
        assertEquals(Direction.NORTH, at(res, -1, 0, 0).getValue(StairBlock.FACING));
    }

    @Test
    void negativeRegionSizeGrowsTowardsNegative() throws IOException {
        // size.x = -2: end = -1, minOff.x = -1, so container x=0 -> local -1 and x=1 -> local 0.
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
                List.of(row(new BlockPos(5, 0, 0), new Vec3i(-2, 1, 1), Rotation.NONE, Mirror.NONE)), null);
        assertSame(stone, at(res, 4, 0, 0));
        assertSame(stairsNorth, at(res, 5, 0, 0));
    }

    @Test
    void subMirrorAxisSwapsUnderQuarterTurnForStates() throws IOException {
        // Rp=CW90, Ms=FRONT_BACK -> state mirror becomes LEFT_RIGHT before rotate(CW90).
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.CLOCKWISE_90, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.FRONT_BACK)), null);
        BlockState expected = stairsNorth.mirror(Mirror.LEFT_RIGHT).rotate(Rotation.CLOCKWISE_90);
        // Position: T(T((1,0,0), NONE, CW90) = (0,0,1), FRONT_BACK, NONE) = (0,0,1).
        assertSame(expected, at(res, 0, 0, 1));
    }

    @Test
    void ySlabCropsAndDropsOutsideLayers() throws IOException {
        var tall = new LitematicaSnapshot.Region(BlockPos.ZERO, new Vec3i(1, 3, 1), Rotation.NONE, Mirror.NONE,
                (x, y, z) -> stone, new Vec3i(1, 3, 1));
        var res = LitematicaSnapshot.build("t", "t", new BlockPos(0, 64, 0), Rotation.NONE, Mirror.NONE,
                List.of(tall), new LitematicaSnapshot.Slab(Direction.Axis.Y, 65, 65));
        assertEquals(1, res.schematic().sizeY());
        assertEquals(65, res.placement().origin().getY());
        assertEquals(1, res.schematic().totalBlocks());
    }

    @Test
    void xSlabMarksOutsideCellsNotWanted() throws IOException {
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)),
                new LitematicaSnapshot.Slab(Direction.Axis.X, 1, 1));
        assertEquals(1, res.schematic().totalBlocks());
        assertSame(stairsNorth, at(res, 1, 0, 0));
    }

    @Test
    void slabOutsideEverythingYieldsNull() throws IOException {
        assertNull(LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)),
                new LitematicaSnapshot.Slab(Direction.Axis.Y, 100, 200)));
    }

    @Test
    void airAndStructureVoidAreNotWanted() throws IOException {
        var region = new LitematicaSnapshot.Region(BlockPos.ZERO, new Vec3i(3, 1, 1), Rotation.NONE, Mirror.NONE,
                (x, y, z) -> switch (x) {
                    case 0 -> Blocks.AIR.defaultBlockState();
                    case 1 -> Blocks.STRUCTURE_VOID.defaultBlockState();
                    default -> stone;
                }, new Vec3i(3, 1, 1));
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(region), null);
        assertEquals(1, res.schematic().totalBlocks());
        assertEquals(0, res.schematic().paletteIndex(0, 0, 0));
    }

    @Test
    void paletteIdMatchesReaderKeyFormat() throws IOException {
        var res = LitematicaSnapshot.build("t", "t", BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
                List.of(row(BlockPos.ZERO, new Vec3i(2, 1, 1), Rotation.NONE, Mirror.NONE)), null);
        int idx = res.schematic().paletteIndex(1, 0, 0);
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                res.schematic().paletteId(idx));
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `bash gradlew test --tests combatant.client.util.schematic.LitematicaSnapshotTest`. Expected: compile
failure (`LitematicaSnapshot` is missing).

- [ ] **Step 3: Implement**

```java
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a Litematica placement into one dense {@link Schematic} with every transform baked in, the way Litematica
 * itself pastes it (SchematicPlacingUtils.placeBlocksWithinChunk): positions go through the placement's
 * mirror-then-rotate and then the sub-region's; states are mirrored by the placement, then by the sub-region
 * (axis swapped under a quarter turn of the placement), then rotated by both rotations combined. Cells outside the
 * layer slab, air and structure voids are not wanted. Knows nothing about Litematica's classes.
 */
public final class LitematicaSnapshot {

    @FunctionalInterface
    public interface StateSource {
        BlockState get(int x, int y, int z);
    }

    /** {@code size} is Litematica's signed area size; {@code containerSize} the positive container extent. */
    public record Region(BlockPos pos, Vec3i size, Rotation rotation, Mirror mirror, StateSource container,
                         Vec3i containerSize) {}

    /** Inclusive world-coordinate bounds on one axis. */
    public record Slab(Direction.Axis axis, int min, int max) {
        boolean contains(int x, int y, int z) {
            int c = switch (axis) { case X -> x; case Y -> y; case Z -> z; };
            return c >= min && c <= max;
        }
    }

    public record Result(Schematic schematic, SchematicPlacement placement) {}

    private LitematicaSnapshot() {}

    public static BlockPos transform(int x, int y, int z, Mirror mirror, Rotation rotation) {
        switch (mirror) {
            case LEFT_RIGHT -> z = -z;
            case FRONT_BACK -> x = -x;
            default -> { }
        }
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-z, y, x);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
            case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
            default -> new BlockPos(x, y, z);
        };
    }

    private static int minOffset(int size) {
        int end = size >= 0 ? size - 1 : size + 1;
        return Math.min(0, end);
    }

    private static BlockPos world(int lx, int ly, int lz, BlockPos base, Rotation rp, Mirror mp, Region r) {
        BlockPos main = transform(lx, ly, lz, mp, rp);
        BlockPos sub = transform(main.getX(), main.getY(), main.getZ(), r.mirror(), r.rotation());
        return sub.offset(base);
    }

    public static Result build(String name, String fileName, BlockPos origin, Rotation rp, Mirror mp,
                               List<Region> regions, Slab slab) throws IOException {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<BlockPos> bases = new ArrayList<>(regions.size());
        for (Region r : regions) {
            BlockPos base = transform(r.pos().getX(), r.pos().getY(), r.pos().getZ(), mp, rp).offset(origin);
            bases.add(base);
            int ox = minOffset(r.size().getX()), oy = minOffset(r.size().getY()), oz = minOffset(r.size().getZ());
            int cx = r.containerSize().getX(), cy = r.containerSize().getY(), cz = r.containerSize().getZ();
            if (cx <= 0 || cy <= 0 || cz <= 0) continue;
            // The transform is linear, so the eight corners bound the region.
            for (int i = 0; i < 8; i++) {
                BlockPos p = world(ox + ((i & 1) == 0 ? 0 : cx - 1), oy + ((i & 2) == 0 ? 0 : cy - 1),
                        oz + ((i & 4) == 0 ? 0 : cz - 1), base, rp, mp, r);
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
        }
        if (minX > maxX) return null;
        if (slab != null) {
            switch (slab.axis()) {
                case X -> { minX = Math.max(minX, slab.min()); maxX = Math.min(maxX, slab.max()); }
                case Y -> { minY = Math.max(minY, slab.min()); maxY = Math.min(maxY, slab.max()); }
                case Z -> { minZ = Math.max(minZ, slab.min()); maxZ = Math.min(maxZ, slab.max()); }
            }
            if (minX > maxX || minY > maxY || minZ > maxZ) return null;
        }
        long sx = (long) maxX - minX + 1, sy = (long) maxY - minY + 1, sz = (long) maxZ - minZ + 1;
        if (sx > LitematicReader.MAX_AXIS_SPAN || sy > LitematicReader.MAX_AXIS_SPAN
                || sz > LitematicReader.MAX_AXIS_SPAN || sx * sy * sz > LitematicReader.MAX_VOLUME) {
            throw new IOException("Schematic too large: " + sx + "x" + sy + "x" + sz);
        }
        int sizeX = (int) sx, sizeY = (int) sy, sizeZ = (int) sz;
        short[] blocks = new short[sizeX * sizeY * sizeZ];
        Map<BlockState, Integer> indexOf = new IdentityHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        ids.add("minecraft:air");

        for (int ri = 0; ri < regions.size(); ri++) {
            Region r = regions.get(ri);
            BlockPos base = bases.get(ri);
            Mirror subMirror = r.mirror();
            if (subMirror != Mirror.NONE && (rp == Rotation.CLOCKWISE_90 || rp == Rotation.COUNTERCLOCKWISE_90)) {
                subMirror = subMirror == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
            }
            Rotation combined = rp.getRotated(r.rotation());
            int ox = minOffset(r.size().getX()), oy = minOffset(r.size().getY()), oz = minOffset(r.size().getZ());
            for (int y = 0; y < r.containerSize().getY(); y++) {
                for (int z = 0; z < r.containerSize().getZ(); z++) {
                    for (int x = 0; x < r.containerSize().getX(); x++) {
                        BlockState state = r.container().get(x, y, z);
                        if (state == null || state.isAir() || state.is(Blocks.STRUCTURE_VOID)) continue;
                        BlockPos p = world(ox + x, oy + y, oz + z, base, rp, mp, r);
                        if (slab != null && !slab.contains(p.getX(), p.getY(), p.getZ())) continue;
                        if (p.getX() < minX || p.getX() > maxX || p.getY() < minY || p.getY() > maxY
                                || p.getZ() < minZ || p.getZ() > maxZ) continue;
                        if (mp != Mirror.NONE) state = state.mirror(mp);
                        if (subMirror != Mirror.NONE) state = state.mirror(subMirror);
                        if (combined != Rotation.NONE) state = state.rotate(combined);
                        Integer index = indexOf.get(state);
                        if (index == null) {
                            index = palette.size();
                            if (index > 0xFFFF) throw new IOException("Palette has more than 65535 states");
                            indexOf.put(state, index);
                            palette.add(state);
                            ids.add(key(state));
                        }
                        int lx = p.getX() - minX, ly = p.getY() - minY, lz = p.getZ() - minZ;
                        blocks[(ly * sizeZ + lz) * sizeX + lx] = (short) (int) index;
                    }
                }
            }
        }
        int total = 0;
        for (short b : blocks) if (b != 0) total++;
        if (total == 0) return null;
        Schematic schematic = new Schematic(name, fileName, "", "", sizeX, sizeY, sizeZ,
                palette.toArray(new BlockState[0]), ids.toArray(new String[0]), blocks, total, regions.size(), List.of());
        return new Result(schematic, SchematicPlacement.at(new BlockPos(minX, minY, minZ)));
    }

    /** Same key format as {@code LitematicReader.PaletteBuilder}: {@code ns:id[a=b,c=d]} with sorted properties. */
    static String key(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Map<String, String> props = new TreeMap<>();
        for (Property<?> property : state.getProperties()) props.put(property.getName(), value(state, property));
        return props.isEmpty() ? name
                : name + props.toString().replace('{', '[').replace('}', ']').replace(", ", ",");
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
```

If `SchematicPlacement.at` or `Schematic.paletteId` / `state(x,y,z)` / `paletteIndex(x,y,z)` have different
names, use the real ones. They are listed in `Schematic.java:53-141` and `SchematicPlacement.java:28-45`.
Make `LitematicReader.MAX_AXIS_SPAN`/`MAX_VOLUME` package-visible or public if they are not already.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `bash gradlew test --tests combatant.client.util.schematic.LitematicaSnapshotTest`. Expected: 10 passed.
If `subMirrorAxisSwapsUnderQuarterTurnForStates` fails on position only, re-derive the expected position from the
algorithm and fix the test, not the algorithm. The algorithm comes verbatim from the decompiled Litematica source
(`%TEMP%/lmapi/dec`).

- [ ] **Step 5: Commit**: `git add -A && git commit -m "Schematic: build a dense snapshot from Litematica placement data"`

---

### Task 3: External ownership in SchematicState and the menu

**Files:**
- Modify: `src/main/java/combatant/client/util/schematic/SchematicState.java` (`setSchematic` :144-161,
  `setOrigin` :183, and `rotateClockwise()` / `cycleMirror()`)
- Modify: `src/main/java/combatant/client/features/gui/schematic/SchematicActions.java` (`load` :54,
  `setOriginHere` :94, `setOriginLooked` :102)
- Modify: `src/main/java/combatant/client/features/gui/schematic/SchematicScreen.java` (:441, :794-797 and the
  info panel `renderInfo`)
- Modify: the en_us/ru_ru lang files
- Test: `src/test/java/combatant/client/util/schematic/SchematicStateExternalTest.java`

**Interfaces:**
- Produces:
  - `SchematicState.setExternal(Schematic schematic, SchematicPlacement placement, String label)`
  - `boolean isExternal()`
  - `String externalLabel()`
  - While external, `setOrigin` / `rotateClockwise` / `cycleMirror` do nothing, and `setSchematic(...)` and
    `clear()` end external mode.

- [ ] **Step 1: Write the failing test**

```java
package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchematicStateExternalTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Schematic one() {
        BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        return new Schematic("s", "s", "", "", 1, 1, 1, new BlockState[]{air, stone},
                new String[]{"minecraft:air", "minecraft:stone"}, new short[]{1}, 1, 1, List.of());
    }

    @Test
    void externalLocksPlacementUntilReplaced() {
        SchematicState state = new SchematicState();
        state.setExternal(one(), SchematicPlacement.at(new BlockPos(1, 2, 3)), "house");
        assertTrue(state.isExternal());
        assertEquals("house", state.externalLabel());
        state.setOrigin(new BlockPos(9, 9, 9));
        state.rotateClockwise();
        assertEquals(new BlockPos(1, 2, 3), state.placement().origin());
        assertEquals(Rotation.NONE, state.placement().rotation());

        state.setSchematic(one(), null);
        assertFalse(state.isExternal());
        state.setExternal(one(), SchematicPlacement.at(BlockPos.ZERO), "x");
        state.clear();
        assertFalse(state.isExternal());
        assertFalse(state.hasSchematic());
    }
}
```

If `SchematicState`'s constructor is private, add a package-private no-arg constructor for tests. `INSTANCE`
keeps using it.

- [ ] **Step 2: Run the test and confirm it fails** (`bash gradlew test --tests '*SchematicStateExternalTest'`).
It should fail because `setExternal` is missing.

- [ ] **Step 3: Implement in SchematicState**

```java
    private String externalLabel;

    public boolean isExternal() {
        return externalLabel != null;
    }

    public String externalLabel() {
        return externalLabel;
    }

    /** A schematic owned by another mod (Litematica): placement is fixed; the menu cannot load or move over it. */
    public void setExternal(Schematic next, SchematicPlacement at, String label) {
        setSchematic(next, null);
        placement = at;
        externalLabel = label == null ? "" : label;
        changed();
    }
```

- First line of `setSchematic(Schematic, LayerStats, SchematicDiff.Layout)`: `externalLabel = null;`
- First line of `setOrigin`, `rotateClockwise` and `cycleMirror`: `if (isExternal()) return;`

- [ ] **Step 4: Guard the menu**

- `SchematicActions.load`: after the null check, add
  `if (SchematicState.INSTANCE.isExternal()) { Notifier.warning(I18n.get("screen.combatant.schematic.status.external_locked")); return false; }`
- `setOriginHere` / `setOriginLooked`: return `false` when `SchematicState.INSTANCE.isExternal()`.
- `SchematicScreen`:
  - Line 441 becomes `boolean canLoad = selectedPath != null && SchematicActions.loadingPath() == null && !state.isExternal();`
  - In `renderRight`, `boolean movable = !state.isExternal();`. ORIGIN_HERE and ORIGIN_LOOK use
    `loaded && movable`; ROTATE and MIRROR use `placed && movable`.
  - In `renderInfo`, when `state.isExternal()`, draw one extra line
    `tr("screen.combatant.schematic.external", state.externalLabel())` with the same `ui.text` style as the
    existing info lines.
- Lang keys (en_us / ru_ru):
  - `screen.combatant.schematic.external`: "Following Litematica: %s" / "Следует за Litematica: %s"
  - `screen.combatant.schematic.status.external_locked`: "Litematica owns the schematic; switch SchematicBuilder source to Combatant to load files." / "Схемой управляет Litematica; переключите источник SchematicBuilder на Combatant, чтобы загружать файлы."

- [ ] **Step 5: Run the test and `compileJava`, and confirm they pass**

- [ ] **Step 6: Commit**: `git commit -am "Schematic state: externally owned schematics lock load and placement"`

---

### Task 4: Litematica bridge

**Files:**
- Create: `src/main/java/combatant/client/compat/litematica/LitematicaCompat.java`
- Create: `src/main/java/combatant/client/compat/litematica/LitematicaBridge.java`

**Interfaces:**
- Consumes: `LitematicaSnapshot.build/Region/Slab/Result` (Task 2)
- Produces, all safe to call without Litematica installed:
  - `LitematicaCompat.LOADED` (boolean)
  - `boolean available()`
  - `long fingerprint()` (0 = no usable placement)
  - `LitematicaSnapshot.Result snapshot()` (null = nothing to build)
  - `boolean easyPlaceEnabled()`
  - `String placementName()`

- [ ] **Step 1: LitematicaCompat**

```java
package combatant.client.compat.litematica;

import combatant.client.util.schematic.LitematicaSnapshot;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Safe entry point; {@link LitematicaBridge} is only touched once the mod is known to be loaded. */
public final class LitematicaCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("litematica");
    private static final Logger LOG = LoggerFactory.getLogger("Combatant/Litematica");
    private static boolean failed;

    private LitematicaCompat() {}

    /** False without the mod, or after its API broke once (a newer Litematica); never retried until restart. */
    public static boolean available() {
        return LOADED && !failed;
    }

    public static long fingerprint() {
        if (!available()) return 0L;
        try {
            return LitematicaBridge.fingerprint();
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return 0L;
        }
    }

    public static LitematicaSnapshot.Result snapshot() {
        if (!available()) return null;
        try {
            return LitematicaBridge.snapshot();
        } catch (LinkageError | RuntimeException | java.io.IOException e) {
            if (e instanceof java.io.IOException) {
                LOG.warn("Litematica placement not loaded: {}", e.getMessage());
                return null;
            }
            fail(e);
            return null;
        }
    }

    public static boolean easyPlaceEnabled() {
        if (!available()) return false;
        try {
            return LitematicaBridge.easyPlaceEnabled();
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    public static String placementName() {
        if (!available()) return "";
        try {
            return LitematicaBridge.placementName();
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return "";
        }
    }

    private static void fail(Throwable e) {
        failed = true;
        String version = FabricLoader.getInstance().getModContainer("litematica")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        LOG.error("Litematica {} API is incompatible; Litematica source disabled", version, e);
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("litematica")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
```

Note that a snapshot that's too large throws `IOException`, and the catch logs a warning instead of disabling
the source. Simplify `fail` to reuse `version()`.

- [ ] **Step 2: LitematicaBridge**

```java
package combatant.client.compat.litematica;

import combatant.client.util.schematic.LitematicaSnapshot;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.position.LayerRange;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** DO NOT CALL WITHOUT CHECKING {@link LitematicaCompat#LOADED}. The only class importing Litematica/MaLiLib. */
final class LitematicaBridge {
    private LitematicaBridge() {}

    /** The selected enabled placement, else the only enabled one; null when none or ambiguous. */
    static SchematicPlacement selected() {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        SchematicPlacement chosen = manager.getSelectedSchematicPlacement();
        if (chosen != null && chosen.isEnabled()) return chosen;
        SchematicPlacement only = null;
        for (SchematicPlacement placement : manager.getAllSchematicsPlacements()) {
            if (!placement.isEnabled()) continue;
            if (only != null) return null;
            only = placement;
        }
        return only;
    }

    static long fingerprint() {
        SchematicPlacement p = selected();
        if (p == null || p.getSchematic() == null) return 0L;
        long h = 1469598103934665603L;
        h = mix(h, System.identityHashCode(p));
        h = mix(h, System.identityHashCode(p.getSchematic()));
        h = mix(h, p.getSchematic().getMetadata().getTimeModified());
        h = mix(h, p.getOrigin().asLong());
        h = mix(h, p.getRotation().ordinal());
        h = mix(h, p.getMirror().ordinal());
        for (String name : new TreeMap<>(p.getSchematic().getAreaSizes()).keySet()) {
            SubRegionPlacement s = p.getRelativeSubRegionPlacement(name);
            if (s == null) continue;
            h = mix(h, name.hashCode());
            h = mix(h, s.getPos().asLong());
            h = mix(h, s.getRotation().ordinal());
            h = mix(h, s.getMirror().ordinal());
            h = mix(h, s.isEnabled() ? 1 : 2);
        }
        LayerRange range = DataManager.getRenderLayerRange();
        h = mix(h, range.getLayerMode().ordinal());
        h = mix(h, range.getAxis().ordinal());
        h = mix(h, range.getMinLayerBoundary());
        h = mix(h, range.getMaxLayerBoundary());
        return h == 0L ? 1L : h;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 1099511628211L;
    }

    static LitematicaSnapshot.Result snapshot() throws IOException {
        SchematicPlacement p = selected();
        if (p == null) return null;
        LitematicaSchematic schematic = p.getSchematic();
        if (schematic == null) return null;
        List<LitematicaSnapshot.Region> regions = new ArrayList<>();
        for (String name : new TreeMap<>(schematic.getAreaSizes()).keySet()) {
            SubRegionPlacement sub = p.getRelativeSubRegionPlacement(name);
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(name);
            if (sub == null || !sub.isEnabled() || container == null) continue;
            regions.add(new LitematicaSnapshot.Region(sub.getPos(), schematic.getAreaSizeAsVec3i(name),
                    sub.getRotation(), sub.getMirror(), container::get, container.getSize()));
        }
        LayerRange range = DataManager.getRenderLayerRange();
        LitematicaSnapshot.Slab slab = range.getLayerMode() == LayerMode.ALL ? null
                : new LitematicaSnapshot.Slab(range.getAxis(), range.getMinLayerBoundary(), range.getMaxLayerBoundary());
        Path file = p.getSchematicFile();
        String fileName = file == null ? p.getName() : file.getFileName().toString();
        return LitematicaSnapshot.build(p.getName(), fileName, p.getOrigin(), p.getRotation(), p.getMirror(),
                regions, slab);
    }

    static boolean easyPlaceEnabled() {
        return Configs.Generic.EASY_PLACE_MODE.getBooleanValue();
    }

    static String placementName() {
        SchematicPlacement p = selected();
        return p == null ? "" : p.getName();
    }
}
```

Regions are sorted by name so that overlap order is deterministic. Litematica iterates a map whose order is
unspecified; overlapping enabled regions are rare, and the later one wins, the same as in our reader.

- [ ] **Step 3: `bash gradlew compileJava` and confirm it passes.** Then run `bash gradlew test` to confirm no
test class loads Litematica types.

- [ ] **Step 4: Commit**: `git add -A && git commit -m "Compat: Litematica bridge for the selected placement"`

---

### Task 5: SchematicBuilder source setting, sync, easy-place pause and panic cleanup

**Files:**
- Modify: `src/main/java/combatant/client/features/module/modules/player/schematic/SchematicBuilder.java`
  (settings block around :129-157, `onEnable`/`onDisable` :208-221, `onTick` :308, `canWork` :380)
- Modify: the en_us/ru_ru lang files

**Interfaces:**
- Consumes: `LitematicaCompat.*` (Task 4) and `SchematicState.setExternal/isExternal` (Task 3)

- [ ] **Step 1: Add the setting and fields**

```java
    public enum Source {
        COMBATANT,
        LITEMATICA
    }

    private static final int LITEMATICA_SYNC_INTERVAL = 10;

    private final EnumValue<Source> source =
            visibleWhen(enumMode("source", Source.COMBATANT), () -> LitematicaCompat.LOADED);

    /** -1 = not synced yet (forces a read); 0 = Litematica has no usable placement. */
    private long litematicaFingerprint = -1L;
    private boolean easyPlacePaused;
    private boolean litematicaFallbackWarned;
    private boolean litematicaEmptyWarned;
```

Declare `source` as the first setting so it heads the module's settings list.

- [ ] **Step 2: Add the sync logic**

```java
    private boolean followsLitematica() {
        if (source.get() != Source.LITEMATICA) return false;
        if (LitematicaCompat.available()) return true;
        if (!litematicaFallbackWarned) {
            litematicaFallbackWarned = true;
            Notifier.warning(I18n.get("module.schematicbuilder.notify.litematica_unavailable", LitematicaCompat.version()));
        }
        return false;
    }

    /** Mirrors Litematica's selected placement into the schematic state; re-snapshots when anything about it changed. */
    private void syncLitematica() {
        if (!followsLitematica()) {
            if (state.isExternal()) state.clear();
            litematicaFingerprint = -1L;
            easyPlacePaused = false;
            return;
        }
        boolean easyPlace = LitematicaCompat.easyPlaceEnabled();
        if (easyPlace && !easyPlacePaused) Notifier.warning(I18n.get("module.schematicbuilder.notify.easy_place"));
        easyPlacePaused = easyPlace;
        if (ticks % LITEMATICA_SYNC_INTERVAL != 0 && litematicaFingerprint != -1L) return;
        long fingerprint = LitematicaCompat.fingerprint();
        if (fingerprint == litematicaFingerprint) return;
        litematicaFingerprint = fingerprint;
        LitematicaSnapshot.Result result = fingerprint == 0L ? null : LitematicaCompat.snapshot();
        pending = null;
        if (result == null) {
            if (state.hasSchematic()) state.clear();
            if (!litematicaEmptyWarned) {
                litematicaEmptyWarned = true;
                Notifier.warning(I18n.get("module.schematicbuilder.notify.litematica_none"));
            }
            return;
        }
        litematicaEmptyWarned = false;
        state.setExternal(result.schematic(), result.placement(), LitematicaCompat.placementName());
    }
```

- Call `syncLitematica();` in `onTick` right after `ticks++;` (line 311).
- In `onEnable`, call `syncLitematica()` before the no-placement warning (so it doesn't warn while following
  Litematica), and skip that warning when `followsLitematica()` is true. Set `litematicaFingerprint = -1L;`,
  `litematicaFallbackWarned = false;` and `litematicaEmptyWarned = false;` first so a fresh enable re-reads.
- In `onDisable`, add `if (state.isExternal()) state.clear(); litematicaFingerprint = -1L; easyPlacePaused = false;`.
  Soft panic disables the module through `ModuleManager.shutdownForRuntime`, so this also leaves nothing of ours
  drawn. `restoreAfterSoftPanic` re-enables it, and `onEnable` re-snapshots.
- In `canWork`, after the `isPlaced` check, add `if (easyPlacePaused) { status = Status.PAUSED; return false; }`.

- [ ] **Step 3: Lang keys (en_us / ru_ru)**

- `setting.schematicbuilder.source`: "Source" / "Источник"
- `setting.schematicbuilder.source.option.combatant`: "Combatant file" / "Файл Combatant"
- `setting.schematicbuilder.source.option.litematica`: "Litematica placement" / "Размещение Litematica"
- `module.schematicbuilder.notify.litematica_unavailable`: "Litematica %s is not usable; building from the Combatant schematic instead." / "Litematica %s недоступна; строю по схеме Combatant."
- `module.schematicbuilder.notify.litematica_none`: "Select (or enable exactly one) placement in Litematica." / "Выберите (или включите ровно одно) размещение в Litematica."
- `module.schematicbuilder.notify.easy_place`: "Litematica Easy Place is on; SchematicBuilder pauses so both don't click the same blocks." / "В Litematica включён Easy Place; SchematicBuilder на паузе, чтобы не кликать одни и те же блоки."

- [ ] **Step 4: `bash gradlew compileJava test` and confirm both pass**

- [ ] **Step 5: Commit**: `git commit -am "SchematicBuilder: build Litematica's selected placement as an optional source"`

---

### Task 6: Projection steps aside for external schematics

**Files:**
- Modify: `src/main/java/combatant/client/features/module/modules/visuals/schematic/SchematicProjection.java`
  (`rebuild` :181-202)

- [ ] **Step 1:** In `rebuild`, after `ticksSinceRebuild = 0;` (line 187), insert:

```java
        // Litematica already draws the schematic it owns; a second ghost on top only hides its colours.
        if (state.isExternal()) {
            boxCount = 0;
            return;
        }
```

`needsRebuild` already compares `state.revision()`, so switching source back rebuilds normally.

- [ ] **Step 2: `bash gradlew compileJava` and confirm it passes. Commit**:
`git commit -am "SchematicProjection: no ghost boxes for Litematica-owned schematics"`

---

### Task 7: Integrate, smoke test, Grim test

- [ ] **Step 1: Merge preset clamping.** Run `git fetch fork && git merge fork/lb/preset-clamp-263`, then
  `git merge fork/lb/sync-26.3` (the latest boot fixes). In `SchematicBuilder`, keep both the `source` setting
  and the preset-bound declarations.
- [ ] **Step 2:** Run `bash gradlew build` and confirm it is green.
- [ ] **Step 3: Smoke without Litematica.** Run `bash gradlew runClient` (no optional runtime). Expected:
  - The client boots.
  - The SchematicBuilder settings don't show Source.
  - Loading a `.litematic` through @schematic and building one layer works as before.
- [ ] **Step 4: Smoke with Litematica.** Run `bash gradlew runClient -PwithOptionalModRuntime=true` and go
  into a creative test world:
  1. Load a small two-region schematic in Litematica. Rotate the placement CW90 and mirror it.
  2. Set the layer range to Y min..min+1.
  3. Set SchematicBuilder source to Litematica in survival, with materials, and enable it.
  4. Expected: it builds only those two layers. Every placed block matches Litematica's overlay (no red or
     orange cells in Litematica's verifier), and the Combatant ghost boxes are absent.
  5. Turn on Easy Place: the builder pauses and warns. Turn it off: building resumes.
  6. Enter soft panic: the builder stops and no Combatant overlay remains. Leave panic: it resumes.
  7. Take screenshots of the overlay match and the panic state.
- [ ] **Step 5: Grim.**
  - Copy the existing SchematicBuilder grim-preset scenarios in `C:/Users/voidg/Downloads/krypton/.omc/scripts/grim/`
    into a new `scn_extra_litematica.py` with the `lp_` prefix, setting `"source": "litematica"`.
  - The scenario setup must create a Litematica placement on the test client. Load a fixture schematic through
    a client command, or document that a manual placement is required.
  - The harness must launch with Litematica on the runtime. Pass `-PwithOptionalModRuntime=true` through the
    harness's client launch.
  - Run `GRIM_MC=26.3 python .omc/scripts/grim/grim_run.py --tree C:/Users/voidg/kv/printer263 --only lp_` (cwd
    `C:/Users/voidg/Downloads/krypton`). The grim preset must be clean 3 rounds in a row. Coordinate use of the
    shared Grim server with the other agents (`agent://CrystalGrim`, `agent://AnchorGrim`): message "taking grim
    server" before the run and "released" after.
  - Document the additions in the harness README.
- [ ] **Step 6:** Update `CHANGELOG.md` with one entry under the current section: "SchematicBuilder: optional
  Litematica source (selected placement, sub-regions, layer range; pauses for Easy Place)".
- [ ] **Step 7:** Commit and run `git push fork lb/printer-263`.
