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
| M6 | Block hardness table | 1.4 | all 40 verified blocks match (BlockType) | [x] | unit; ice and mangrove roots now read from the client jar [V] (G15) |
| M7 | Block drops | 1.5 | stone, grass, gravel, leaves, clay, snow, glowstone, coal, diamond, glass, raw iron/gold/copper (2-5) | [x] | G1 unit + live: stone pickaxe on iron ore dropped RAW_IRON |
| M8 | Tool durability | 1.1 / 1.7 | wood 59, stone 131, iron 250, diamond 1561; armor 55-... all match | [x] | Durability |
| M9 | Durability use | tool -1 per block, -2 on attack; sword -1 on attack, -2 per block (1.7) | Durability.attackCost / blockBreakCost | [x] |  |
| M10 | Stack sizes | 16: egg, snowball, ender pearl, bucket, sign; 1: tools, armor (1.7) | tools/armor 1, egg and snowball 16, everything else 64 | [x] | G1 unit + live: 40 eggs became 16/16/8 |
| M11 | Armor defense/toughness | 1.6 | ArmorStats leather 1/3/2/1, iron 2/6/5/2, gold 2/5/3/1, diamond 3/8/6/3 tough 2 | [x] | unit CombatSystemTest |
| M12 | Crafting recipes (existing) | 2 | planks, sticks, table, furnace, chest, sandstone, tools, swords, armor, blast furnace, smoker all match | [x] | Recipes |
| M13 | Crafting recipes (missing) | 2 | partly fixed | [~] | G6 unit RecipesVanillaTest + live: iron block from 9 ingots. Added storage blocks (+unpack), bricks, snow, clay, glowstone, TNT. Still missing: torch, bread/wheat, ladder, bucket, shears, bow, book/bookshelf (needs paper), glass bottle - their ingredient items do not exist yet |
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
| M30 | Inventory: shift-click quick move | 6 | InventoryMenu.quickMove, shift held in Game.updateInventoryScreen | [x] | G6 unit InventoryMenuVanillaTest (hotbar<->storage, merge first, wear armor, grid/armor back to inventory) |
| M31 | Inventory: double-click gather | 6 | InventoryMenu.gatherSame | [x] | G6 unit: partial stacks drained first, other items untouched; 250 ms same-slot double click in Game |
| M32 | Inventory: drag to distribute | 6 | InventoryMenu.distribute | [x] | G6 unit: left drag even split with remainder, right drag one each, skips other items, respects the limit; resolves on button release in Game |
| M33 | Inventory: number key swap | 6 | InventoryMenu.swapWithHotbar | [x] | G6 unit: swaps with hotbar slot only while the cursor is empty; uses the rebindable hotbar keys |
| M34 | Crafting result shift-click | crafts as many as possible | InventoryMenu.craftAll via shift-click | [x] | G6 unit: crafts as many as fit, never into a full inventory |
| M35 | Block display names | 1.21 names (Oak Planks, Oak Log, ...) | renamed 9 wood blocks from the pre-1.13 names ("Oak Wood", "Oak Wood Planks") | [x] | G6 live: tooltip shows the vanilla name |

## 2. HUD

| # | Element | Vanilla (rules 5) | Code | Status | Notes / commit |
|---|---|---|---|---|---|
| H1 | Hotbar sprite 182x22 + selection 24x23 | at (C-91, H-22), items at +3,+3 | the real hotbar sprites at the vanilla geometry | [x] | G2 live: real 182x22 hotbar + 24x23 selection frame |
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
| H13 | Crosshair | 15x15 sprite, inverted blend |  | [x] | G11 live: the real `hud/crosshair.png` drawn with `glBlendFuncSeparate(ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR, ONE, ZERO)`; it reads red on the blue sky |

## 3. Inventory and crafting UI

