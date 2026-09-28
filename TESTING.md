# Testing

## Running tests

```
mvn -o test                                          # full suite
mvn -o test -Dtest=RaycastTest                       # one class
mvn -o test -Dtest=RaycastTest#straightDownHitsGroundAtExpectedCoordinateAndFace  # one method
mvn -o test -Dtest="WeatherTest,BiomeTest"            # several classes
```

(`-o` = offline; drop it if you need Maven to re-resolve anything for the first time.)

There is no separate integration/gameplay Maven phase - everything runs through the normal
`mvn test` / Surefire step. The split described below is by **package**, not by plugin.

A real test failure exits non-zero, same as any other Surefire failure - nothing here suppresses
or downgrades a failing assertion.

## How the suite is organized

| Package | What it covers | Needs a window? |
|---|---|---|
| `com.example.world.unit` | `SimplexNoise`, `Biome` selection/weights, `World` coordinate conversion, `BlockType` properties, `Raycast`/DDA, `WorldIO.sanitizeFolderName` | No |
| `com.example.physics.gameplay` | Real `Player.update` driven by a fixed timestep + a `FakeInputState` - walking, sprinting, jumping/landing, wall collision | No |
| `com.example.world.integration` | `WorldIO` save/load round-trips (`@TempDir`, never touches the real `saves/`), world-generation determinism and chunk-boundary consistency, `Weather` state cycling | No |
| `com.example.game` (`DayNightTest`) | `Game`'s pure day/night helpers (`sunDirection`, `computeDaylightFactor`, `computeSkyColor`) | No |
| `com.example.inventory` | `Inventory`/`ItemStack`: add/merge/split, stack limits, full/partial insertion, removal, selection, save/load round-trip | No |
| `com.example.entity` | `ItemEntityManager`: drop physics (never tunnels/sinks), pickup delay, full/partial/duplicate pickup, merging, lifetime, entity cap, freezing in unloaded chunks | No |
| `com.example.fx` | `ParticleSystem`: burst size, expiry, pool cap, vertex output | No |
| `com.example.game` (`MiningControllerTest`, `BlockPlacerTest`, `ItemModelBuilderTest`) | The break transaction (one drop per break, cancel/target-change/invalidation, crack stage progression) and transactional placement (consume only after success) | No |
| `com.example.inventory` (`RecipesTest`, `InventoryMenuTest`) | Block-only crafting recipes (2x2 / 3x3 shaped matching), cursor pick/place/merge/swap/split, taking crafted results, item conservation, close-returns-everything with overflow | No |
| `com.example.ui` (`InventoryLayoutTest`) | Inventory screen geometry: slot hit-testing, no overlaps, everything inside the panel | No |
| `com.example.engine` (`GrassSideSplitTest`) | Splitting grass_block_side into a dirt base and a biome-tintable grayscale fringe overlay | No |
| `com.example.engine` (`LeafEdgeDilateTest`) | Filling every leaf/sapling tile's fully-transparent pixels with the nearest opaque pixel's color before mipmapping, so a distant leaf edge fades out instead of picking up a dark/blue halo (never touches alpha) | No |
| `com.example.world.unit` (`TreeShapeTest`) | Per-species canopy silhouettes (birch narrow/tall, spruce tiered cone, jungle branch clusters, acacia broad/shallow/offset, dark oak heavy, cherry lobes + hanging, mangrove branch sprawl, swamp oak low), every tree face-connected to its trunk, no solid block overwritten, canopy inside its declared margin, per-seed variation, and oak's exact block layout pinned | No |
| `com.example.world.unit` (`BlockDropsTest`) | Drop table rules | No |
| `com.example.engine` (`CrackOverlayTest`) | Procedural crack stage images only (cumulative, deterministic); the overlay's GL parts are manual-only | No |
| `com.example.audio` | Material mapping, manifest validation, registry metadata, trigger predicates (never touches OpenAL) | No |

None of the above needs LWJGL/GLFW/OpenGL. That's not an accident of test design - `Player`,
`World`/`Chunk`, `Raycast`, `Weather`, `TerrainGenerator`/`Biome`/`BiomeColors`, `SimplexNoise`,
and `WorldIO` were already implemented with no rendering dependency; this suite just exercises
that existing separation.

