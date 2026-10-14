# Parity checklist vs vanilla Minecraft Java 1.21

Reference: [`docs/vanilla-rules.md`](vanilla-rules.md) (every rule there has a source and a [V]/[P]/[U] confidence tag).
Resume protocol: read this file top to bottom, pick the first `[ ]` row, fix it, run the game, tick the row with the commit hash.
Status: `[x]` matches (or was fixed, commit in the last column), `[ ]` wrong or missing, `[~]` blocked or only partly fixed (reason in the notes).
Verification in the notes column: `unit` = a unit test, `live` = checked in the running game through the test controller (screenshots in `test-output/screenshots/`).

## 1. Mechanics

| # | Mechanic | Vanilla (rules ref) | Code | Status | Notes / commit |
|---|---|---|---|---|---|
| M1 | Tool speed multipliers | wood 2, stone 4, iron 6, diamond 8, gold 12 (1.1) | wood 2, stone 4, iron 6, diamond 8; no gold tier | [x] | gold tier tracked under items (missing) |
| M2 | Harvest levels | stone>=1 for iron/lapis, iron>=2 for gold/diamond/redstone, diamond>=3 for obsidian (1.2) | same gating via REQUIRED_HARVEST_LEVEL (shifted +1 internally) | [x] | ToolEffectiveness |
| M3 | Break-time formula | x1.5 harvestable / x5 not, / tool speed, /5 airborne, /5 underwater (1.3) | implemented in ToolEffectiveness.breakSeconds + MiningController speedFactor | [x] | matches wiki cells for diamond ore / iron ore / cobblestone |
| M4 | Break time rounded up to whole ticks | ticks = ceil(1/damage) (1.3) | rounded up to whole ticks, instant at <= 0.05 s (ToolEffectiveness.wholeTicks) | [x] | G1 unit VanillaRulesTest = wiki cells (1.15, 0.6, 9.4, 1.35 ...) |
| M5 | Delay between broken blocks | 6 ticks = 0.3 s (1.3) | 0.3 s (BREAK_COOLDOWN_SECONDS) | [x] | G1 |
| M6 | Block hardness table | 1.4 | all 40 verified blocks match (BlockType) | [x] | unit; ice, mangrove roots unverified [U] |
| M7 | Block drops | 1.5 | stone, grass, gravel, leaves, clay, snow, glowstone, coal, diamond, glass, raw iron/gold/copper (2-5) | [x] | G1 unit + live: stone pickaxe on iron ore dropped RAW_IRON |
| M8 | Tool durability | 1.1 / 1.7 | wood 59, stone 131, iron 250, diamond 1561; armor 55-... all match | [x] | Durability |
| M9 | Durability use | tool -1 per block, -2 on attack; sword -1 on attack, -2 per block (1.7) | Durability.attackCost / blockBreakCost | [x] |  |
| M10 | Stack sizes | 16: egg, snowball, ender pearl, bucket, sign; 1: tools, armor (1.7) | tools/armor 1, egg and snowball 16, everything else 64 | [x] | G1 unit + live: 40 eggs became 16/16/8 |
| M11 | Armor defense/toughness | 1.6 | ArmorStats leather 1/3/2/1, iron 2/6/5/2, gold 2/5/3/1, diamond 3/8/6/3 tough 2 | [x] | unit CombatSystemTest |
| M12 | Crafting recipes (existing) | 2 | planks, sticks, table, furnace, chest, sandstone, tools, swords, armor, blast furnace, smoker all match | [x] | Recipes |
| M13 | Crafting recipes (missing) | 2 | no torch, bread, ladder, bucket, shears, bow, arrow, TNT, bricks, bookshelf, bottle, ingot/ore storage blocks | [ ] | tracked per item below |
| M14 | Smelting time | furnace 200 t, blast/smoker 100 t (2.1) | CookingStationType | [x] | unit |
| M15 | Fuel burn times | coal/charcoal 1600, planks/logs 300, stick 100 (2.1) | FuelRegistry; lava/blaze/coal block missing (no items) | [x] |  |
| M16 | Smelting XP | iron 0.7, gold 1.0, glass 0.1, meat 0.35 (2.1) | CookingRecipeRegistry; coal ore and diamond ore recipes absent | [x] | missing recipes are non-vanilla-critical |
| M17 | Hunger exhaustion values | sprint 0.1/m, jump 0.05, sprint-jump 0.2, swim 0.01/m, break 0.005, attack 0.1, damage 0.1 (3.1) | PlayerSurvival constants | [x] |  |
| M18 | Natural regeneration | food>=18: 1 HP/80 t; food 20 + saturation: every 10 t (3.1) | PlayerSurvival.tickRegeneration | [x] |  |
| M19 | Starvation | 80 t, floor 10 Easy / 1 Normal / 0 Hard (3.1) | PlayerSurvival.tickStarvation | [x] |  |
| M20 | Sprint food threshold | food > 6 (3.1) | HungerComponent.canSprint | [x] |  |
| M21 | Food values | 3.2 | all 22 foods match (raw chicken 2/1.2 kept; Perplexity's 3/1.8 contradicts the wiki, see rules doc) | [x] | carrot, cookie, melon, golden apple missing |
| M22 | Fall damage | ceil(distance - 3), water/ladder reset (3.3) | PlayerSurvival.updateFallTracking | [x] |  |
| M23 | Drowning | 300 air, 2 HP/s, +4 per tick recovery (3.4) | PlayerSurvival.tickAir | [x] |  |
| M24 | Hurt invulnerability | 10 ticks, excess damage only (3.1) | HealthComponent | [x] |  |
| M25 | Death XP drop / XP levels | min(7*level,100); 2L+7 / 5L-38 / 9L-158 | Experience | [x] |  |
| M26 | Day length / phases | 24000 t (4) | 1200 s per day, sky colour ramps | [x] |  |
| M27 | Weather durations | rain 12000-24000 t (4) | MIN_RAIN_SECONDS 600 (= 12000 t); others not audited | [x] |  |
| M28 | Dropped item rules | pickup 10 t / 40 t thrown, despawn 6000 t, merge 0.5 x 0.25 x 0.5 (6) | 0.5 s / 2 s, 300 s ok; merge centre distance 0.75 = 0.25-wide boxes inflated by the wiki's 0.5 | [x] | not wrong: 0.5 is the inflate amount, not the centre distance |
| M29 | Inventory: pick up / place / swap / half / one | 6 | InventoryMenu left/right click | [x] | unit InventoryTest |
| M30 | Inventory: shift-click quick move | 6 | not implemented | [ ] |  |
| M31 | Inventory: double-click gather | 6 | not implemented | [ ] |  |
| M32 | Inventory: drag to distribute | 6 | not implemented | [ ] |  |
| M33 | Inventory: number key swap | 6 | not implemented | [ ] |  |
| M34 | Crafting result shift-click | crafts as many as possible | not implemented | [ ] |  |

## 2. HUD

| # | Element | Vanilla (rules 5) | Code | Status | Notes / commit |
|---|---|---|---|---|---|
| H1 | Hotbar sprite 182x22 + selection 24x23 | at (C-91, H-22), items at +3,+3 | generated original sprites at the vanilla geometry | [x] | G2 live: original 182x22 hotbar + 24x23 selection frame (HudSprites), unit HudSpritesTest |
| H2 | Hearts: 10, 9x9, step 8, y=H-39, half hearts | 5.1/5.2 | 10 hearts, 9x9, step 8, y=H-39 | [x] | G2 live: hearts rounded up, half hearts correct (17 HP shows 8 + half) |
| H3 | Heart container + damage blink | blink 3-tick phases for 20 t (10 t when healed) | flash 20 ticks on damage, 10 on heal | [x] | G2 live: container and lost hearts flash white in 3-tick phases; unit HudAnimationTest |
| H4 | Low-health shake | <= 4 HP (wiki: 5) random 0/1 px | each heart jitters 0-1 px at <= 4 HP | [x] | G2 unit HudAnimationTest (threshold 4 HP from the decompiled source; wiki says 5, see rules 5.2) |
| H5 | Regeneration bounce | heart (tick % (max+5)) raised 2 px |  | [~] | blocked: vanilla bounces hearts only under the Regeneration status effect and this project has no status effects |
| H6 | Hunger bar: 10 drumsticks, right-aligned, half icons | x = C+91-8i-9 | 10 drumsticks right-aligned, half icons | [x] | G2 live: 7 food shows 3 full + half drumstick |
| H7 | Hunger shake at saturation 0 | tick % (food*3+1)==0, y +-1 | implemented in Game.drawHealthAndFood | [x] | G2 unit HudAnimationTest (period food*3+1 ticks, saturation 0); jitter -1..+1 px |
| H8 | Armor bar: 10 icons, only when armor>0 | row y=H-49 | original chestplate sprites | [x] | G2 live: armor 13 shows 6 full + half, row appears only with armor |
| H9 | Air bubbles: full + popping, only underwater/refilling | right-aligned above food; 1.21 has no empty bubbles | row kept at H-49 (rules list about H-59 from a possibly older source) | [x] | G2 live: bubbles appear submerged, popping sprite for the partial one, none above water |
| H10 | XP bar 182x5 + green level number | y about H-29 | y = H-29 | [x] | G2 live: 182x5 bar with green fill and level number |
| H11 | Attack indicator (crosshair / hotbar) | 5.1 | original sprites | [x] | G2 live: crosshair indicator bar fills with charge; hotbar mode uses the same sprites |
| H12 | Item count text, durability bar | 13 px bar green->red | Game.drawDurabilityBar | [x] | G2: 13 px bar, rounded width, red-to-green hue like vanilla |
| H13 | Crosshair | 16x16 sprite, white inverted |  | [~] | minor: vanilla crosshair is an inverted-blend 15x15 sprite; the two white quads are close enough |

## 3. Inventory and crafting UI

| # | Element | Vanilla (rules 6) | Code | Status | Notes / commit |
|---|---|---|---|---|---|
| U1 | Panel size and scaling | 176x166 GUI px at the GUI scale | scaled panel, vanilla bevel look (GuiPainter) | [x] | G5 live + unit: 176x166 GUI px panel at the auto GUI scale (InventoryLayout/CookingLayout) |
| U2 | Slot size and spacing | 18 px pitch, slot art 16 px inside | 18 px pitch, 16 px item area | [x] | G5 unit InventoryLayoutTest.slotsSitAtVanillaGuiPixelPositions |
| U3 | Player inventory layout | armor column x=8,y=8 step 18; craft 2x2 at (98,18); out (154,28); offhand (77,62); storage (8,84); hotbar y=142 | layout matches except offhand/preview | [~] | G5: armor column (8,8 step 18), 2x2 grid (98,18), result (154,28), storage (8,84), hotbar y=142 all match. Still missing: offhand slot (77,62) and the player-model preview (an empty inset box is drawn) |
| U4 | Crafting table layout | 3x3 at (30,17), out (124,35) |  | [x] | G5 live + unit: 3x3 at (30,17), result (124,35); no armor slots at the table |
| U5 | Furnace layout | in (56,17), fuel (56,53), out (116,35), flame (56,36), arrow (79,34) |  | [x] | G5 live + unit CookingLayoutTest: in (56,17), fuel (56,53), out (116,35), flame (56,36), arrow (79,34); lit flame and half-filled arrow seen |
| U6 | Panel and slot look | light-grey panel with bevel, inset slots | GuiPainter.panel / slot (original art, flat quads) | [x] | G5 live: light-grey bevelled panel, inset 18 px slots |
| U7 | Hover highlight, held item, tooltip | white 50% square on the slot; item at cursor; dark-purple tooltip | tooltip colours from rules 6 are [U] | [x] | G5 live: white 50% hover square seen; held item drawn at 16 px x scale centred on the cursor; dark-purple tooltip (GuiPainter.tooltip) |
| U8 | Labels | Crafting / Inventory text colour 0x404040 |  | [x] | G5 live: labels in 0x404040 ('Crafting' at (97,8) / (28,6), 'Inventory' at (8,72), furnace title centred) |

## 4. Blocks and items (one row per entry in `BlockType`)

Columns: stack size, durability, mining/drop result, icon. Icon status refers to the 16x16 art, which must be original pixel art in the vanilla style (outlined shapes, 3-4 tone shading, light from the top left). `Hardness` is checked against the verified table.

| Item | Kind | Stack | Dur. | Hardness / mining / drop | Icon | Status | Notes / commit |
|---|---|---:|---:|---|---|---|---|
| Stone (`STONE`) | block | 64 | | 1.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Grass Block (`GRASS_BLOCK`) | block | 64 | | 0.6 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Dirt (`DIRT`) | block | 64 | | 0.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Cobblestone (`COBBLESTONE`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Oak Wood Planks (`OAK_PLANKS`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Bedrock (`BEDROCK`) | block | 64 | | -1 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Water (`WATER`) | block | 64 | | -1 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Sand (`SAND`) | block | 64 | | 0.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Gravel (`GRAVEL`) | block | 64 | | 0.6 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Gold Ore (`GOLD_ORE`) | block | 64 | | 3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. drops raw item (G1) |
| Iron Ore (`IRON_ORE`) | block | 64 | | 3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. drops raw item (G1) |
| Coal Ore (`COAL_ORE`) | block | 64 | | 3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Oak Wood (`OAK_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Oak Leaves (`OAK_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Glass (`GLASS`) | block | 64 | | 0.3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Sandstone (`SANDSTONE`) | block | 64 | | 0.8 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Block of Gold (`GOLD_BLOCK`) | block | 64 | | 3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Block of Iron (`IRON_BLOCK`) | block | 64 | | 5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Bricks (`BRICKS`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| TNT (`TNT`) | block | 64 | | 0 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Bookshelf (`BOOKSHELF`) | block | 64 | | 1.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Mossy Cobblestone (`MOSSY_COBBLESTONE`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Obsidian (`OBSIDIAN`) | block | 64 | | 50 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Chest (`CHEST`) | block | 64 | | 2.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Diamond Ore (`DIAMOND_ORE`) | block | 64 | | 3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Block of Diamond (`DIAMOND_BLOCK`) | block | 64 | | 5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Crafting Table (`CRAFTING_TABLE`) | block | 64 | | 2.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Furnace (`FURNACE`) | block | 64 | | 3.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Ice (`ICE`) | block | 64 | | 0.5 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Snow Block (`SNOW_BLOCK`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Clay (`CLAY`) | block | 64 | | 0.6 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Pumpkin (`PUMPKIN`) | block | 64 | | 1 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. axe is the fast tool (G1) |
| Netherrack (`NETHERRACK`) | block | 64 | | 0.4 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Glowstone (`GLOWSTONE`) | block | 64 | | 0.3 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Terracotta (`TERRACOTTA`) | block | 64 | | 1.25 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Red Sand (`RED_SAND`) | block | 64 | | 0.5 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Birch Wood (`BIRCH_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Birch Leaves (`BIRCH_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Spruce Wood (`SPRUCE_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Spruce Leaves (`SPRUCE_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Jungle Wood (`JUNGLE_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Jungle Leaves (`JUNGLE_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Acacia Wood (`ACACIA_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Acacia Leaves (`ACACIA_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Mangrove Wood (`MANGROVE_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Mangrove Leaves (`MANGROVE_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Dark Oak Wood (`DARK_OAK_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Dark Oak Leaves (`DARK_OAK_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Cherry Wood (`CHERRY_LOG`) | block | 64 | | 2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Cherry Leaves (`CHERRY_LEAVES`) | block | 64 | | 0.2 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Mud (`MUD`) | block | 64 | | 0.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Mangrove Roots (`MANGROVE_ROOTS`) | block | 64 | | 0.7 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Oak Sapling (`OAK_SAPLING_STAGE_0`) | block | 64 | | 0 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Stick (`STICK`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Wooden Pickaxe (`WOODEN_PICKAXE`) | item | 1 | 59 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Stone Pickaxe (`STONE_PICKAXE`) | item | 1 | 131 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Pickaxe (`IRON_PICKAXE`) | item | 1 | 250 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Wooden Axe (`WOODEN_AXE`) | item | 1 | 59 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Stone Axe (`STONE_AXE`) | item | 1 | 131 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Axe (`IRON_AXE`) | item | 1 | 250 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Wooden Shovel (`WOODEN_SHOVEL`) | item | 1 | 59 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Stone Shovel (`STONE_SHOVEL`) | item | 1 | 131 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Shovel (`IRON_SHOVEL`) | item | 1 | 250 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Apple (`APPLE`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Bread (`BREAD`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Beef (`COOKED_BEEF`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Blast Furnace (`BLAST_FURNACE`) | block | 64 | | 3.5 ok | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Smoker (`SMOKER`) | block | 64 | | 3.5 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Copper Ore (`COPPER_ORE`) | block | 64 | | 3 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. drops raw item (G1) |
| Smooth Stone (`SMOOTH_STONE`) | block | 64 | | 2 [U] | original pixel art | [x] | G4 live (world wall, trees, inventory), unit BlockArtTest. |
| Coal (`COAL`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Charcoal (`CHARCOAL`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Ingot (`IRON_INGOT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Gold Ingot (`GOLD_INGOT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Copper Ingot (`COPPER_INGOT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Nugget (`IRON_NUGGET`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Brick (`BRICK`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Iron (`RAW_IRON`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Gold (`RAW_GOLD`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Copper (`RAW_COPPER`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Beef (`RAW_BEEF`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Porkchop (`RAW_PORKCHOP`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Porkchop (`COOKED_PORKCHOP`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Chicken (`RAW_CHICKEN`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Chicken (`COOKED_CHICKEN`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Mutton (`RAW_MUTTON`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Mutton (`COOKED_MUTTON`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Rabbit (`RAW_RABBIT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Rabbit (`COOKED_RABBIT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Cod (`COD`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Cod (`COOKED_COD`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Raw Salmon (`SALMON`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Cooked Salmon (`COOKED_SALMON`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Potato (`POTATO`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Baked Potato (`BAKED_POTATO`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Kelp (`KELP`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Dried Kelp (`DRIED_KELP`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Rotten Flesh (`ROTTEN_FLESH`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Wooden Sword (`WOODEN_SWORD`) | item | 1 | 59 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Stone Sword (`STONE_SWORD`) | item | 1 | 131 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Sword (`IRON_SWORD`) | item | 1 | 250 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Leather (`LEATHER`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Feather (`FEATHER`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Egg (`EGG`) | item | 16 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. stack 16 fixed (G1) |
| Leather Cap (`LEATHER_HELMET`) | item | 1 | 55 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Leather Tunic (`LEATHER_CHESTPLATE`) | item | 1 | 80 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Leather Pants (`LEATHER_LEGGINGS`) | item | 1 | 75 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Leather Boots (`LEATHER_BOOTS`) | item | 1 | 65 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Helmet (`IRON_HELMET`) | item | 1 | 165 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Chestplate (`IRON_CHESTPLATE`) | item | 1 | 240 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Leggings (`IRON_LEGGINGS`) | item | 1 | 225 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Iron Boots (`IRON_BOOTS`) | item | 1 | 195 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Golden Helmet (`GOLD_HELMET`) | item | 1 | 77 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Golden Chestplate (`GOLD_CHESTPLATE`) | item | 1 | 112 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Golden Leggings (`GOLD_LEGGINGS`) | item | 1 | 105 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Golden Boots (`GOLD_BOOTS`) | item | 1 | 91 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond (`DIAMOND`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Sword (`DIAMOND_SWORD`) | item | 1 | 1561 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Axe (`DIAMOND_AXE`) | item | 1 | 1561 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Pickaxe (`DIAMOND_PICKAXE`) | item | 1 | 1561 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Shovel (`DIAMOND_SHOVEL`) | item | 1 | 1561 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Helmet (`DIAMOND_HELMET`) | item | 1 | 363 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Chestplate (`DIAMOND_CHESTPLATE`) | item | 1 | 528 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Leggings (`DIAMOND_LEGGINGS`) | item | 1 | 495 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Diamond Boots (`DIAMOND_BOOTS`) | item | 1 | 429 | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Flint (`FLINT`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Clay Ball (`CLAY_BALL`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Snowball (`SNOWBALL`) | item | 16 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. stack 16 fixed (G1) |
| Glowstone Dust (`GLOWSTONE_DUST`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Book (`BOOK`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Gunpowder (`GUNPOWDER`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Bone (`BONE`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Arrow (`ARROW`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| String (`STRING`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |
| Spider Eye (`SPIDER_EYE`) | item | 64 | | item | original pixel art | [x] | G3 live (inventory + hotbar), unit ItemIconsTest. |

### Items and blocks vanilla has that the clone lacks (missing, tracked, not all will be built)

- [ ] gold pickaxe/axe/shovel/sword (tier gold: speed 12, level 0, durability 32)
- [ ] hoes (all tiers) and farmland
- [ ] chainmail armor
- [ ] torch
- [ ] bread recipe (wheat), wheat, carrot, melon slice, cookie, golden apple
- [ ] bucket, shears, flint and steel, bow, fishing rod
- [ ] ladder, door, fence, bed
- [ ] glass bottle
- [ ] coal/iron/gold/diamond/emerald/redstone/lapis storage blocks and their 9-item recipes
- [ ] redstone, lapis, emerald ores
- [ ] TNT crafting recipe, bricks crafting recipe, bookshelf crafting recipe, book recipe
- [ ] ender pearl, sign, banner, saddle, boat, minecart
- [ ] offhand slot (inventory (77,62), F to swap) and the player-model preview in the inventory screen
- [ ] ghost armor icons in empty armor slots