| # | Element | Vanilla (rules 6) | Code | Status | Notes / commit |
|---|---|---|---|---|---|
| U1 | Panel size and scaling | 176x166 GUI px at the GUI scale | scaled panel, vanilla bevel look (GuiPainter) | [x] | G5 live + unit: 176x166 GUI px panel at the auto GUI scale (InventoryLayout/CookingLayout) |
| U2 | Slot size and spacing | 18 px pitch, slot art 16 px inside | 18 px pitch, 16 px item area | [x] | G5 unit InventoryLayoutTest.slotsSitAtVanillaGuiPixelPositions |
| U3 | Player inventory layout | armor column x=8,y=8 step 18; craft 2x2 at (98,18); out (154,28); offhand (77,62); storage (8,84); hotbar y=142 | layout matches except offhand/preview | [~] | G5: armor column (8,8 step 18), 2x2 grid (98,18), result (154,28), storage (8,84), hotbar y=142 all match. Still missing: offhand slot (77,62) and the player-model preview (an empty inset box is drawn) |
| U4 | Crafting table layout | 3x3 at (30,17), out (124,35) |  | [x] | G5 live + unit: 3x3 at (30,17), result (124,35); no armor slots at the table |
| U5 | Furnace layout | in (56,17), fuel (56,53), out (116,35), flame (56,36), arrow (79,34) |  | [x] | G5 live + unit CookingLayoutTest: in (56,17), fuel (56,53), out (116,35), flame (56,36), arrow (79,34); lit flame and half-filled arrow seen |
| U6 | Panel and slot look | light-grey panel with bevel, inset slots | the real `container/*.png` backgrounds (slot frames, arrows and preview box are baked into them) | [x] | live: real inventory / crafting table / furnace backgrounds |
| U7 | Hover highlight, held item, tooltip | white 50% square on the slot; item at cursor; dark-purple tooltip | tooltip colours from rules 6 are [U] | [x] | G5 live: held item drawn at 16 px x scale centred on the cursor; G11: the real `slot_highlight_back/front` sprites (hovered interior measured 139 -> 192) and the real `tooltip/background` + `tooltip/frame` 9-slice sprites |
| U8 | Labels | Crafting / Inventory text colour 0x404040 |  | [x] | G5 live: labels in 0x404040 ('Crafting' at (97,8) / (28,6), 'Inventory' at (8,72), furnace title centred) |

## 4. Blocks and items (one row per entry in `BlockType`)

Columns: stack size, durability, mining/drop result, icon. Icon status refers to the 16x16 art, which is always the real texture file (the game draws no art of its own). `Hardness` is checked against the verified table.

