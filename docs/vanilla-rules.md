# Vanilla Minecraft Java Edition 1.21 rules (research notes)

Target: Minecraft Java Edition 1.21 (the project's older notes in `docs/research/*.md` target 1.20.x-1.21.x; this file
is the single parity reference, `docs/PARITY.md` tracks the code against it).

Method: every number below came from a Perplexity query (`perplexity_ask`, `perplexity_search`, `perplexity_research`)
run on 2026-10-14, with the source page named next to it. Status tags:

- **[V]** verified by a minecraft.wiki page snippet returned by the search (URL given).
- **[P]** stated by a Perplexity answer that cited the page but did not show the number itself; treat as probable.
- **[U]** unverified: Perplexity could not confirm it. Kept only because the game already uses it or it is the
  commonly cited value; do not "fix" the code towards a [U] value without a second source.
- **CONFLICT** sources disagree; both values are listed.

URLs are all under `https://minecraft.wiki/w/` unless shown in full.

---

## 1. Tool tiers, durability, mining

### 1.1 Tiers [V] (`Tiers`, `Durability`, `Breaking`)
| Tier | Mining level | Durability | Speed multiplier | Damage bonus |
|---|---:|---:|---:|---:|
| Wood | 0 | 59 | 2 | 0 |
| Stone | 1 | 131 | 4 | 1 |
| Gold | 0 | 32 | 12 | 0 |
| Iron | 2 | 250 | 6 | 2 |
| Diamond | 3 | 1561 | 8 | 3 |
| Netherite | 3 | 2031 | 9 | 4 |

Damage-bonus column: stone/iron/diamond rows [V] from `Tiers`; wood/gold/netherite values are [U] (standard values, not shown in the snippet).
Mining-level rule [V]: blocks tagged `needs_stone_tool` need level >= 1, `needs_iron_tool` level >= 2, `needs_diamond_tool` level >= 3.
A golden pickaxe has level 0, so it cannot harvest gold (level 2). Shears: speed 2 (5 on wool, 15 on cobweb and leaves). Sword: 1.5 (15 on cobweb).
Hoes have the same durability as the other tools of the tier [P].

### 1.2 Minimum pickaxe for drops [V] (`Tiers`, `Iron_Pickaxe`, `Ore`, `Tutorial:Tips_and_tricks`)
- Any pickaxe: stone, cobblestone, coal ore, sandstone, bricks, furnace, netherrack, terracotta, mossy cobblestone.
- Stone pickaxe or better (level 1): iron ore, lapis ore, copper ore, block of iron, block of lapis.
- Iron pickaxe or better (level 2): gold ore, redstone ore, diamond ore, emerald ore, block of gold, block of diamond, block of emerald.
- Diamond pickaxe or better (level 3): obsidian, crying obsidian, ancient debris, netherite block, respawn anchor.
- Mining with too low a tier still breaks the block, but it drops nothing and uses the x5 (not x1.5) base time.
- No tool needed for a drop: dirt, grass block, sand, gravel, clay, logs, planks, leaves (leaves only drop sapling/stick/apple chances), glass (drops nothing without Silk Touch), TNT, chest, bookshelf (needs Silk Touch for itself; drops 3 books otherwise [U]).

### 1.3 Break-time formula [V] (`Breaking`)
```
speed = tool speed multiplier (1 if the tool does not help) [+ Efficiency, Haste, Fatigue omitted]
if not in water without Aqua Affinity: speed /= 5
if not on ground:                       speed /= 5
damage = speed / hardness
damage /= 30 if the block can be harvested with the tool else /= 100
if damage >= 1: instant (0 ticks)
ticks = ceil(1 / damage)            // seconds = ticks / 20
```
Equivalent: base seconds = hardness x 1.5 (can harvest) or x 5 (cannot), divided by the tool speed multiplier; always a multiple of 1/20 s (rounded up).
A 5-tick (the page says 6 ticks / 0.3 s) delay applies between consecutive broken blocks unless the block breaks in 0.05 s or less.
Creative mode breaks instantly. Hardness `-1` (bedrock) can never be broken.

### 1.4 Hardness and break times (seconds, hand / wood / stone / iron / diamond / netherite / gold)
All **[V]** from the per-block pages unless tagged.

