# Testing

## Running tests

```
mvn -o test                                          # full suite
mvn -o test -Dtest=RaycastTest                       # one class
mvn -o test -Dtest=RaycastTest#straightDownHitsGroundAtExpectedCoordinateAndFace  # one method
mvn -o test -Dtest="WeatherTest,BiomeAttributesTest"            # several classes
```

(`-o` = offline; drop it if you need Maven to re-resolve anything for the first time.)

There is no separate integration/gameplay Maven phase - everything runs through the normal
`mvn test` / Surefire step. The split described below is by **package**, not by plugin.

A real test failure exits non-zero, same as any other Surefire failure - nothing here suppresses
or downgrades a failing assertion.

## How the suite is organized

| Package | What it covers | Needs a window? |
|---|---|---|
| `com.example.world.unit` | biome atmosphere attributes and precipitation from the real biome files (`BiomeAttributesTest`), `World` coordinate conversion, `BlockType` properties, `Raycast`/DDA, `WorldIO.sanitizeFolderName` | No |
| `com.example.physics.gameplay` | Real `Player.update` driven by a fixed timestep + a `FakeInputState` - walking, sprinting, jumping/landing, wall collision | No |
| `com.example.world.integration` | `WorldIO` save/load round-trips (`@TempDir`, never touches the real `saves/`), world-generation determinism and chunk-boundary consistency, `Weather` state cycling | No |
| `com.example.game` (`DayNightTest`) | `Game`'s pure day/night helpers (`sunDirection`, `computeDaylightFactor`, `computeSkyColor`) | No |
| `com.example.inventory` | `Inventory`/`ItemStack`: add/merge/split, stack limits, full/partial insertion, removal, selection, save/load round-trip | No |
| `com.example.entity` | `ItemEntityManager`: drop physics (never tunnels/sinks), pickup delay, full/partial/duplicate pickup, merging, lifetime, entity cap, freezing in unloaded chunks | No |
| `com.example.fx` | `ParticleSystem`: burst size, expiry, pool cap, vertex output | No |
| `com.example.game` (`MiningControllerTest`, `BlockPlacerTest`, `ItemModelBuilderTest`) | The break transaction (one drop per break, cancel/target-change/invalidation, crack stage progression) and transactional placement (consume only after success) | No |
| `com.example.inventory` (`RecipesTest`, `InventoryMenuTest`) | Crafting recipes (2x2 / 3x3 shaped matching, including mirrored axe heads), cursor pick/place/merge/swap/split, taking crafted results, item conservation, close-returns-everything with overflow. Covers wood/stone/iron tools + sticks (see `com.example.world.unit`'s `ToolEffectivenessTest` for their mining behavior) and the Blast Furnace/Smoker recipes; `InventoryMenu.leftClickSlot`/`rightClickSlot` were made public static so `CookingMenu` reuses them, verified non-breaking here | No |
| `com.example.ui` (`InventoryLayoutTest`) | Inventory screen geometry: slot hit-testing, no overlaps, everything inside the panel | No |
| `com.example.engine` (`GrassSideSplitTest`) | Splitting grass_block_side into a dirt base and a biome-tintable grayscale fringe overlay | No |
| `com.example.engine` (`LeafEdgeDilateTest`) | Filling every leaf/sapling tile's fully-transparent pixels with the nearest opaque pixel's color before mipmapping, so a distant leaf edge fades out instead of picking up a dark/blue halo (never touches alpha) | No |
| `com.example.world.unit` (`TreeShapeTest`) | Per-species canopy silhouettes (birch narrow/tall, spruce tiered cone, jungle branch clusters, acacia broad/shallow/offset, dark oak heavy, cherry lobes + hanging, mangrove branch sprawl, swamp oak low), every tree face-connected to its trunk, no solid block overwritten, canopy inside its declared margin, per-seed variation, and oak's exact block layout pinned | No |
| `com.example.world.unit` (`BlockDropsTest`) | Drop table rules, and (this round) the tool-aware `resolve` overload: a gated ore drops nothing without the required tier, drops normally with it, and the 2-arg overload is exactly a bare hand | No |
| `com.example.world.unit` (`ToolEffectivenessTest`) | Tool kind/tier lookup, correct-tool speed multipliers vs. the wrong tool/no tool, and harvest-level gating for the 4 tool-gated ores | No |
| `com.example.engine` (`CrackOverlayTest`) | Procedural crack stage images only (cumulative, deterministic); the overlay's GL parts are manual-only | No |
| `com.example.audio` | Material mapping, manifest validation, registry metadata, trigger predicates (never touches OpenAL) | No |
| `com.example.survival` (`HealthComponentTest`, `HungerComponentTest`) | Reusable health/damage/hurt-cooldown/death state and food/saturation/exhaustion state, entirely dt-driven (a cooldown check runs the same real-time span at 30fps and 144fps and gets the same result) | No |
| `com.example.physics.gameplay` (`PlayerSurvivalTest`) | `PlayerSurvival` driving a real `Player` + real gravity: sprint/swim/jump/flying exhaustion, block-break/damage exhaustion, regen (gated on food, costs exhaustion), starvation (ticks, stops at each `Difficulty`'s floor), real fall damage from an actual drop, eating a real food item, death freezing further hunger/regen/starvation, reset/loadState. Also `PlayerMovementGameplayTest`'s `canSprint=false` override test | No |
| `com.example.world.unit` (`FoodRegistryTest`) | The 3 requested foods' exact nutrition/saturation values, and that none are placeable | No |
| `com.example.world.integration` (`WorldIoRoundTripTest` additions) | Health/hunger/saturation/exhaustion and difficulty round-trip through `world.dat`, and an old save with no survival section still loads (falls back to full health/NORMAL) | No |
| `com.example.cooking` (`CookingRecipeRegistryTest`) | Every furnace/blast-furnace/smoker recipe's accept/reject list, output/count/XP correctness, uniqueness of inputs | No |
| `com.example.cooking` (`FuelRegistryTest`) | Fuel burn-tick values, non-fuel items report 0 | No |
| `com.example.cooking` (`CookingStationStateTest`) | The tick state machine: burn/cook progress, output safety (never overwrites incompatible output, never wastes fuel with no valid input, never consumes input without producing output), the exact "8 items per coal in every station type" fuel-efficiency invariant, and that specialized stations are only faster, never more efficient (first item at tick 100 vs. 200) | No |
| `com.example.cooking` (`CookingStationManagerTest`) | Create/get/remove by position, snapshot/restore round-trip, and that `tickAll` only advances stations in loaded+generated chunks | No |
| `com.example.cooking` (`CookingMenuTest`) | Input/fuel slot pick-place (reusing `InventoryMenu`'s now-public static click helpers), output slot is take-only and grants proportional XP on a partial take, `close()` returns only the cursor and leaves the station's own contents untouched | No |
| `com.example.entity` (`MobSystemTest`) | Entity/LivingEntity/Mob + goal AI + bounded A* navigation + `MobManager`/`MobSpawner`: gravity/landing, wall collision, path around a wall, 1-block step-up, target acquisition, stop at attack range, cooldown-gated attacks with per-difficulty damage via the shared damage pipeline, no attack through a wall (shared `Raycast`), death stops AI + single drop roll + delayed corpse removal, cap/despawn, ray-vs-mob test, spawn-rule rejections, 24-zombie tick cost | No |
| `com.example.ui` (`CookingLayoutTest`) | Cooking screen geometry: slot hit-testing, no overlaps, everything inside the panel | No |
| `com.example.ui` (`MenuSystemTest`) | Menu system: GUI-scale rule (largest integer scale keeping 320x240, manual cap), pixel-font glyph table/advance/line-height/128x128 sheet, options persistence + clamping + corrupt-file fallback, slider/button/text-field logic, vanilla seed parsing, world rename keeps every other saved byte | No |
| `com.example.testctl` (`CommandsTest` additions) | `openCookingScreen`/`advanceSimulationTicks` parsing, validation, and error dispatch | No |

None of the above needs LWJGL/GLFW/OpenGL. That's not an accident of test design - `Player`,
`World`/`Chunk`, `Raycast`, `Weather`, `BiomeAttributes`,
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
  `giveItem`, `holdAttack`, `holdUse`, `useItem`, `lookAt`, `openCookingScreen` and `advanceSimulationTicks` (fast-
  forwards cooking-station ticks without waiting real time) so mining, pickup, placement, and now
  cooking/smelting can be driven live through the same code path real input uses. It is a manual tool,
  not an automated test. The original
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

**Cooking/smelting milestone - live-controller pass actually performed:** launched with
`-Dtest.controller=true`, then via real HTTP calls: placed a real Furnace through the normal
place-block path (confirmed the block-entity was registered at the exact placement coordinates,
not just written to the world grid); opened its screen; moved coal and iron ore from the
inventory into the fuel/input slots through the same click primitives real input uses;
`advanceSimulationTicks(200)` completed exactly one smelt (1 iron ore -> 1 iron ingot, 1 coal
consumed, burn fraction 0.875 = 1400/1600 ticks remaining - matches the fuel-scaling math
exactly); took the output slot and confirmed XP (0.7) was granted to `PlayerSurvival`; closed
the screen and confirmed the taken ingot returned to the inventory; confirmed ordinary real-time
play (not just the fast-forward command) also advances cooking correctly; mined the furnace with
a pickaxe and confirmed every one of its contents (the block itself, remaining fuel, remaining
output) dropped as real item entities - nothing lost, nothing duplicated; saved, fully restarted
the process, reloaded the world, and confirmed the station's exact mid-cook state (remaining
fuel, output count, burn fraction) survived the round trip. All of this matched the unit tests'
own numbers exactly, so the wiring in `Game` (placement hook, break hook, fixed-tick
accumulator, GUI open/close, save/load) is confirmed correct, not just the isolated logic.
Also confirmed live: with the furnace's own screen left open the whole time (no close/reopen),
`cookFraction` kept advancing on its own - cooking only stops at the real pause menu
(`GameState.PAUSED`), matching real vanilla's "GUIs don't pause the world, the pause menu does"
behavior exactly (see `Game.tickCookingStations`, called for `IN_GAME`, `INVENTORY`, and
`COOKING` alike - this replaced an earlier, incorrect version of this feature that paused
cooking while its own screen was open).

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

**Mob-system milestone - live-controller pass actually performed:** `spawnZombie` on a flat
world with natural spawning off; the zombie acquired the player, walked up, stopped at range and
hit for 3.0 on a ~1s rhythm (Normal) until the player died and it went idle; after `respawn`,
holding attack through the real left-click path took the zombie from 20 to 4 HP and then killed
it, dropping 2 Rotten Flesh as item entities and removing the corpse; a screenshot confirmed the
box-humanoid model renders facing the player. Multi-zombie cost is covered by `MobSystemTest`,
not a live frame-time measurement.

**Menu-system milestone - live-controller pass actually performed:** every screen was opened
(`openMenu`) and screenshotted: title, Select World, Create New World, Options hub, Video, Music &
Sounds, Controls/Mouse/Key Binds, Game Menu, Death. Responsiveness was checked by `resizeWindow` to
640x480, 900x400, 1280x720 and 1920x1080 (GUI scale 2/1/3/4 chosen automatically, layout intact).
A real end-to-end click flow used `click` (same edge flags as a physical click): Singleplayer ->
Create New World -> Create (world generated and entered) -> pause -> Options -> Difficulty (Normal
-> Hard confirmed in `state`) -> Done -> Respawn from the death screen -> Save and Quit to Title.

**Real-texture HUD pass:** real Minecraft item icons (`textures/item/*.png`), HUD sprites
(`textures/gui/sprites/hud/` hearts, hunger icons, hotbar and its selection frame) were downloaded
from the public `misode/mcmeta` asset mirror into the gitignored `textures/` folder and verified by
screenshot (hotbar, hearts at partial health, hunger, inventory icons, held items). A missing
texture falls back to the previous procedural icon / plain HUD, so nothing breaks without them.

**Combat-and-food milestone - live-controller pass performed:** launched with
`-Dtest.controller=true`; equipped a full iron set by right-click (`armor.defense=15`, armor bar
on the HUD, a zombie hit for 3 took 1.38 HP); an iron sword did 5.9 damage per fully charged hit
to a zombie with 2 natural armor and lost 1 durability per swing; `spawnMob` placed a pig, cow
and chicken (all render with the four-legged / bird models), natural packs appeared on newly
generated chunks, and killing a cow dropped 2 raw beef and 2 leather and granted 3 XP.
Unit coverage: `CombatSystemTest` (charge/crit/sweep/knockback/armor math, durability, armor
slots, recipes) and `PassiveMobTest` (stats, drop ranges, egg timer, mob/armor persistence,
species weights). Not verified live: save/reload of animals (covered by the `WorldIO` unit test only).

**Diamond tier / original skins / mob sounds - live pass:** full diamond set equipped by right-click
(`armor.defense=20 toughness=8.0`, durabilities 363/528/495/429); a diamond sword hit cost 1 of 1561
durability. Pig, cow, chicken and zombie render with the original 64x32 / 64x64 Mojang skins through the
vanilla box-UV unwrap (screenshots checked by eye: faces, snout, udder, wattle, zombie shirt/pants).
Sounds are wired for mob say/hurt/death/step, egg plop, player attack variants, player hurt, armor equip
and item break; the log showed no missing-file warnings, but I cannot hear the audio from here, so the
mix (volumes, pitch) is by the research values and not judged by ear. Skins and sounds live in the
gitignored `textures/entity/` and `sounds/mob|entity|damage|item/` (downloaded from the misode/mcmeta
`1.21.4-assets` tag); without them the mobs fall back to flat-colour skins and silence.

**Vanilla-alignment milestone - live pass:** screenshots checked by eye for the vanilla-layout Options hub
(difficulty + lock), Video Settings (render distance, max framerate, brightness, attack indicator, FOV effects),
Music & Sounds (hostile / friendly sliders) and the rebinding Key Binds screen; the HUD shows the XP bar filling
from orbs; creeper, skeleton and spider render with their original skins and a creeper that reached a target
exploded, hurt the player, dug blocks and spilled orbs. Unit tests cover `options.txt` round trips (vanilla and
legacy format), key binding conflicts, XP curve, fast regeneration, Peaceful, drowning state, falling blocks, XP
orbs, thrown-item velocity, zombie sunburn/babies, spawn light rules, leaf/gravel drop chances, harvest tiers,
explosions (damage formula, craters, bedrock/obsidian), arrows and the three hostile mobs. Not verified live:
audio playback quality, eating animation timing with real mouse input, drowning HUD, and Q-drop/pick-block by
real keypress (the controller has no key-injection command).