| Item | Kind | Stack | Dur. | Hardness / mining / drop | Icon | Status | Notes / commit |
|---|---|---:|---:|---|---|---|---|
| Stone (`STONE`) | block | 64 | | 1.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Grass Block (`GRASS_BLOCK`) | block | 64 | | 0.6 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Dirt (`DIRT`) | block | 64 | | 0.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Cobblestone (`COBBLESTONE`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Oak Wood Planks (`OAK_PLANKS`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Bedrock (`BEDROCK`) | block | 64 | | -1 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Water (`WATER`) | block | 64 | | -1 (vanilla 100, never targeted) [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Sand (`SAND`) | block | 64 | | 0.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Gravel (`GRAVEL`) | block | 64 | | 0.6 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Gold Ore (`GOLD_ORE`) | block | 64 | | 3 ok | real texture | [x] | G4 live (world wall, trees, inventory). drops raw item (G1) |
| Iron Ore (`IRON_ORE`) | block | 64 | | 3 ok | real texture | [x] | G4 live (world wall, trees, inventory). drops raw item (G1) |
| Coal Ore (`COAL_ORE`) | block | 64 | | 3 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Oak Wood (`OAK_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Oak Leaves (`OAK_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Glass (`GLASS`) | block | 64 | | 0.3 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Sandstone (`SANDSTONE`) | block | 64 | | 0.8 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Block of Gold (`GOLD_BLOCK`) | block | 64 | | 3 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Block of Iron (`IRON_BLOCK`) | block | 64 | | 5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Bricks (`BRICKS`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| TNT (`TNT`) | block | 64 | | 0 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Bookshelf (`BOOKSHELF`) | block | 64 | | 1.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Mossy Cobblestone (`MOSSY_COBBLESTONE`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Obsidian (`OBSIDIAN`) | block | 64 | | 50 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Chest (`CHEST`) | block | 64 | | 2.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Diamond Ore (`DIAMOND_ORE`) | block | 64 | | 3 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Block of Diamond (`DIAMOND_BLOCK`) | block | 64 | | 5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Crafting Table (`CRAFTING_TABLE`) | block | 64 | | 2.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Furnace (`FURNACE`) | block | 64 | | 3.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Ice (`ICE`) | block | 64 | | 0.5 [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Snow Block (`SNOW_BLOCK`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Clay (`CLAY`) | block | 64 | | 0.6 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Pumpkin (`PUMPKIN`) | block | 64 | | 1 ok | real texture | [x] | G4 live (world wall, trees, inventory). axe is the fast tool (G1) |
| Netherrack (`NETHERRACK`) | block | 64 | | 0.4 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Glowstone (`GLOWSTONE`) | block | 64 | | 0.3 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Terracotta (`TERRACOTTA`) | block | 64 | | 1.25 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Red Sand (`RED_SAND`) | block | 64 | | 0.5 [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Birch Wood (`BIRCH_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Birch Leaves (`BIRCH_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Spruce Wood (`SPRUCE_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Spruce Leaves (`SPRUCE_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Jungle Wood (`JUNGLE_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Jungle Leaves (`JUNGLE_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Acacia Wood (`ACACIA_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Acacia Leaves (`ACACIA_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Mangrove Wood (`MANGROVE_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Mangrove Leaves (`MANGROVE_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Dark Oak Wood (`DARK_OAK_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Dark Oak Leaves (`DARK_OAK_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Cherry Wood (`CHERRY_LOG`) | block | 64 | | 2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Cherry Leaves (`CHERRY_LEAVES`) | block | 64 | | 0.2 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Mud (`MUD`) | block | 64 | | 0.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Mangrove Roots (`MANGROVE_ROOTS`) | block | 64 | | 0.7 [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Oak Sapling (`OAK_SAPLING_STAGE_0`) | block | 64 | | 0 (instabreak) [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Stick (`STICK`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Wooden Pickaxe (`WOODEN_PICKAXE`) | item | 1 | 59 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Stone Pickaxe (`STONE_PICKAXE`) | item | 1 | 131 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Pickaxe (`IRON_PICKAXE`) | item | 1 | 250 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Wooden Axe (`WOODEN_AXE`) | item | 1 | 59 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Stone Axe (`STONE_AXE`) | item | 1 | 131 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Axe (`IRON_AXE`) | item | 1 | 250 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Wooden Shovel (`WOODEN_SHOVEL`) | item | 1 | 59 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Stone Shovel (`STONE_SHOVEL`) | item | 1 | 131 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Shovel (`IRON_SHOVEL`) | item | 1 | 250 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Apple (`APPLE`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Bread (`BREAD`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Beef (`COOKED_BEEF`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Blast Furnace (`BLAST_FURNACE`) | block | 64 | | 3.5 ok | real texture | [x] | G4 live (world wall, trees, inventory). |
| Smoker (`SMOKER`) | block | 64 | | 3.5 [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Copper Ore (`COPPER_ORE`) | block | 64 | | 3 (a copy of iron ore) [V] | real texture | [x] | G4 live (world wall, trees, inventory). drops raw item (G1) |
| Smooth Stone (`SMOOTH_STONE`) | block | 64 | | 2 [V] | real texture | [x] | G4 live (world wall, trees, inventory). |
| Coal (`COAL`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Charcoal (`CHARCOAL`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Ingot (`IRON_INGOT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Gold Ingot (`GOLD_INGOT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Copper Ingot (`COPPER_INGOT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Nugget (`IRON_NUGGET`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Brick (`BRICK`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Iron (`RAW_IRON`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Gold (`RAW_GOLD`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Copper (`RAW_COPPER`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Beef (`RAW_BEEF`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Porkchop (`RAW_PORKCHOP`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Porkchop (`COOKED_PORKCHOP`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Chicken (`RAW_CHICKEN`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Chicken (`COOKED_CHICKEN`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Mutton (`RAW_MUTTON`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Mutton (`COOKED_MUTTON`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Rabbit (`RAW_RABBIT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Rabbit (`COOKED_RABBIT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Cod (`COD`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Cod (`COOKED_COD`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Raw Salmon (`SALMON`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Cooked Salmon (`COOKED_SALMON`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Potato (`POTATO`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Baked Potato (`BAKED_POTATO`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Kelp (`KELP`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Dried Kelp (`DRIED_KELP`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Rotten Flesh (`ROTTEN_FLESH`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Wooden Sword (`WOODEN_SWORD`) | item | 1 | 59 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Stone Sword (`STONE_SWORD`) | item | 1 | 131 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Sword (`IRON_SWORD`) | item | 1 | 250 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Leather (`LEATHER`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Feather (`FEATHER`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Egg (`EGG`) | item | 16 | | item | real texture | [x] | G3 live (inventory + hotbar). stack 16 fixed (G1) |
| Leather Cap (`LEATHER_HELMET`) | item | 1 | 55 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Leather Tunic (`LEATHER_CHESTPLATE`) | item | 1 | 80 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Leather Pants (`LEATHER_LEGGINGS`) | item | 1 | 75 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Leather Boots (`LEATHER_BOOTS`) | item | 1 | 65 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Helmet (`IRON_HELMET`) | item | 1 | 165 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Chestplate (`IRON_CHESTPLATE`) | item | 1 | 240 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Leggings (`IRON_LEGGINGS`) | item | 1 | 225 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Iron Boots (`IRON_BOOTS`) | item | 1 | 195 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Golden Helmet (`GOLD_HELMET`) | item | 1 | 77 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Golden Chestplate (`GOLD_CHESTPLATE`) | item | 1 | 112 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Golden Leggings (`GOLD_LEGGINGS`) | item | 1 | 105 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Golden Boots (`GOLD_BOOTS`) | item | 1 | 91 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond (`DIAMOND`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Sword (`DIAMOND_SWORD`) | item | 1 | 1561 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Axe (`DIAMOND_AXE`) | item | 1 | 1561 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Pickaxe (`DIAMOND_PICKAXE`) | item | 1 | 1561 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Shovel (`DIAMOND_SHOVEL`) | item | 1 | 1561 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Helmet (`DIAMOND_HELMET`) | item | 1 | 363 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Chestplate (`DIAMOND_CHESTPLATE`) | item | 1 | 528 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Leggings (`DIAMOND_LEGGINGS`) | item | 1 | 495 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Diamond Boots (`DIAMOND_BOOTS`) | item | 1 | 429 | item | real texture | [x] | G3 live (inventory + hotbar). |
| Flint (`FLINT`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Clay Ball (`CLAY_BALL`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Snowball (`SNOWBALL`) | item | 16 | | item | real texture | [x] | G3 live (inventory + hotbar). stack 16 fixed (G1) |
| Glowstone Dust (`GLOWSTONE_DUST`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Book (`BOOK`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Gunpowder (`GUNPOWDER`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Bone (`BONE`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Arrow (`ARROW`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| String (`STRING`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |
| Spider Eye (`SPIDER_EYE`) | item | 64 | | item | real texture | [x] | G3 live (inventory + hotbar). |

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

## 5. Session status (resume here)

Phase 3 task picked: **finish the asset restore** (README / `scripts/restore-assets.py` / `docs/ASSETS.md` were never committed and the
live pass of the restored assets had not been run). It was the last unfinished request: the script existed but the download had been
blocked on the work network. The assets have since been restored on this machine (2,860 textures, 228 sounds).

Verified live (G7, 2026-10-14): all 228 sounds are Ogg Vorbis, all 2,702 PNGs have a valid header, the game log shows no texture decode
or sound-load warnings after breaking and placing blocks, the real item icons / hearts / hunger / hotbar render, and `options.txt`
applies (render distance 12 loaded 441 chunks, 3 loaded 29).

- [~] **Pig, cow and chicken skins**: Minecraft 1.21.5+ renamed them (`entity/pig/temperate_pig.png`, `entity/cow/temperate_cow.png`,
  `entity/chicken/temperate_chicken.png`, source: Mojang's 1.21.5 changelog via Perplexity). The game and the script now accept both names, but
  the three files are not on disk yet, so those mobs still draw flat placeholder colours. **Next step:** on an unfiltered network run
  `python scripts/restore-assets.py` (idempotent), then look at the real file sizes: the pig and cow got larger and may have a different UV layout,
  in which case the box UVs in `MobModelBuilder` (pig/cow/chicken) need re-mapping. Not verifiable without the files.
- Precedence note: there is no generated art any more (BlockArt, ItemIcons and HudSprites were deleted); every tile, icon, HUD sprite, container background, particle, sun, moon, rain and the bare hand comes from `textures/`, restored by `scripts/restore-assets.py`.

## 6. Fix batch 2 (user-reported bugs)

All verified live (screenshots in `test-output/screenshots/`) and by unit tests where a screenshot cannot show it.

- [x] Pig, cow and chicken skins: real 26.x textures (`*_temperate.png`) load; pig and cow keep the old box layout on a 64x64 canvas.
- [x] Block-break cracks are black (the supplied crack textures are converted instead of drawn with white highlights).
- [x] Furnace / crafting-table / inventory arrows have the real shapes (22x15, 22x15, 16x13), measured pixel by pixel from the wiki's GUI images; the unlit flame is not drawn.
- [x] Tool durability: all 16 tools and sword hits match vanilla (1 per block, sword 2 per block, 1 per mob hit).
- [x] Fuel: 1 coal = 8 items in furnace, blast furnace and smoker; planks 1.5, sticks 0.5; wooden tools, saplings, crafting tables, chests, bookshelves and mangrove roots burn; progress drains 2 per tick when the fire is out.
- [x] First-person hand and held items use vanilla's transforms (right-hand anchor 0.56/-0.52/-0.72, attack rotations, tool display transform, held blocks at scale 0.4); flat items are extruded 1/16 thick; the empty hand is the 4x12x4 px arm; the swing lasts 0.3 s; the hand uses the player's FOV.
- Held tools use the JSON's rotation [0,-90,25] (an earlier +90 was a mistake, corrected in section 10); baby pig/cow/chicken textures are used since section 9.

## 7. Fix batch 3 (user-reported)

Live evidence in `test-output/screenshots/` (not committed). Sources for the numbers: Perplexity research on the decompiled
1.21 `CritParticle` / `TrackingEmitter` / `Player.attack` / `ItemEntityRenderer` (tag [P]); the textures themselves are the 26.3 client jar's.

- [x] All generated art deleted (`BlockArt`, `ItemIcons`, `HudSprites`, the procedural atlas, crack and HUD fallbacks, the arrow/flame/slot drawing in `GuiPainter`); missing files show vanilla's magenta-and-black square or are skipped.
- [x] Tiles that had no real texture now use one: blast furnace, smoker, copper ore and smooth stone (block files), chest (cut from `entity/chest/normal.png`), bare hand (real arm rectangle of `steve.png`), sun, moon and rain (`environment/`).
- [x] Night motes removed (they were invented, not vanilla).
- [x] Rain is vanilla's: one camera-facing quad per block column within 10 blocks, real `rain.png`, scrolling per column. No per-column alpha fade yet (the shader has only a global alpha).
- [x] Sun and moon draw additively with the real textures (vanilla's blend). The moon is always the full moon: the game has no day counter yet.
- [x] Q drops one item (Ctrl+Q the stack); the dropped item is the real sprite extruded 1/16 thick at the ground display scale 0.5 (blocks stay 0.25 cubes). Counts above 1 are still one model (vanilla shows up to 5 copies).
- [x] Critical hits: the rule was already right (charge >= 0.9, falling, not on ground / swimming / flying / sprint-hit, x1.5). Now the victim gets vanilla's tracking emitter (3 ticks, 16 tries per tick, `particle/critical_hit.png`), and a hit that takes more than 2 health spawns (int)(damage/2) dark hearts (`particle/damage.png`, 20 ticks, rising).
- [x] Equip animation: changing the held item lowers the old one then raises the new one (0.15 s each, ease-out), for flat items, blocks and the bare hand; the hand also rises when a world opens. No offhand exists, so only the main hand animates.
- [x] Inventory, crafting table, furnace, blast furnace and smoker use the real container textures and the real lit/burn progress sprites.
- [x] Grayscale PNGs are read with their stored values (Java's `getRGB` lightened them).
- Known gaps at that time: moon phases, rain edge fade, multiple copies of a dropped stack, the preview and the offhand slot (all done in sections 8 and 9).

## 8. Offhand and player preview

Sources: Perplexity research on the decompiled 1.21 `Inventory`, `InventoryScreen`, `ItemInHandRenderer` and `Gui` plus minecraft.wiki Controls/Inventory (tag [P]); live checks in `test-output/screenshots/`.

- [x] Offhand slot: separate from the 36 normal slots, saved as one extra slot after them (old saves still load), dropped on death. Pick-ups top up a matching offhand stack but never fill an empty one.
- [x] F (`key.swapOffhand`) swaps the selected hotbar stack with the offhand; over a slot in the inventory screen it swaps that slot with the offhand. Shift-click on the offhand slot sends its stack back to the inventory.
- [x] Right click: main hand first, offhand when the main hand passes (empty hand, a non-placeable item, a block with nothing to aim at). Verified live: an offhand block is placed with an empty main hand, offhand food is eaten, a main-hand block wins over offhand food.
- [x] HUD: offhand slot (`hotbar_offhand_left.png`, 29x24 at centre-120, bottom-23) with the item at (centre-117, bottom-19), shown only while it holds something. Left-main-arm option does not exist here, so the right-hand variant is unused.
- [x] First person: the offhand item is the mirrored right-hand view with its own equip animation; no bare left arm; attacking is always the main hand.
- [x] Inventory screen: offhand slot at (77,62) in the player inventory only.
- [x] Player preview: the real Steve skin with its outer layers and the worn armor (humanoid / humanoid_leggings textures, leather tinted 0xA06540 plus its overlay) in the box (26,8)-(75,78), 30 GUI pixels per block, body yaw 20 and head yaw 40 degrees per atan((centre - mouse) / 40), pitch 20 degrees, full bright. The 32-pixel model is scaled by 15/16 like the player renderer.
- Known gaps at that time: held items on the preview, idle arm sway, leather icon tint (done in section 9); armor has no enchantment glint (no enchanting exists here).

## 9. Sky, babies, leather, rain, stacks, preview extras

Sources: Perplexity research on `SkyRenderer` / `LevelRenderer.renderSky`, `AgeableMob`, `ItemEntityRenderer`, `ItemInHandLayer` (tag [P]); the baby and adult model boxes are read from the 26.3 client jar itself (`javap` on `BabyPigModel`, `BabyCowModel`, `BabyChickenModel`, `CowModel`, `AdultChickenModel`), which is unobfuscated.

- [x] Sun and moon size: vanilla's quads are 60 (sun) and 40 (moon) blocks wide at distance 100; they are drawn at the same angular size at the game's sky distance of 180 (sun half-size 54, moon 36). They were about 4x too small.
- [x] Moon phases: 8 sprites in vanilla's order (full, waning gibbous, third quarter, waning crescent, new, waxing crescent, first quarter, waxing gibbous), chosen by the number of the current day, which changes at sunrise like vanilla's day count. The day count is saved in `world.dat` (an optional trailing section; old saves start at day 0). `setTime?time=&day=` sets it for tests. The moon quad uses the sprite with both axes reversed, as 26.x does.
- [x] Baby pig, cow and chicken: dedicated models and textures (`*_temperate_baby.png`), box for box from the jar; hitbox half the adult's; grow up after 1200 s (24000 ticks); no drops and no experience; saved as minus their remaining age; later members of a naturally spawned group are babies with a 5% chance (the first is always an adult); chicks lay no eggs. `spawnMob?type=pig_baby` (also cow_baby, chicken_baby) for tests.
- [x] Leather armor icons: the grey item texture is multiplied by the default dye 0xA06540 and the `_overlay` sprite is laid over it, in the inventory, hotbar, hand and on the ground.
- [x] Rain fades towards its edge like vanilla (alpha = ((1 - d^2) * 0.5 + 0.5) x rain level), drawn in six alpha steps because the shader has one alpha per draw.
- [x] Dropped stacks draw 1, 2, 3, 4 or 5 copies (counts 1 / 2-16 / 17-32 / 33-48 / 49+) with small fixed random offsets. [P] The offset sizes are approximate.
- [x] Inventory preview: held items are drawn in the hands (vanilla's ItemInHandLayer: out along the arm, then the item's third-person display transform), and the arms sway idly (AnimationUtils.bobModelPart).
- [x] Fixed a regression: the lazily loaded real textures shared the mob-skin map, so the mob skins never loaded and every mob was invisible. They now have their own map.
- Known gaps: baby sounds are not pitched up; baby animals do not follow adults; no breeding; moon quad orientation per phase is unverified against vanilla.

## 10. Hand light, slot outlines, preview direction, tools, pickup animation

Reference images: minecraft.wiki `DualWielding.png` (a real first-person iron sword, 1920x1017) and `OffhandSlotHighlighted.png` (the real inventory with the empty-slot outlines), both looked at in the browser.

- [x] First-person hands (main, offhand, bare arm) are lit like the world: the sky and block light at the player's eye, the mesh's baked face brightness on the sky part. Checked at day and at night (both swords go dark at night).
- [x] Empty armor slots show the helmet / chestplate / leggings / boots outlines and an empty offhand slot shows the shield outline (the real `container/slot/*.png` sprites), as in the reference.
- [x] The preview player follows the mouse in the same direction: the head had been turning the wrong way (cancelling the body's turn) and looked up when the mouse was below. Now body 20 and head 40 degrees per atan unit towards the mouse, head up when the mouse is above, body leaning back; covered by `PlayerModelBuilderTest`.
- [x] Held tools: the flat-item display rotation is the model JSON's [0,-90,25]. I measured it: the simulated left-edge silhouette of the iron sword matches the reference image row for row once the reference's wider screen (aspect 1.887 against 1.778) is allowed for (a uniform 42 px shift, which is exactly the aspect difference). The +90 used since the held-item rework was the cause of the tools looking wrong.
- [x] Pickup animation: a collected stack keeps spinning and flies to the player in 0.15 s (3 ticks) easing in (position x f^2 with f = t / 0.15), then is gone; the inventory still receives it at once. `ItemEntityManager.drainCollected()` reports what was taken (a partly collected stack reports only the taken part).

## 11. Menus drawn only from the real files (G11)

- [x] The font is the real `textures/font/ascii.png`: `PixelFont` measures each glyph (width = rightmost lit column + 1, advance +1, space 4) instead of carrying drawn glyphs. Without the file nothing is drawn and layouts still work (advance 6).
- [x] Buttons, sliders (track + handle with their own borders), text fields, the world-list scrollbar, the tooltip and the slot highlight are the real 9-slice sprites (`ui/UiSprites`, borders from the `.mcmeta` files: button 3, slider 1, handle 2/2/2/3, text field 1, scroller 1, tooltip 9/10). Button text is white, grey when disabled. Slider handle position checked: FOV 70 sits at 50%.
- [x] The title screen shows the real `title/minecraft.png` logo (256x44 at y=30), the `edition.png` plate, and a splash picked from the real `texts/splashes.txt` (yellow, rotated -20 degrees, pulsing). The invented block-letter logo, hill silhouette and splash list are gone.
- [x] **Limit:** in this jar the `panorama_*.png` files are 1x1 stubs, and `menu_background.png` is a 16x16 black at alpha 64 meant to sit over the panorama. So the title screen is the sky colour with that faint overlay, which is what the real files give; it needs the real panorama images to look like vanilla.
- [x] Scrolling lists are clipped with a GL scissor (`UiRenderer.setClip`) instead of being hidden under opaque strips, because the real backgrounds are translucent.
- [x] XP orbs use the real `entity/experience/experience_orb.png` (16 icons of 16x16 chosen by value as in vanilla, 0.3-block camera-facing quads, green-yellow pulse). The tinted snow tile is gone.
- [x] The crosshair is the real sprite with vanilla's inverting blend.
- Tests: 747 passing (`PixelFontTest` and `UiSpritesTest` are new; the font tests use a made-up sheet so they do not depend on the real file).

## 12. Held-item animations and dropped items (G12)

All numbers below were read from the 26.3 client jar with `javap` (FirstPersonHandsAndItemsRenderer, ItemEntityRenderer, ItemEntity, LivingEntity$SwingState, Minecraft.continueAttack), not from memory.

- [x] Eating motion (`applyEatTransform`): the item rises to the mouth by f3 = 1 - progress^27 (0.6 across, 0.5 down, rotated 90 about y, 10 about x, 30 about z) and bobs by |cos(f / 4 * pi)| * 0.1 once 20% of the use time is gone; the hand does not swing while using. Offhand food mirrors it. Shown live with cooked beef (hold use for 1.6 s). `HeldItemModel.eat`, tested.
- [x] Hand swing restart rule (`SwingState.startIfAble`): a swing only restarts when none is running, or the running one is past half (3 of 6 ticks). Holding the mine button calls swing every tick, so it plays quick half swings every 4 ticks (0.2 s) instead of full swings every 0.3 s. Mining hit sounds now follow vanilla's every-4-ticks rhythm, starting with the first tick.
- [x] Dropped items: the model's lowest point floats 0.0625 + (sin(ageTicks / 10 + phase) * 0.1 + 0.1) above the entity (so 0.0625 to 0.2625, always, also in the air), and spins ageTicks / 20 + phase radians (1 rad/s); the phase is per item (vanilla: random in [0, 2 pi)). Before: a 0.03 bob, only on the ground, and 1.6 rad/s with a common phase.
- [x] Held blocks and tools use the model JSON display transforms (blocks [0,45,0] x 0.4, flat items [0,-90,25] + [1.13,3.2,1.13] x 0.68, verified earlier against a real screenshot).
- New test-controller command `holdUse?held=true|false` (the real eating path, as a player holding right click).
- Not done: drinking (no potions or milk exist), bow / crossbow / shield use poses (no such items), item-use cooldown overlay.
- Tests: 751 passing.

## 13. Mob animation, head turning and babies (G13)

Rules read from the 26.3 client with `javap` (LookControl, BodyRotationControl, WalkAnimationState, LivingEntity.updateWalkAnimation, QuadrupedModel / HumanoidModel / ChickenModel / CreeperModel `setupAnim`, AnimationUtils.animateZombieArms, Chicken.aiStep, FollowParentGoal, the animals' `registerGoals`, getVoicePitch).

- [x] Walk animation: vanilla's WalkAnimationState (20 Hz: speed eases to min(4 * distance per tick, 1) by 0.4, position += speed, babies x3) and real limb rotation about the hip: legs swing by cos(pos * 0.6662) * 1.4 * speed (pig, cow, creeper diagonal pairs; chicken; humanoid legs, humanoid arms by cos * speed). Before: legs were slid and lifted. The part pivots come from the models' part offsets (cow front legs z = -5, a 1 px fix).
- [x] Head and body: `HeadControl` runs LookControl (head turns at most 10 degrees per tick toward the target, pitch up to 40, a look request lasts 2 ticks, head kept within 75 degrees of the body while walking) and BodyRotationControl (the body follows the movement heading while moving; standing still it follows the head only past 75 degrees, and re-aligns after the head held still for 10 ticks). Look-at-player (6 blocks, 2-4 s) and random look-around (2% per tick, 1-2 s) now turn the head, not the whole body; hostile mobs also look at the player's eyes. Rendering interpolates between ticks.
- [x] Zombie arms: raised 80 degrees (120 while it has a target) with the idle bob, as animateZombieArms; skeleton arms hang with the held-item pose and raise to the bow pose (-90 degrees plus head pitch, yaw -0.1 / 0.5 plus head yaw) while attacking.
- [x] Chicken wings flap like Chicken.aiStep (flapSpeed +1.2 per tick in the air, -0.3 on the ground, flapping decays by 0.9, wings open by (sin(flap) + 1) * flapSpeed).
- [x] Baby voice: say / hurt / death at pitch x1.5 (vanilla getVoicePitch 1.5 against 1.0); footsteps keep their pitch.
- [x] Babies follow their parent (FollowParentGoal: nearest adult of the same kind within 8 blocks and 4 vertically, not closer than 3, kept up to 16 blocks, x1.1, cow x1.25). Goals that steer the mob now share vanilla's MOVE flag, so a higher-priority one (panic, then follow, then stroll) blocks the lower ones; before, the stroll goal could override.
- Not done: spider leg animation (still the old sway), baby zombie model (still a half-scale adult), breeding, mob attack swing for zombies, the 26.x animation-keyframe walk if any mob uses it.
- Tests: 770 passing (`HeadAndWalkTest`, `MobModelPoseTest`, `BabyBehaviourTest` are new).

## 14. Sky and weather (G14)

All numbers from the 26.3 client (SkyRenderer.buildStars, CloudRenderer, `clouds.vsh`, `AtmosphericFogEnvironment`, `ClientLevel.tickWeatherEffects`, `WaterDropParticle`) and its data (`timeline/day.json`, `dimension_type/overworld.json`).

- [x] Stars: the vanilla field (seed 10842, 1500 tries, those with squared length outside (0.01, 1) dropped, 100 blocks away, half-size 0.15 + 0.1 * random, random roll), turning with the sun and moon. Brightness is the timeline's `star_brightness` keyframes (0 by day, 0.5 from tick 13228 to 22772, ramping in between) times (1 - rain). `SkyDetails.buildStars`.
- [x] Clouds from the real `clouds.png`: 12 x 4 x 12 block boxes per cloudy texel (alpha > 0) at height 192.33, the pattern repeating every 256 cells, drifting 0.03 blocks per tick toward -X with a 3.96 block z offset, face shades top 1.0 / sides 0.9 and 0.8 / bottom 0.7, colour `#ccffffff` (80% opaque) times the timeline's cloud colour (white by day, `#191926` at night). Drawn in two passes (depth first) so only the nearest surface is blended.
- **Differences:** the field is 24 cells (288 blocks) wide with only the outer rim fading out, because vanilla's fog end is up to 2048 blocks and draws every cell on the GPU; the fast (flat) cloud mode and the cloud options do not exist; vanilla's cloud colour has no rain darkening in the data I found, so none is applied.
- [x] Rain splashes: every tick (int)(0.225 * 21^2 * rain^2) tries (99 at full rain) choose a column within 10 blocks whose surface is within 10 blocks of the camera's height; a drop rises at 0.1-0.3 blocks per tick, gravity 0.06, 2% drag, lives 8 / (0.2..1.0) ticks, 50% to vanish each tick on the ground, drawn as 0.1-0.2 half-size quads with the real `splash_0..3` sprites. `RainSplashes`.
- Moon phase quad: each phase is its own real sprite under `celestial/moon/`, so the quad's texture orientation does not depend on the phase (the reversed axes found earlier apply to all of them).
- Not done: rain / snow particles for snow biomes, lava and campfire smoke in the rain, the rain sound variants by position, thunder flash tweaks, the sunrise / sunset glow (`sunrise_sunset_color` keyframes) and the real sky colour keyframes (the sky colour is still this game's own blend).
- Tests: 784 passing (`SkyDetailsTest`, `RainSplashesTest`).

## 15. Gold tools, hoes, void and the open numbers (G15)

- [x] Gold sword, axe, pickaxe and shovel and the wooden, stone, iron, gold and diamond hoes: new items with the real icons, recipes (hoes in both orientations), durability (gold 32, hoes by tier) and attack stats. Read from `Items` / `ToolMaterial` in the client: damage = 1 + tier bonus + the tool's own value, speed = 4 + the tool's own value. Gold sword 4 / 1.6, axe 7 / 1.0, pickaxe 2 / 1.2, shovel 2.5 / 1.0; every hoe deals 1 and swings at 1 / 2 / 3 / 4 (wood, stone, iron, diamond), gold 1. Gold mines at x12 but has mining level 0 (cannot harvest iron). Hoes mine nothing faster (no hay or sculk) and cannot till: there is no farmland yet. The 26.x client also has copper tools (190 uses, speed 5, bonus 1) and spears: not added.
- [x] Tier damage bonuses confirmed (wood 0, stone 1, iron 2, diamond 3, gold 0, netherite 4), as are the durabilities and speeds in docs/vanilla-rules.md 1.1.
- [x] Block hardness [U] rows resolved from `Blocks`: ice 0.5, red sand 0.5, mangrove roots 0.7, bookshelf 1.5, smoker 3.5, smooth stone 2 (resistance 6), sapling instant, copper ore = a copy of iron ore (3), water 100 (we use -1, the same in practice since water is never targeted).
- [x] Raw chicken is 2 hunger / 1.2 saturation (nutrition 2, saturation modifier 0.3): the code already had it. The other foods match too (beef 3 / 1.8, cooked beef 8 / 12.8, bread 5 / 6, rotten flesh 4 / 0.8, spider eye 2 / 3.2).
- [x] Death drops 7 x level experience, at most 100 (`Player.getBaseExperienceReward`), as the doc says.
- [x] Void: below y = (world minimum - 64) a living thing takes 4 damage whenever the hurt cooldown allows (`Entity.checkBelowWorld`, `LivingEntity.onBelowWorld`). Now wired for the player at y < -64 (this world's minimum is 0), `DamageSource.VOID`; the doc's "-128" was the 1.17 value.
- [x] Hostile spawning: the overworld dimension data has `monster_spawn_block_light_limit` 0 and `monster_spawn_light_level` uniform 0..7, i.e. block light 0 and sky light at most a random 0-7, as in docs/vanilla-rules.md 5.
- **Still not modeled:** lightning damage and fire (bolts are only a flash), torches, buckets and the bow (they need new systems: block light sources, fluids, projectiles with charge), status effects (so no regeneration heart bounce).
- **JVM crash log `hs_err_pid27744.log`** (2026-10-14 12:54): an EXCEPTION_ACCESS_VIOLATION inside `jvm.dll` on the "C2 CompilerThread1" thread while it was compiling `Game.renderHeldItem` (OpenJDK 25.0.2+10). The crash is in HotSpot's optimizing compiler, not in this game's Java or in LWJGL / OpenGL (no Java or native game frame is on the stack), and it has not come back in many runs since. Workarounds if it returns: a newer JDK 25 update, or `-XX:CompileCommand=exclude,com.example.game.Game::renderHeldItem` for the run.
- Tests: 790 passing (`GoldToolsAndHoesTest`, the void test).