| Block | Hardness | Default | Wood | Stone | Iron | Diamond | Netherite | Gold | Page |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Stone | 1.5 | - | - | - | - | - | - | - | `Breaking` (hardness only; example in text) |
| Cobblestone / Mossy cobblestone / Bricks | 2 | 10 | 1.5 | 0.75 | 0.5 | 0.4 | 0.35 | 0.25 | `Cobblestone`, `Bricks`, `Mossy_Cobblestone` |
| Dirt | 0.5 | 0.75 | 0.4 | 0.2 | 0.15 | 0.1 | 0.1 | 0.1 | `Dirt` |
| Grass block | 0.6 | 0.9 | 0.45 | 0.25 | 0.15 | 0.15 | 0.1 | 0.1 | `Grass_Block` |
| Sand | 0.5 | 0.75 | 0.4 | 0.2 | 0.15 | 0.1 | 0.1 | 0.1 | `Sand` |
| Gravel / Clay | 0.6 | 0.9 | 0.45 | 0.25 | 0.15 | 0.15 | 0.1 | 0.1 | `Shovel` speed table |
| Oak log / any log | 2 | 3 | 1.5 | 0.75 | 0.5 | 0.4 | 0.35 | 0.25 | `Log` |
| Planks | 2 | 3 | 1.5 | 0.75 | 0.5 | 0.4 | 0.35 | 0.25 | `Planks` |
| Leaves | 0.2 | 0.3 | 0.15 | 0.1 | 0.05 | 0.05 | 0.05 | 0.05 | `Leaves` (shears 0.05, sword 0.2) |
| Glass | 0.3 | 0.45 | (same for every tool) | | | | | | `Glass` |
| Coal / gold / diamond / lapis / redstone ore | 3 | 15 (coal 15) | 7.5 (coal 2.25) | see below | | | | | `Breaking` table |
| Iron ore | 3 | 15 | 7.5 | 1.15 | 0.75 | 0.6 | 0.5 | 1.25 | `Iron_Ore` |
| Redstone ore | 3 | 15 | 7.5 | 3.75 (cannot harvest) | 0.75 | 0.6 | 0.5 | 1.25 | `Redstone_ore` |
| Diamond ore / gold ore / block of gold | 3 | 15 | 7.5 | 3.75 (cannot harvest) | 0.75 | 0.6 | 0.5 | 1.25 | `Breaking` table |
| Coal ore | 3 | 15 | 2.25 | 1.15 | 0.75 | 0.6 | 0.5 | 0.4 | `Breaking` table |
| Block of iron | 5 | 25 | 12.5 | 1.9 | 1.25 | 0.95 | 0.85 | 2.1 | `Block_of_Iron` |
| Block of diamond | 5 | 25 | 12.5 | 6.25 (cannot harvest) | 1.25 | 0.95 | 0.85 | 2.1 | `Block_of_Diamond` |
| Obsidian | 50 | 250 | 125 | 62.5 | 41.7 | 9.4 | 8.35 | 20.85 | `Obsidian` (drops only with diamond+) |
| Crafting table / Chest | 2.5 | 3.75 | 1.9 | 0.95 | 0.65 | 0.5 | 0.45 | 0.35 | `Crafting_Table`, `Chest` |
| Furnace / Blast furnace | 3.5 | 17.5 | 2.65 | 1.35 | 0.9 | 0.7 | 0.6 | 0.45 | `Furnace` |
| Bookshelf | 1.5 | - | 2.25 | 1.15 | 0.6 | 0.4 | 0.3 | 0.25 | `Breaking` (axe-speed row; default is not in the snippet) |
| Sandstone | 0.8 | 4 | 0.6 | 0.3 | 0.2 | 0.15 | 0.15 | 0.1 | `Sandstone` |
| Terracotta | 1.25 | 6.25 | 0.95 | 0.5 | 0.35 | 0.25 | 0.25 | 0.2 | `Terracotta` |
| Netherrack | 0.4 | 2 | 0.3 | 0.15 | 0.1 | 0.1 | 0.1 | 0.05 | `Netherrack` |
| Pumpkin | 1 | 1.5 | 0.75 | 0.4 | 0.25 | 0.2 | 0.2 | 0.15 | `Pumpkin` |
| Snow block | 0.2 | 1 | 0.15 | 0.1 | 0.05 | 0.05 | 0.05 | 0.05 | `Snow_Block` (needs a shovel to drop) |
| Glowstone | 0.3 | - | - | - | - | - | - | - | `Glowstone` |
| TNT | 0 | instant | | | | | | | `TNT` |
| Mud | 0.5 | 0.75 | 0.4 | 0.2 | 0.15 | 0.1 | 0.1 | 0.1 | ja.minecraft.wiki `Mud` |
| Bedrock | -1 (unbreakable) | | | | | | | | `Bedrock` |
| Ice | 0.5 | | | | | | | | **[U]** (not returned by the search) |