### The one testability change this required

`Player.update(...)` used to take the concrete `Input` class, which wraps a live GLFW window
handle. `com.example.engine.InputState` is a 2-method interface (`isKeyDown`, `consumeKeyPressed`)
that `Input` now implements and `Player.update` now accepts - so a test can drive `Player` with
a plain hand-written fake instead. No behavior change, no mocking framework.

`Game.sunDirection`, `Game.computeDaylightFactor`, `Game.computeSkyColor`, and
`Game.MIN_NIGHT_DAYLIGHT` went from `private` to package-private, purely so `DayNightTest`
(which lives in the same `com.example.game` package for exactly this reason) can call them
directly. No other part of `Game` was changed or is unit-tested this round - see below.

## What is *not* covered, and why

- **`Game`'s orchestration itself** (the main loop, chunk-streaming trigger, input dispatch,
  screen transitions) - `Game.init()` unconditionally creates a real GLFW window and OpenGL
  context. Only the 3 pure helpers above are exercised; the rest is manual-verification-only
  today (see "Manual verification" below).
- **Rendering** (`Window`, `TextureAtlas`, `Shader`, `Mesh`, `Font`, `UiRenderer`, `ChunkMeshBuilder`,
  the UI screens) - all of these construct real GL/GLFW resources in their constructors.
  `Window`'s constructor calls `glfwShowWindow` unconditionally, so even a "headless" attempt
  pops a real window on a machine with a display, and does nothing useful on one without a
  display at all. Not attempted this round.
- **A live test controller** - now exists (`-Dtest.controller=true`, see `com.example.testctl`), with
  `giveItem`, `holdAttack`, `useItem` and `lookAt` so mining, pickup and placement can be driven live
  through the same code path real input uses. It is a manual tool, not an automated test. The original
  note follows for history: it was in scope of the original request but deliberately deferred. `Game` today has no test hooks, no
  injected clock, and creates its GL context at construction; adding a controller means giving it
  those first. The gameplay logic a controller would let you drive (movement, world state,
  raycasting, weather) is already reachable directly through the packages above without one.
- **Performance/stability harness** (frame-time thresholds, GC pressure, long soak tests) as an
  *automated* test - the existing `STREAMING_TIME_BUDGET_NANOS`/`FRAME_WARN_THRESHOLD_NANOS`
  logging in `Game` (from an earlier freeze-diagnosis round) already gives real runtime signal
  when you play the game; turning that into an automated pass/fail threshold needs a running
  window, same limitation as rendering tests above.

**Do not read "60 tests, all passing" as "the game is fully tested."** It means the logic that
*can* run without a window has real, repeatable coverage. Everything above this line is a real,
current gap, not a hedge.

## Manual verification (until the deferred items above are picked up)

For anything touching rendering, the window, or real-time feel: launch with
`mvn -o exec:java`, play through the change, and note what you checked in the PR description.
This is the same method used throughout this project's earlier rounds (sprint feel, lighting,
biome blending) when a screenshot or hands-on check was the only way to confirm something.

## Policy: tests are part of the feature, not a follow-up

**No new feature is considered done until its headless-testable logic has tests for it.**

When you add or change something, before calling it finished:

1. If it's deterministic logic with no GL/window dependency (world gen, physics math, block
   properties, I/O, anything like the packages above) - write a unit or gameplay test for it.
   If you're not sure where it fits, look at the table above for the closest existing example.
2. If it's rendering-only, or otherwise can't run headless - manually verify with
   `mvn -o exec:java` and say what you checked in the PR description. Don't claim automated
   coverage that doesn't exist.
3. If you're fixing a bug, the failing case becomes a permanent regression test in the suite
   (see `DayNightTest.daylightFactorNeverDropsBelowTheDocumentedNightFloor` for the pattern -
   it's a direct regression test for a real bug fixed earlier in this project).
4. Run `mvn -o test` before considering the change complete.

This file (and the table above) should stay accurate - if you add a package or defer something
new, update it here too.