Break-time cells in the "ore" rows that read "cannot harvest" are the penalised x5 times from the wiki table.

### 1.5 Block drops [V] (`Leaves`, `Ore`, `Gravel`, `Obsidian`, `Grass_Block`)
- Stone -> cobblestone (needs any pickaxe); grass block -> dirt; gravel -> gravel, 10% flint (Fortune III 100%); glass -> nothing (Silk Touch gives glass).
- Leaves (no shears/Silk Touch): sapling 5% (jungle 2.5%), 1-2 sticks 2%, oak and dark oak also 0.5% apple.
- Coal ore -> 1 coal. Diamond ore -> 1 diamond. Emerald ore -> 1 emerald. Redstone ore -> 4-5 redstone dust. Lapis ore -> 4-9 lapis. Iron / gold / copper ore -> raw iron / raw gold / raw copper (copper 2-5).
- Obsidian drops itself only with a diamond or netherite pickaxe.
- Logs drop themselves with any tool. Planks/cobblestone/bricks etc. drop themselves (pickaxe needed for stone-type).

### 1.6 Armor [V] (Perplexity answer citing `Armor`, `Armor_materials`)
| Material | Durability H/C/L/B | Defense H/C/L/B | Toughness (each piece) |
|---|---|---|---:|
| Leather | 55 / 80 / 75 / 65 | 1 / 3 / 2 / 1 | 0 |
| Gold | 77 / 112 / 105 / 91 | 2 / 5 / 3 / 1 | 0 |
| Chainmail | 165 / 240 / 225 / 195 | 2 / 5 / 4 / 1 | 0 |
| Iron | 165 / 240 / 225 / 195 | 2 / 6 / 5 / 2 | 0 |
| Diamond | 363 / 528 / 495 / 429 | 3 / 8 / 6 / 3 | 2 |
| Netherite | 407 / 592 / 555 / 481 | 3 / 8 / 6 / 3 | 3 |

### 1.7 Other durabilities and stack sizes [P] (`Durability`, `Hoe`, `Trident`, `Item`, `Inventory`)
Bow 384, shears 238, flint and steel 64, fishing rod 64, shield 336, trident 250, elytra 432, turtle shell 275, carrot on a stick 25.
Stack size 64 by default; **16**: ender pearl, snowball, egg, bucket (empty), sign, honey bottle; **1**: tools, armor, bow, shears, flint and steel, fishing rod, saddle, boat, minecart, bed, cake, potions, music discs, armor stand, banner (banner and bed values from the answer; the page snippets themselves were not shown).
**CONFLICT / note:** the answer lists filled buckets as unstackable (1) and empty buckets as 16 - that matches the wiki.
Hoe damage: tilling costs 1 durability, using it as a weapon costs 2 [P].

---

## 2. Recipes

All from the Perplexity answer citing `Crafting`, `Crafting_Table`, `Smelting` **[P]** (patterns not echoed from the page text; they match the well-known vanilla recipes).
`P` = planks (any matching type), `S` = stick, `M` = tool/armor material, `C` = cobblestone.

| Item | Pattern | Yield |
|---|---|---:|
| Planks | 1 log anywhere | 4 |
| Sticks | 2 planks stacked vertically | 4 |
| Crafting table | 2x2 planks | 1 |
| Furnace | ring of 8 cobblestone (3x3, centre empty) | 1 |
| Chest | ring of 8 planks | 1 |
| Torch | coal or charcoal above a stick | 4 |
| Sword | M / M / S (centre column) | 1 |
| Pickaxe | MMM / .S. / .S. | 1 |
| Axe | MM. / MS. / .S. (mirrored also valid) | 1 |
| Shovel | M / S / S (centre column) | 1 |
| Hoe | MM. / .S. / .S. (mirrored also valid) | 1 |
| Helmet | MMM / M.M | 1 (5 material) |
| Chestplate | M.M / MMM / MMM | 1 (8) |
| Leggings | MMM / M.M / M.M | 1 (7) |
| Boots | M.M / M.M | 1 (4) |
| Bread | 3 wheat in a row | 1 |
| Bucket | I.I / .I. (iron ingots) | 1 |
| Shears | 2 iron ingots diagonal | 1 |
| Bow | .S T / S.T / .S T (sticks left, string right) | 1 |
| Arrow | flint / stick / feather in a column | 4 |
| Ladder | S.S / SSS / S.S | 3 |
| Bookshelf | PPP / BBB / PPP (B = book) | 1 |
| TNT | GSG / SGS / GSG (G gunpowder, S sand) | 1 |
| Sandstone | 2x2 sand | 1 |
| Bricks | 2x2 brick items | 1 |
| Block of iron/gold/diamond/coal/redstone | 9 of the item, 3x3 | 1 |
| Blast furnace | III / ISI / CCC (S furnace, C smooth stone) | 1 |
| Smoker | LLL / LSL / LLL (L logs, S furnace) | 1 |

Armor and tool materials: leather, iron, gold, diamond (netherite is a smithing upgrade, not a crafting recipe).
Repair by combining two damaged items: sum of remaining durability + 5% of max, capped [P].

### 2.1 Smelting [V/P] (`Smelting`)
- Furnace 200 ticks (10 s); blast furnace and smoker 100 ticks (5 s) per item [V].
- Fuel burn time (ticks) [V]: coal 1600, charcoal 1600, planks 300, logs 300, stick 100, blaze rod 2400, coal block 16000, lava bucket 20000.
- Smelting XP [P]: iron ore / raw iron 0.7, gold ore / raw gold 1.0, diamond ore 1.0, sand -> glass 0.1, cooked meat 0.35. XP is fractional; remainder is a chance of one more point.

---

## 3. Survival numbers

### 3.1 Hunger / saturation / exhaustion [V] (`Tutorial:Hunger_management`, `Hunger`, zh/ja wiki copies)
- Start: food 20, saturation 5, exhaustion 0. Saturation can never exceed the food level.
- Exhaustion adds up to 4, then resets to 0 and takes 1 saturation, or 1 food if saturation is 0.
- Exhaustion per action: sprint 0.1 per metre; jump 0.05; sprint-jump 0.2; swim 0.01 per metre; break a block 0.005; attack landing 0.1; take damage 0.1; Hunger effect 0.1 per second per level; natural regeneration 6.0 per 1 HP healed. Walking, sneaking, boating, placing blocks cost nothing.
- **CONFLICT:** an earlier Perplexity answer said "walking and swimming do not add exhaustion". The wiki tables say swimming is 0.01 per metre. The German wiki lists the older 0.015 (pre-1.11 value was 0.015 per `ru.minecraft.wiki` history). Use 0.01.
- Natural regeneration: food >= 18 heals 1 HP every 80 ticks; food 20 with saturation > 0 heals 1 HP every 10 ticks (Java only). No natural regeneration at food <= 17. Sprinting disabled at food <= 6. (Sources: `Tutorial:Hunger_management`, ru wiki `Голод`.)
- Starvation: at food 0, 1 HP every 80 ticks; stops at 10 HP on Easy, 1 HP on Normal, kills on Hard [P].
- Eating takes 32 ticks [P]. Hurt invulnerability 10 ticks [P].

### 3.2 Food table [P] (`Food`) - hunger / saturation
Apple 4/2.4, bread 5/6.0, cooked beef 8/12.8, cooked porkchop 8/12.8, cooked chicken 6/7.2, raw beef/porkchop/chicken 3/1.8 (raw chicken [U] 2/1.2 in current versions - the answer lists 3/1.8 for all three),
carrot 3/3.6, potato 1/0.6, baked potato 5/6.0, cookie 2/0.4, melon slice 2/1.2, golden apple 4/9.6, rotten flesh 4/0.8.
**CONFLICT / [U]:** raw chicken is 2 hunger / 1.2 saturation in 1.21 according to commonly cited tables; the Perplexity answer grouped it with raw beef. Double-check before changing the code.

### 3.3 Fall damage [V] (`Damage`, `Hay_Bale`)
- damage = ceil(fallDistance - safeFallDistance) where safe distance is 3 for the player (about 1 HP per block over 3).
- Water of any depth (entity touches the water before the ground), ladders and vines reset fall distance. Slime block negates it (not when it bounces). Hay bale and honey block: 20% of normal. Bed: distance halved. Sweet berries negate it.
- A fall of 23.5 blocks kills a full-health player (the page says 23 is "enough" but 23.5 is the real threshold).

### 3.4 Other damage [V/P] (`Damage`, `Void`)
- Drowning: 300 air ticks; at air = -20 take 2 HP, reset air to 0 (so 2 HP per second) [P]. Air refills 4 per tick out of water [P].
- Fire / cactus / suffocation: 1 HP every 10 ticks [P]. Lava: 4 HP every 10 ticks and sets on fire [P]. Burn duration is source dependent (the "8 s / 15 s" figures were **not supported**).
- Void: about 4 HP every 10 ticks below y = -64 in 1.18+ worlds; Perplexity said "-128 in the Overworld" which **CONFLICTS** with 1.18+ (the build limit is -64) - treat as [U].
- Lightning damage, respawn reset values and the death XP-drop formula (min(7 x level, 100)) were **not confirmed** by the search [U].

---

## 4. Day / night / weather

- Day = 24000 ticks (20 min) [V `Daylight_cycle`]. Day 0-12000; dusk 12000-13000; night 13000-23000; dawn 23000-24000. Beds usable from 12542 (clear) or during thunder.
- Sky light bottoms at 4 at night, not 0. Moon: 8 phases, a full cycle is 8 days [V].
- Hostile spawning (Overworld, 1.18+): the answer says block light 0; **CONFLICT / [U]** - in 1.19+ the rule is "block light 0 and sky light <= a random value 0-7", which this search did not verify.
- Zombies and skeletons burn in direct sunlight unless under a block/roof/helmet/in water [P].
- Weather durations [V `Commands/weather`]: clear 12000-180000 ticks, rain 12000-24000, thunder 3600-15600. Lightning damage, thunder delay and sky-light reduction in rain: not confirmed [U].
- Mob drops [P] (`Drops`): zombie 0-2 rotten flesh; skeleton 0-2 bone + 0-2 arrow; spider 0-2 string (spider eye rare, 1/3 when killed by player [U]); creeper 0-2 gunpowder; pig 1-3 porkchop; cow 1-3 beef + 0-2 leather; chicken 0-2 feather + 1 chicken.

---

## 5. HUD

### 5.1 Verified facts
- Since 1.20.2 the HUD uses individual sprites in `assets/minecraft/textures/gui/sprites/hud/` [V] (feedback.minecraft.net 1.20.2 notes via Perplexity).
- Health: 10 hearts for 20 HP, each heart 2 HP with a full and half state; 9 px sprite, hearts spaced 8 px apart [P] (resourcepackcreator GUI guide). Blink after damage, shake at low health, regeneration bounce, absorption (gold), poison / wither / frozen colours, hardcore sprite set [P].
- Hunger: 10 drumsticks, full/half, drawn right-to-left on the right side of the hotbar; jitters when saturation is 0; green "hunger effect" sprites [P].
- Armor: 10 icons, row above the health row, only shown when armor > 0 [P].
- Air bubbles: shown only when air < 300, up to 10 bubbles, above the hunger row; popping bubble sprite; drowning damage 2 per second [P].
- XP bar: 182 px wide, level number in green, centred above the hotbar, progress clipped to the fraction [P].
- Hotbar: 182 x 22 px strip, selection frame 24 x 24 sprite (Perplexity's second answer: 24 x 24; its first request said 24 x 23 - **CONFLICT**, use 24 x 24), slot centres 20 px apart, health row about 39 px above the bottom edge [P/U].
- Attack indicator: crosshair sword icon or hotbar bar showing cooldown [P].
- Durability bar under damaged items: green -> yellow -> red, 13 px wide max [P/U on width].

### 5.2 Pixel layout and animation rules
Source: one `perplexity_research` run. It found **no 1.21 method bodies**. Every number in this table that is tagged **[M]** comes from a decompiled `Gui.java`
mirror of unidentified (possibly older) version, https://github.com/Archit-web-29/Minecraft-Decompiled-Code/blob/main/DecompiledMinecraftClient/net/minecraft/client/gui/Gui.java,
so treat [M] as "probably right, not certified for 1.21". Only the sprite names and the existence of the methods are verified for 1.21
(https://mappings.dev/1.21/net/minecraft/client/gui/Gui.html and the Yarn 1.21 `InGameHud` page). Notation: `W`,`H` = GUI-scaled screen size, `C = floor(W/2)`.

| Element | Rule | Status |
|---|---|---|
| Hotbar background | at (C-91, H-22), 182 x 22 | [M] |
| Hotbar item i (0..8) | x = C-88+20i, y = H-19 | [M] |
| Selection frame (slot s) | at (C-92+20s, H-23); drawn **24 x 23** (the 1.20.2 sprite file is 24 x 24; the draw size is 24 x 23) | [M]. **CONFLICT:** first Perplexity answer said 24 x 24 |
| Hearts | row 0 at y = H-39, x = C-91+8(i%10), 9 x 9 icons; extra rows (absorption) step up by `max(10-(rows-2), 3)` px | [M] |
| Food | y = H-39, x = C+91-8i-9 (right aligned, drawn right-to-left), 9 x 9; replaced by the mount-health hearts while riding | [M] |
| Armor | only if armor > 0; x = C-91+8i, y = H-39-(healthRows-1)*rowStep-10 (= H-49 with one health row); full if 2i+1 < armor, half if 2i+1 == armor, else the empty icon | [M] |
| Air bubbles | right aligned above food, about H-59; sprites `air` and `air_bursting` (the popping bubble). **Empty bubbles, drowning wobble and pop sound arrived in 1.21.2**, so 1.21 has only full/popping bubbles | row [M]; sprite names [V]; 1.21.2 note https://www.minecraft.net/en-us/article/minecraft-1-21-2-pre-release-1 |
| XP bar | 182 x 5, with `experience_bar_background` and `experience_bar_progress` sprites; progress clipped to the fraction; level number above it, green. Exact y (about H-29) and text baseline **not sourced** | [U] |
| Heart blink (damage) | on damage while invulnerable: blink deadline = tick + 20; on heal: tick + 10. Blinking is shown only while the deadline is ahead and `((deadline - tick) / 3) % 2 == 1`, i.e. alternating every **3 ticks** (6-tick on/off cycle) | [M] |
| Low-health shake | when health + absorption <= 4, each heart's y gets +0 or +1 px at random. **CONFLICT:** the wiki says the shaking starts at 5 HP or less | [M] vs wiki |
| Regeneration bounce | the heart whose index equals `tick % ceil(maxHealth + 5)` is raised 2 px | [M] |
| Food shake | when saturation <= 0 and `tick % (food*3 + 1) == 0`, each food icon's y gets -1, 0 or +1 px at random | [M]; qualitative behaviour [V] (wiki Hunger) |
| Half icons | for food and armor, half when `2i+1 == value`, full when `2i+1 < value`; hearts half when `2i+1 == health` | [M] |

The earlier project notes (`docs/research/*.md`) used the same 182/22/9/8 geometry, so the code already follows it in broad terms.

---

## 6. Inventory and GUI

Texture sizes and slot origins below are quoted as **[U] "layout reference"** by Perplexity (it could not confirm them from a page), but they are the standard values and match this project's earlier research. Coordinates are from the GUI's top-left, slot = 18 x 18.

| Screen | Texture | Slots |
|---|---|---|
| Player inventory | 176 x 166 | main 9x3 at (8,84); hotbar row at y=142; 2x2 craft at (98,18); output (154,28); armor column at x=8, y=8 step 18; offhand (77,62) |
| Crafting table | 176 x 166 | 3x3 at (30,17); output (124,35); player inv (8,84), hotbar y=142 |
| Furnace | 176 x 166 | input (56,17); fuel (56,53); output (116,35); flame (56,36); arrow (79,34) |
| Chest | 176 x 166 | 27 container slots (3x9) [V count only] |

Mouse behaviour [P] (`Inventory`, `Controls`): left-click pick up / place / merge / swap; right-click picks up half (rounded up) or places one; shift-click quick-moves between the inventory and the open container; double-click gathers matching items up to a full stack; left-drag spreads evenly, right-drag places one per slot; number keys swap with the hotbar slot; Q drops one, Ctrl+Q the stack; scroll wheel cycles the hotbar.
Labels, tooltip colours (0x404040 text, 0x80FFFFFF hover) and shift-click-craft details: **[U]**.
Item entities [P] (`Item_(entity)`): pickup delay 10 ticks (40 for player-thrown), despawn 6000 ticks, merge range 0.5 x 0.25 x 0.5, gravity 0.04 blocks/tick^2, drag 0.02.

---

## 7. Items the search could not settle (kept as [U])
Ice hardness, bookshelf default time, damage bonus for wood/gold/netherite, raw chicken food values, lightning damage, death XP drop, respawn resets, exact hostile light-level rule, GUI label offsets, shift-click crafting rules.
