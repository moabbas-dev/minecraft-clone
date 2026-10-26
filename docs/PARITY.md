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

- [x] First-person hands (main, offhand, bare arm) are lit like the world: the sky and block light at the player's eye, the mesh's baked face brightness on the sky part (replaced in section 16: the light goes through the lightmap and the shader shades from the face normals). Checked at day and at night (both swords go dark at night).
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
- Not done at the time (spider legs, the baby zombie model and breeding are now in section 23): mob attack swing for zombies, the 26.x animation-keyframe walk if any mob uses it.
- Tests: 770 passing (`HeadAndWalkTest`, `MobModelPoseTest`, `BabyBehaviourTest` are new).

## 14. Sky and weather (G14)

All numbers from the 26.3 client (SkyRenderer.buildStars, CloudRenderer, `clouds.vsh`, `AtmosphericFogEnvironment`, `ClientLevel.tickWeatherEffects`, `WaterDropParticle`) and its data (`timeline/day.json`, `dimension_type/overworld.json`).

- [x] Stars: the vanilla field (seed 10842, 1500 tries, those with squared length outside (0.01, 1) dropped, 100 blocks away, half-size 0.15 + 0.1 * random, random roll), turning with the sun and moon. Brightness is the timeline's `star_brightness` keyframes (0 by day, 0.5 from tick 13228 to 22772, ramping in between) times (1 - rain). `SkyDetails.buildStars`.
- [x] Clouds from the real `clouds.png`: 12 x 4 x 12 block boxes per cloudy texel (alpha > 0) at height 192.33, the pattern repeating every 256 cells, drifting 0.03 blocks per tick toward -X with a 3.96 block z offset, face shades top 1.0 / sides 0.9 and 0.8 / bottom 0.7, colour `#ccffffff` (80% opaque) times the timeline's cloud colour (white by day, `#191926` at night). Drawn in two passes (depth first) so only the nearest surface is blended.
- **Differences:** the field is 24 cells (288 blocks) wide with only the outer rim fading out, because vanilla's fog end is up to 2048 blocks and draws every cell on the GPU; the fast (flat) cloud mode and the cloud options do not exist; vanilla's cloud colour has no rain darkening in the data I found, so none is applied.
- [x] Rain splashes: every tick (int)(0.225 * 21^2 * rain^2) tries (99 at full rain) choose a column within 10 blocks whose surface is within 10 blocks of the camera's height; a drop rises at 0.1-0.3 blocks per tick, gravity 0.06, 2% drag, lives 8 / (0.2..1.0) ticks, 50% to vanish each tick on the ground, drawn as 0.1-0.2 half-size quads with the real `splash_0..3` sprites. `RainSplashes`.
- Moon phase quad: each phase is its own real sprite under `celestial/moon/`, so the quad's texture orientation does not depend on the phase (the reversed axes found earlier apply to all of them).
- Not done (snow columns and per-biome precipitation: section 30): rain / snow particles for snow biomes, lava and campfire smoke in the rain, the rain sound variants by position, thunder flash tweaks, the sunrise / sunset glow (`sunrise_sunset_color` keyframes) and the real sky colour keyframes (the sky colour is still this game's own blend).
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


## 16. The light system (G16)

Everything below was read from the 26.3 client jar with `javap` (lightmap.fsh, sample_lightmap.glsl, light.glsl, `LightmapRenderStateExtractor`, `BlockModelLighter` and its `AdjacencyInfo`, `LightEngine`, `BlockLightEngine`, `SkyLightEngine`, `BlockBehaviour` light dampening, `timeline/day.json`, `WeatherAttributes`), not from memory. Before this the light was a per-chunk recompute with a made-up `max(sky * daylight, block)` curve.

**Propagation (`world/LightEngine`, `LightProperties`)**
- [x] Block and sky light are one engine across the whole loaded world, not per chunk: a light crosses chunk borders both when a chunk is lit and when a block changes, and no light is left behind when a source goes (two queues: first the light that depended on the changed cells is removed, then what remains spreads back in; checked for a glowstone next to a chunk border).
- [x] A step loses `max(1, dampening)`; a block with dampening 15 stops light. Dampening: full opaque blocks 15; leaves, water, ice and mangrove roots 1; glass, plants and the chest 0 (the chest is not a full block). So light under a tree canopy or in water loses 1 per block, as in vanilla.
- [x] Sky light: every cell above the highest light-dampening block of its column is a source at 15 (no loss straight down an open column); roofs let light in sideways at 1 per block. Placing or breaking a block moves the column's blocker and relights what changed.
- [x] Emission: glowstone 15 (the only emitter among the blocks); a burning furnace, blast furnace or smoker gives 13 (`Blocks.litBlockEmission(13)`), set from `CookingStationManager.tickAll` through `World.setLightEmissionOverride` and removed with the block.
- Tests: `LightEngineTest` (16: dampening, L1 diamond falloff, 14-block reach, walls, removal without ghost light across a border, two sources, furnace on/off, roofs, skylight holes, leaves, water, border meeting, relight idempotence, remesh marking) and the rewritten `CrossChunkLightingTest`.

**Colours (`game/LightmapColors`, `LightmapTexture`, shader)**
- [x] The lightmap is lightmap.fsh: `get_brightness(l) = l / (4 - 3 l)`, sky brightness times the day timeline's `sky_light_factor`, block brightness times `1.4 + flicker`; the sky colour `sky_light_color` goes from white by day to `#7a7aff` at night (keyframes 730 / 11270 / 13140 / 22860 of the 24000-tick day); ambient `#0a0a0a`; block colour mixes the warm tint `#ffd88c` towards white by `0.9 (2 b - 1)^2`; clamp; then the Brightness option mixes with the `notGamma` curve (`1 - (1 - max)^4`). Rain pulls factor and colour toward night with alpha 0.3125, thunder with 0.527 (weather layers, scaled by the eased storm level).
- [x] The block-light flicker is vanilla's random walk, stepped 20 times a second: `f += (r1 - r2) r3 r4 0.1; f *= 0.9`.
- [x] The vertex shader samples the 16x16 lightmap (linear filter, texel centres) per vertex with the light levels the mesher stores (fractional where four cells were averaged), exactly like sample_lightmap.glsl. The old `uDaylightFactor`/`uGamma` uniforms are gone. Tests: `LightmapColorsTest` (9).

**Smooth lighting and ambient occlusion (`ChunkMeshBuilder.faceLight`)**
- [x] Each face vertex averages the light of the four cells in front of the face around it (edge, edge, diagonal, centre) with vanilla's rules: a diagonal hidden behind two opaque neighbours is replaced by the first edge (the corner tables are the jar's `AdjacencyInfo`), dark cells are filled in from the centre when the centre is dark, and the same cells give the occlusion shade (0.2 beside a full block, 1.0 beside air, glass, mud, water...). The cardinal shade (top 1.0, bottom 0.5, north/south 0.8, east/west 0.6) is multiplied in. Light-emitting blocks (glowstone) use flat lighting with the shade only; water uses the brighter of its cell and the one above; cross plants are lit by their own cell.
- [ ] [U] The exact `smoothBlend` dark-cell rule is from my reading of the bytecode (sky-only fix-up for cells that have block light but no sky); worth a second look against a screenshot of a cave corner.

**Entities, items, particles**
- [x] Mobs, dropped items, falling blocks and arrows are lit by the lightmap at their cell plus vanilla's two directional lights (`light.glsl`: `min(1, (max(0, n.L0) + max(0, n.L1)) 0.6 + 0.4)`, L0 = (0.2, 1, -0.7), L1 = (-0.2, 1, 0.7) normalised: top 1.0, north/south 0.742, east/west 0.498, bottom 0.4). The shader takes the normal from the screen-space derivatives of the position, so nothing is baked into the models any more. The first-person hands do the same in view space.
- [x] The inventory preview uses a full-bright lightmap with the inventory lights. [P] the signs of the two inventory light vectors were mapped from vanilla's y-down GUI space to our y-up one, not measured.
- [x] Particles, rain and its splashes use the lightmap without directional shading; the sky, sun, moon, stars, clouds and the crack overlay are drawn unlit.

**Not done / known gaps**
- (Fixed later, section 17: the mob spawners now use `sky_light_level`; entities are lit at their eye cell.) Lightning does not change the lightmap (it does not in vanilla's overworld either, only the sky colour).
- No night-vision, blindness or darkness effects (no source for them exists). Torches and lava are emitters now (sections 20 and 24).
- Light is recomputed when a chunk is first meshed and is not saved (by design: it is derived data).
- The [U] smoothBlend rule above was checked again against the bytecode in section 17 and holds; the two inventory light vectors are the jar's constants, only their mapping from the y-down GUI space is inferred.


## 17. Spawn light, eye-cell light and the smoothBlend check (G17)

- [x] `sky_light_level` as vanilla has it: `World.getSkyDarken` is 0 at noon and 11 on a clear night (the timeline's `sky_light_factor` and the weather layers); `getRawBrightness` = max(block light, sky light - skyDarken). `Monster.isDarkEnoughToSpawn`: block light 0 and a random 0..7 against the raw brightness (thunder compares with 10), in `MobSpawner.isValidSpawn(world, x, y, z, player, thundering, random)`; animals need raw brightness above 8 on grass.
- [x] Mobs are lit at the cell of their **eye position**, and a burning mob at block light 15 (`entity.getLightCoords`).
- [x] `smoothBlend` re-read: the fix-up of dark neighbours is `if (centre sky <= 2 || centre block <= 2)` per neighbour, sky-only when only the sky is zero - implemented that way in `ChunkMeshBuilder.fillDark`.
- Tests: `MobSpawnerTest` and `DayNightTest` updated.

## 18. Sky, fog, clouds and the sunrise from the real timeline (G18)

All from the 26.3 client (`SkyColors`, `timeline/day.json`, the biome `sky_color` / `fog_color` / `water_fog_color` values, `AtmosphericFogEnvironment`, `FogRenderer`, `SkyRenderer`):

- [x] The sky colour is the biome's `sky_color` times the day timeline's brightness curve, blended toward grey by rain and thunder (the weather layers: multiply and `BlendToGray`), and the flash of lightning lerps 0.22 toward (0.8, 0.8, 1). The fog colour is `#c0d8ff` (overworld) or the biome's, same layers. The clear colour is the fog colour, and the sky disc (radius 512 at +16) fades into it toward the horizon. The old `computeSkyColor` / `computeDaylightFactor` (this game's own blend) are gone.
- [x] The sun and moon angle is eased by the timeline's cubic bezier (`SkyDetails.sunAngleDegrees`; the sun crosses the horizon at ticks about 12542 and 23460), and the sunrise / sunset fan uses the 32 real `sunrise_sunset_color` keyframes (#AARRGGBB).
- [x] Fog: start = d - clamp(d / 10, 4, 64) for render distance d, end = d; rain adds the environment fog from -160 to 768 (strength eased, 0.5 in deserts, savannas and badlands, less without sky light); underwater -8 to 96 x max(0.25, vision); lava 0.25 to 1 (section 24). The cloud field is 48 cells in radius.
- [x] Biome tables (water colour, underwater fog, sky, fog) are the real values; the white lightning overlay was removed (vanilla only changes the sky colour). **Section 30:** the per-biome tables (`Biome`/`BiomeColors`) are gone; the values now come from each biome file's attributes through the probe's Gaussian blend.
- **Fixed in section 26:** the underwater fog now switches at once, swamp / mangrove water fog end x0.85 is applied, and the dark disc below the horizon is drawn.
- Tests: `DayNightTest`, `CelestialWeatherTest`, `BiomeAtmosphereTest` rewritten (`BiomeAtmosphereTest` replaced by `BiomeAttributesTest` in section 30).

## 19. Copper tools and the six spears (G19)

- [x] Copper sword, axe, pickaxe, shovel and hoe: stone stats, 190 uses, mining speed 5, real icons and recipes (copper ingots in the stone-tool shapes).
- [x] Spears (wood, stone, copper, iron, gold, diamond): jab only. Damage 1 + the tier bonus + the spear's own value: wood 1, stone 2, copper 2, iron 3, gold 1, diamond 4; attack speed 1 / swing 0.65, 0.75, 0.85, 0.95, 0.95, 1.05; one use per attack.
- **Not done:** the spear's charge, lunge and kinetic attack, and its held pose (jab only, shown as the flat icon like the other tools).
- Tests: `CopperToolsAndSpearsTest`.

## 20. Torches, farmland, hoes and random ticks (G20)

- [x] Torches: floor torch and four wall torches (`BlockSupport`: a torch needs a sturdy face, and drops as an item when it loses it), light 14, real model (a 2 x 10 x 2 px box, the wall one leaning 22.5 degrees), recipe coal or charcoal over a stick (4). Not done: the flame and smoke particles (needs a new sprite-particle emitter).
- [x] Farmland: a hoe turns grass or dirt into farmland (not from below, with the block above free), 15/16 high with the indented side. `RandomTicks` (3 random ticks per 16-cubed section per tick within 8 chunks) dries and wets it: moist with water within 4 blocks or rain, otherwise a dry one has a 1-in-7 chance each tick to become dirt, and any solid block above turns it to dirt. Moisture 0-7 is exact since section 26. Not done: trampling and crops (no farming exists).
- [x] Fixes found on the way: glass was walk-through (now solid); the translucent pass drew everything at alpha 0.85 - now the texture's own alpha (water 180, ice 190), and glass, plants and torches are cutouts in the opaque pass; a non-solid block can be placed where the player stands.
- Tests: `TorchAndFarmlandTest`.

## 21. Status effects, golden apple, lightning and burning (G21)

Numbers from `MobEffects`, `Foods`, `LightningBolt`, `Entity.baseTick` and the damage-type data in the 26.3 jar:

- [x] Effects (`StatusEffect`, `PlayerEffects`): hunger adds 0.005 x (amplifier + 1) exhaustion per tick; poison 1 damage every 25 >> amplifier ticks and never below 1 health; regeneration heals 1 every 50 >> amplifier ticks; absorption is 4 x (amplifier + 1) soaked up first. Food effects: rotten flesh hunger 600 ticks (80%), raw chicken hunger (30%), spider eye poison 100 ticks, golden apple regeneration II 100 ticks + absorption 2400 ticks (recipe: an apple in eight gold ingots).
- [x] HUD: poisoned hearts, the regeneration hop (`tick % ceil(max + 5)`), the absorption row, the effect icons (real `mob_effect/*.png`) with the 24 px background and the blink in the last 10 s.
- [x] Burning player: 1 damage a second (ignores armor), put out by water or rain, the `fire_1.png` overlay (flattened to two sheets, not the 3D wrap).
- [x] Real lightning (`entity/LightningBolts`): a bolt lives 2 ticks with the first flash and 1-3 more; 1 in 100000 per chunk per tick in a thunderstorm (prefers a living thing near); it hits everything within 3 blocks of 5 damage (reduced by armor) and 8 seconds of fire. The geometry is the jagged additive bolt; its algorithm was recalled and checked only for constants.
- [x] `World.monstersBurn` is the timeline's `monsters_burn` (true tick >= 23460 or < 12542): zombies and skeletons burn then, in sunlight only.
- **Not done:** creepers charged by lightning, fire blocks from lightning, night vision / blindness / darkness.
- Tests: `StatusEffectsTest`.

## 22. The bow (G22)

From `BowItem`, `AbstractArrow` and `Items` in the jar: draw power `(f^2 + 2f) / 3` capped at 1 (f = ticks / 20), nothing under 0.1, arrow speed 3 blocks a tick times the power, damage `ceil(speed x 2)` and a critical roll `nextInt(damage / 2 + 2) + damage` at full draw, 384 uses, one arrow and one use per shot, the pull textures at 0.65 and 0.9, and the FOV squeeze `1 - 0.15 f^2`.

- [x] Hold use with a bow (main hand) and an arrow in the inventory to draw, release to shoot; the player's arrows hurt mobs, not the player, and a lodged one can be picked up again. Recipes: arrow (flint over stick over feather, 4) and bow (sticks and strings, both orientations). Arrows are swept in quarter-block pieces now, which fixed fast arrows flying through walls and mobs.
- **Not done:** offhand bow use, crossbow, enchantments, arrow types, the arrow's own 3D model (the crossed-quad arrow is kept).
- Tests: `BowTest`.

## 23. Spider legs, the baby zombie model and breeding (G23)

- [x] Spider legs swing like `SpiderModel.setupAnim`: four pairs a quarter cycle apart (0, 180, 90, 270 degrees), yaw `-cos(2a + phase) x 0.4 x speed`, lift `|sin(a + phase) x 0.4| x speed` (a = walk position x 0.6662); the head turns with the look direction.
- [x] The baby zombie has its own model (`BabyZombieModel`: head 6 cubed with an 0.25 outer layer, a 4x5x2 body, 2x5x2 arms, 2x4x2 legs) and `zombie_baby.png`, no longer the adult at half scale. The test controller's `spawnMob?type=zombie_baby` makes one.
- [x] Breeding (`Animal`, `BreedGoal`): feed an adult its food with right click - pig carrot, potato or beetroot; cow wheat; chicken wheat or beetroot seeds - and it is in love for 600 ticks; two animals of one kind in love within 8 blocks walk to each other and after 60 ticks within 3 blocks they breed: a baby, 1-7 XP, 6000 ticks of waiting for both parents. Feeding a baby grows it up a tenth of its remaining time sooner.
- New items WHEAT, WHEAT_SEEDS, CARROT (food 3 / 3.6), BEETROOT (food 1 / 1.2), BEETROOT_SEEDS (ids 226-230, real icons). No farming exists, so they only come from the test controller (`giveItem`).
- **Not done:** the heart particles (`heart.png` exists; needs a new particle kind), mob-specific breeding sounds, and species other than pig, cow and chicken.
- Tests: `BreedingTest`, `SpiderAndBabyZombieModelTest`.

## 24. Fluids, buckets and lava (G24)

Read from `FlowingFluid`, `WaterFluid`, `LavaFluid`, `LiquidBlock`, `FluidRenderer` and `LavaFogEnvironment`:

- [x] Flowing fluid (`world/FluidSim`, ticked 20 times a second by the game): water tick delay 5, overworld lava 30 (four times that, three times in four, when a lava block rises); level loses 1 per block for water and 2 for lava (so lava reaches 3 blocks: 6, 4, 2); it goes down first and then sideways towards the nearest hole within 4 blocks (water) or 2 (lava); falling columns are full height; two water sources beside a cell with something solid or a source below make a source (lava does not); lava beside water or with water above becomes obsidian (a source) or cobblestone, and lava flowing down onto water makes stone. A cell in an unloaded chunk acts as a wall. Plants and torches in the way drop and are replaced.
- [x] Every state is its own block id (231-243), so saves need nothing new; `BlockType.isWater / isLava / isFluid / fluidAmount` replace the old `== WATER` checks everywhere (swimming, spawning, explosions, raycast, farmland moisture, fall damage, light dampening 1).
- [x] Rendering: corner heights averaged over the four cells at each corner (a nearly full cell weighs ten times, 1 when the fluid is above), sloped surfaces, side faces cut to those heights with the flowing sprite, the still sprite on top (the flowing one, turned in quarter turns to the flow, where it moves), 0.001 inset, no face against the same fluid. Lava is lit 15 and drawn opaque; water is tinted by the biome and blended.
- [x] Buckets: recipe three iron ingots, scoop a still source (the empty bucket's ray stops at sources), pour into the aimed cell (the clicked block when a plant, else the cell in front of the face); empty buckets stack to 16, full ones to 1.
- [x] Lava: 4 damage per hit (armor reduces it, `DamageSource.LAVA`) and 15 seconds of fire for the player and for mobs; the dark red fog (#991900, section 26) seen from 0.25 to 1 block; swimming in lava is 0.4 x the water speed (drag 0.5 against 0.8) and does not lift the swimmer. Thin flowing water (surface less than 0.3 above the feet) is waded, not swum.
- **Differences / not done:** (the water and lava textures are animated since section 27) the top texture is turned in 90 degree steps, not smoothly; fluids do not push entities; lava does not start fires or burn items; no bucket, fizz or splash sounds (none among the user's files); no waterlogging, cauldrons, milk, powder snow or fish buckets; scheduled fluid ticks are not saved, but unsettled fluid is woken again when its chunk loads (section 26); the nether lava numbers (delay 10, spread 4) are not used.
- Tests: `FluidSimTest` (11), `BucketTest` (4).

## 25. What is still wrong or open (end of the "fix everything" day)

- Title-screen panorama: the files in the user's jar are 1x1 stubs, so it cannot be drawn.
- No farming (crops, trampling), so the new food and seed items are test-controller only; no torch / flame particles or heart particles (snow-biome snow columns: section 30).
- The spear has no charge or lunge; the bow has no offhand use.
- Fire blocks, charged creepers, night vision, blindness and darkness are missing.
- The flowing fluid top is turned in quarter turns only (the atlas tile cannot be sampled at other angles without bleeding); the 3D burning overlay is exact (section 27).
- Tests: 889 passing.

## 26. Fix pass: farmland moisture, fogs, dark disc, fluid reload (G26)

Each item was checked on minecraft.wiki (Farmland, Fog, Biome tag (Java Edition), Void; pages opened in the browser) and then against the 26.3 jar for the exact numbers, and tested in a seeded regular world as well as the flat one.

- [x] **Farmland moisture 0-7** (wiki: Farmland, "Block states": moisture 0-7, only 7 shows the wet texture, newly hydrated farmland jumps to 7; hydration: water within 4 blocks horizontally, from the farmland's own level to one above, or rain). `FarmlandBlock.randomTick` in the jar: near water or rain and moisture below 7 -> 7; otherwise moisture above 0 -> one less; otherwise (no crop) -> dirt. Blocks FARMLAND (0), FARMLAND_1..6 (ids 247-252) and FARMLAND_MOIST (7). Replaces the 1-in-7 approximation.
- [x] **Lava fog colour** was wrong (0.6, 0, 0): `LavaFogEnvironment.COLOR` is #991900 in the jar (the wiki rounds it to #991A00, (0.6, 0.1, 0)).
- [x] **Underwater fog switches at once** when the camera crosses the surface (wiki, Fog: "a much thicker initial fog is applied" on entering, receding with time - which `waterVision` already does); the invented 3 per second ease is gone.
- [x] **Swamp and mangrove swamp water fog**: `minecraft:visual/water_fog_end_distance` multiplied by 0.85 in both biome files (the wiki's `has_closer_water_fog` tag, replaced by this attribute in 25w42a); blended by the biome weights (since section 30 read from the attribute itself through `BiomeAttributes`, no hard-coded biome list).
- [x] **Dark disc**: `SkyRenderer.renderDarkDisc` draws a black radius-512 disc 4 blocks below the camera while the eye is below the horizon height (63, or the world's bottom in a flat world) and not under water (`shouldRenderDarkDisc`; wiki, Void: the void is black).
- [x] **Fluid reload**: `FluidSim.chunkRestored` re-schedules every flowing block, and every source with room to spread, when a chunk is loaded from disk (a second later, so the neighbours are loaded). A settled ocean schedules nothing.
- Not changed on purpose: the flowing texture is still the first frame in quarter turns, the burning overlay is still flat, the test controller's `respawn` command only resets health (the real Respawn button resets everything).
- Tests: `TorchAndFarmlandTest` (moisture steps), `FluidSimTest` (reload).

## 27. Trees, leaves at a distance, animated water / lava / fire, respawn (G27)

Researched with Perplexity on minecraft.wiki (Tree, Tree/Structure, Acacia, Dark oak, Leaves, Fog, Screen effects, Resource pack, Health, Death - the pages were opened in the browser) and read from the 26.3 jar (TreeFeature, the trunk, foliage and root placers, `worldgen/feature/*.json`, MipmapGenerator, SpriteContents animation, ScreenEffectRenderer, the `.mcmeta` files).

- [x] **Leaves at a distance.** The whole atlas was mipmapped by one filter, so far-away leaves turned pale and see-through. Now each atlas cell gets its own mip chain (`engine/MipmapChain`): every leaf texture is `dark_cutout` (its holes take 3/4 of the darkest opaque texel, a level averages in linear light and divides by four whatever is transparent, and the alpha is scaled so the share of texels passing the alpha test stays that of level 0); other textures with a transparent texel are `cutout`, the rest a linear mean (glass says `mean`). The terrain cutout pass tests alpha at 0.5 (RenderPipelines ALPHA_CUTOUT_THRESHOLD_CUTOUT_TERRAIN) and writes the fragment opaque. [P] the coverage count is a 4 x 4 bilinear supersample recalled from the bytecode.
- [x] **Trees are the client's own** (`world/TreePlanner`): TreeFeature with the trunk placers (straight, forking for acacia, dark oak, cherry, upwards-branching for mangrove) and foliage placers (blob, spruce, pine, acacia, dark oak, cherry with holes and hanging leaves, random spread) and the mangrove root placer, with the numbers of the configured features: oak 4 + 0-2 (blob radius 2, height 3), birch 5 + 0-2, swamp oak 5 + 0-3 (radius 3), jungle 4 + 0-8, spruce 5 + 0-2 + 0-1 (a third of them the pine: 6 + 0-4), acacia 5 + 0-2 + 0-2 forking, dark oak 6 + 0-2 + 0-1 on a 2 x 2 trunk with 2-4 block branches, cherry 7 + 0-1 with 1-3 branches, mangrove 4 + 0-1 + 0-9 (85 %) or 2 + 0-1 + 0-4 lifted 3-7 or 1-3 blocks on roots. Block counts follow the wiki's Tree/Structure page (swamp oak 137+ leaves, oak 56+, dark oak 24+ logs). The old hand-shaped blobs and discs (and their tests) are gone.
- [x] **No more flat cuts at chunk borders** (the big gaps): a tree is planned whole and what lies beyond the chunk's border goes to the neighbour (`World.spill`): applied at once when that chunk is generated, kept in memory and in `saves/<world>/spill/` until it is generated or loaded. The result does not depend on which chunk comes first. A mangrove whose roots would be too long is retried up to 5 times (vanilla makes many attempts).
- **Not ported (no such block):** vines, cocoa, bee nests, propagules, moss carpet, muddy mangrove roots, leaf litter; branch logs carry no axis. **Forms not made:** fancy oak (a third of plains oaks and a tenth elsewhere in vanilla), mega jungle and mega spruce, jungle bushes, fallen trees - they are new features, left out on purpose.
- [x] **Animated textures** (`engine/TextureAnimation`, `TextureAtlas.tickAnimations`, 20 times a second in every screen): the `.png.mcmeta` format of the wiki's Resource pack page - `frametime`, `frames` with per-frame `time`, `interpolate`. Real timings: water 32 frames x 2 ticks, flowing water 32 frames x 1 (32 x 32 frames), lava 20 frames played up and back (38 x 2 ticks), flowing lava 16 frames x 3, fire 32 x 1. A changed tile is rewritten into the atlas with its border and mip levels (`glTexSubImage2D`), nothing is re-meshed. The sides of a fluid use the top-left quarter of a 32 x 32 flowing frame and a moving surface its middle (new tiles `WATER_FLOW_TOP`, `LAVA_FLOW_TOP`).
- [x] **Burning overlay** as `ScreenEffectRenderer.renderFire`: two 1 x 1 quads of the animated `fire_1` sprite at 0.5 in front of the camera, moved +-0.24 sideways and 0.3 down and turned -+10 degrees, colour #e5ffffff, drawn with the world's projection before the hand and also with the HUD hidden (wiki: visible with the HUD disabled).
- [x] **Respawn**: the test controller's `respawn` ran only `health.reset()`, so a burning, poisoned player stayed so. It now runs the real respawn (spawn point, full health and food, no effects, no fire). The wiki (Health, Death): the player also loses all effects on respawn.
- Tests: `VanillaTreeTest`, `TreeSpillTest`, `MipmapChainTest`, `TextureAnimationTest`.

## 28. Vanilla terrain blocks (G28)

Every value was read from the 26.3 client jar: the `Blocks` static initializer (destroyTime, explosion resistance, `requiresCorrectToolForDrops`, `SoundType`, friction, `noOcclusion`), `data/minecraft/loot_table/blocks/*.json`, `data/minecraft/tags/block/mineable/*.json` and `needs_*_tool.json`, and `assets/minecraft/blockstates` + `models/block` for the faces. None of these blocks gives off light.

- [x] **New blocks (ids 256-277)**: calcite (0.75), cinnabar (1.5 / 6), coarse dirt (0.5), deepslate (3 / 6), cobbled deepslate (3.5 / 6, what deepslate drops), deepslate iron ore (4.5 / 3, needs a stone pickaxe, drops raw iron), granite (1.5 / 6), mycelium (0.6, drops dirt), podzol (0.5, drops dirt), powder snow (0.25, drops nothing), packed ice (0.5, drops nothing without Silk Touch, opaque), block of raw copper and of raw iron (5 / 6, need a stone pickaxe), red sandstone (0.8), sulfur (1.5 / 6), tuff (1.5 / 6) and white, orange, yellow, brown, red and light gray terracotta (`Blocks.DYED_TERRACOTTA`, 1.25 / 4.2). Pickaxe blocks drop nothing by hand except packed ice (no `requiresCorrectToolForDrops`); coarse dirt, mycelium and podzol are shovel blocks.
- [x] **Faces** from the models: cube_all; deepslate cube_column (`deepslate_top` / `deepslate`); mycelium and podzol cube_bottom_top over `dirt`, no tint (no `tintindex`); red sandstone top / side / bottom.
- [x] **`world/VanillaBlocks`**: maps a vanilla block id + properties (worldgen's `{"Name", "Properties"}` or a bare id) to the engine block and back, and throws `UnsupportedBlockStateException` for anything the engine cannot show (a log or deepslate with axis x/z, snowy grass / mycelium / podzol, furnaces and chests not facing north or lit, a waterlogged block, sapling stage 1, lava with an odd amount, unknown blocks). Leaves' `distance` / `persistent` are accepted with any value (no leaf decay here). `VanillaBlocksTest` walks `material_rule/overworld.json` and every rule it references (ore veins, bedrock, surface, sulfur cave bands) plus the bandlands palette of `MaterialSystem` and checks that every block they write maps.
- **Not added**: deepslate copper ore - the 26.3 overworld ore-vein rules write `copper_ore` and `deepslate_iron_ore` only (`OreVeinRule$VeinType`, `material_rule/overworld/*_ore_vein.json`).
- **Deviations / missing behaviour:**
  - Powder snow: a full-collision, non-occluding cube here. Vanilla (`PowderSnowBlock`) has an empty collision shape unless the entity is a falling block, wears leather boots and is above it and not descending (`canEntityWalkOnPowderSnow`, mobs in `#powder_snow_walkable_mobs`), or has fallen more than 2.5 blocks (a 0.9 high shape); missing are sinking in, `makeStuckInBlock(0.9, 1.5, 0.9)`, freezing (`InsideBlockEffectType.FREEZE`, the frozen screen overlay and freeze damage), putting out fire (`EXTINGUISH`), the snowflake particles, the fall sounds from 4 blocks, the inside view (its model is six double-sided planes, here only outward faces are drawn), pickup with a bucket and the powder snow bucket item. Kept from vanilla: same-block faces are skipped (`skipRendering`), light dampening 1, smooth-lighting shade 1.0, no torch support.
  - Deepslate: only axis=y; the blockstate's random mirrored and rotated variants are not drawn (stone's are not either). Mycelium and podzol: the random y rotations of the top are not drawn; snowy=true is not supported; mycelium's spreading (`randomTicks`) is not implemented.
  - Sounds: calcite, cinnabar, deepslate, cobbled deepslate, deepslate iron ore, sulfur, tuff and powder snow have their own vanilla `SoundType`s whose files are not in `sounds/`; they are silent (`SoundMaterial.MISSING_VANILLA_SOUND`), not given another block's sound.
  - Packed ice's friction 0.98 is not applied (the engine has no block friction; ice has the same gap).
  - No Silk Touch or Fortune; no smelting recipes for the new blocks (deepslate iron ore, cobbled deepslate, sulfur...).
- **Existing blocks checked against the jar** (stone, dirt, grass block, gravel, sand, sandstone, mud, bedrock, water, lava, ice, snow block, copper ore): destroyTime all match. Differences found, not changed: `Explosions.blastResistance` gives lava and flowing water 0 (vanilla 100 - `strength(100)`); ice is not a pickaxe block (it is in `mineable/pickaxe`) and plays the stone sounds (vanilla `SoundType.GLASS`); mud plays the gravel sounds (vanilla `SoundType.MUD`, no files here).
- Tests: `VanillaBlocksTest`, `VanillaTerrainBlocksTest`, `SoundMaterialTest`.

## 29. Vanilla world generation: terrain, surface, carvers, biomes (Phase A)

Regular (non-flat) worlds are generated by the port of the 26.3 pipeline; the invented generator (`TerrainGenerator`, `CaveCarver`, the ore dice, the per-biome tree/height rules) and its tests are deleted.

- [x] **Pipeline** (`worldgen/chunk/OverworldGenerator`, engine side `world/VanillaChunkGenerator`): per world, from the seed, `RandomState`, the `minecraft:overworld` noise settings, the overworld `MultiNoiseBiomeSource` preset, `MaterialSystem` + the `minecraft:overworld` material rule, the carvers and the block tags from `data/`. Per chunk, in `NoiseBasedChunkGenerator.buildTerrain`'s order (read from the jar): BIOMES (`createBiomes`), `doFill` (noise + aquifers), `buildSurface` (region biome manager over the stored biomes of the 3 x 3 chunks, `collectPossibleBiomes(region, 1)`), `generateCarvers` (17 x 17 source chunks, then `applyCarvingMask`: column top-down, `#uncarvable` kept, grass/mycelium flag, `aquifer.computeSubstance(x, y, z, 0.0)`, re-topping exposed dirt with `MaterialSystem.topMaterial` over the uncached biome resolver), `noiseChunk.close()`, then the four final heightmaps. The carver list is looked up per source chunk in vanilla; every overworld biome of the 26.3 data carries `[cave, cave_extra_underground, canyon]`, which the generator checks at start (it refuses data where they differ). No stage reads another chunk's blocks, so a chunk does not depend on the generation order (tested). Each distinct vanilla state of a chunk is converted once through `VanillaBlocks`; an unknown state stops generation with the state named. Blocks found missing: none (every state the overworld rules write maps).
- [x] **Fluid post-processing**: the positions the aquifer / surface rules mark (`markPosForPostProcessing`) get one immediate fluid tick (`FluidState.tick`, as `LevelChunk.postProcessGeneration` does) once the chunk and its 8 neighbours are generated (vanilla does it when the chunk starts ticking). Marks of a chunk unloaded before that are dropped (`FluidSim.chunkRestored` re-ticks unsettled fluids on load anyway).
- [x] **Biomes in the engine**: each chunk keeps its quart biome table (not saved; recomputed from the seed when loaded). `World.getBiome(x, y, z)` is vanilla's `BiomeManager.getBiome` (fiddled zoom, `obfuscateSeed`) over the loaded chunks' tables, and for chunks not loaded the table their BIOMES status stores (cached by the generator). The F3 overlay and the controller's `state` show it.
- [x] **Tint**: `ClientLevel.calculateBlockTint` with `Biome.getGrassColor(x, z)` / `getFoliageColor()` / `getWaterColor()` from the biome JSON (overrides, `grass_color_modifier` dark_forest / swamp with `BIOME_INFO_NOISE`), the colormaps through `ColorMapColorUtil.get` (pixels as `NativeImage.makePixelArray`), and the box average over `(2r + 1)^2` columns with the `biomeBlendRadius` option (options.txt key, 0-7, default 2, from `Options`).
- [x] **Leaf tints** checked against `BlockColors.createDefault`: spruce is the constant 0x619961 (was 0.38/0.50/0.38), birch the constant 0x80A755 (was biome foliage), cherry has no tint (was multiplied pink); oak, jungle, acacia, dark oak, mangrove use the biome foliage colour (unchanged).
- [x] **Spawn** (`OverworldSpawn`): `NoiseBasedChunkGenerator.getOrigin` (`NoiseSpawnFinder`: fitness at 0,0, then the radial searches 2048/512 and 512/32 over `spawn_target`), `MinecraftServer.setInitialSpawn` (spawn height 64, the 11 x 11 chunk spiral of `PlayerSpawnFinder.getSpawnPosInChunk` / `getLevelRespawnPos` on the MOTION_BLOCKING / WORLD_SURFACE / OCEAN_FLOOR heightmaps), then `PlayerSpawnFinder.findSpawn` with `respawn_radius` 10 (441 candidates, coprime 17, offset from an unseeded thread-local random as in vanilla, `noCollisionNoLiquid`, `fixupSpawnHeight` fallback).
- [x] `TerrainGenerator.SEA_LEVEL` is gone: the sea level comes from the loaded noise settings (`VanillaChunkGenerator.seaLevel()`, 63). `worldgen/material`'s duplicate `WorldGenerationContext` / `VerticalAnchor` were merged into `worldgen/provider`'s.
- **Verification**: the stages stay bit-for-bit against the oracles (`TerrainOracleTest` 40 chunks, `MaterialSystemOracleTest` 60 chunks + 7680 `topMaterial`, `CarvingMaskGoldenTest` 48 masks); `WorldGenerationTest` checks determinism, order independence, bedrock at y -64, the engine heightmap, biomes on the load path and the spawn. Live: seeds 12345 (windswept/snowy slopes with packed ice, deepslate caves in lush caves / deep dark at y -51 and -35), 99 (lukewarm ocean and beach, desert, badlands terracotta bands, jungle; water colour differs per ocean), 2024 (forest to savanna to badlands tint gradient, hills). Not compared against an end-to-end vanilla chunk dump: the composition of the stages (and `applyCarvingMask`) is transcribed from the bytecode but has no golden of its own [P].
- **Performance**: about 13 ms per chunk for the terrain (warm), 15 ms with the engine copy, tint about 1 ms; world setup about 0.4 s, spawn search 0.3-0.5 s (first world after start of the JVM up to 6 s while classes load). Streaming now generates nearest chunks first; with the 4 ms budget that is one chunk per frame (a ~15 ms frame while new chunks stream in). Generation stays on the main thread.
- **Pending (not generated yet)**: features (ores outside the veins, trees, vegetation, lakes, springs, ...), structures, the beardifier and blending (the atmosphere, precipitation and F3 now come from the biome files: section 30); powder snow and mud behaviour (section 28).
- **Deviations**: the tint is one value per column, evaluated at the column's top block (vanilla tints per block, so water and leaves deeper in a column and cave grass use the surface value); a fixed blend radius per chunk (changing the option does not re-tint loaded chunks); neighbour biomes for unloaded chunks come from the BIOMES table the generator computes for them (the same values the chunk will store) where a client would see plains; the post-processing tick runs when the 3 x 3 chunks exist rather than at vanilla's ticking-chunk promotion, and the liquid block's bubble-column tick and `updateFromNeighbourShapes` are not run (no bubble columns; only fluids are marked); flat worlds keep their fixed tint. The `data/` folder (gitignored) and `textures/colormap/{grass,foliage}.png` are required for regular worlds; without `data/` world creation fails with a message naming `python scripts/restore-worldgen-data.py`.
- Tests: `WorldGenerationTest` (rewritten), `SaplingGrowthTest` (flat generator); deleted with the old code: `CaveCarverTest`, `SubmergedCaveTest`, `TerrainGeneratorTreeTest`, `NewBiomesTest`. Full suite: 929 passing.

## 30. Biome atmosphere and weather (Phase B)

Research: `docs/research/biome-atmosphere-26-3.md` (26.3 client: `EnvironmentAttributeSystem`, `EnvironmentAttributeProbe`, `GaussianSampler`, `SpatialAttributeInterpolator`, `WeatherAttributes`, `AtmosphericFogEnvironment`, `WaterFogEnvironment`, `WeatherEffectRenderer`, `ClientLevel.tickWeatherEffects` / `getPrecipitationAt`, `DebugEntryBiome`, `DebugEntryChunkGeneration`).

- [x] **Atmosphere from the biome files** (`world/BiomeAttributes`, `world/AtmosphereProbe`): sky colour, fog colour, water fog colour and water fog start / end are each biome's `minecraft:visual/*` attribute (override, or `{modifier, argument}`; swamp and mangrove swamp `water_fog_end_distance` multiply 0.85) over the overworld dimension values (sky #78a7ff, fog #c0d8ff) and the attribute defaults (water fog #050533, -8, 96). Blended around the camera like the probe: every tick a 6x6x6 quart Gaussian (kernel 0 1 4 6 4 1 0) at camera / 4 - 0.5, weights summed per biome and folded with `lerp(w / sum)`; frames lerp the last two ticks. Unloaded chunks count as plains (`ClientLevel.getUncachedNoiseBiome`). The timeline, weather and flash layers (`SkyColors`, sections 18 and 26) apply on top unchanged.
- [x] **Rain fog** uses `biome.hasPrecipitation()` at the camera's block (half strength without precipitation), as `AtmosphericFogEnvironment.updateRainFogState` does.
- [x] **Precipitation per biome and position** (`World.getPrecipitationAt` = `ClientLevel.getPrecipitationAt`): none in an unloaded chunk or a `has_precipitation false` biome (desert, savanna, badlands, ...), snow below 0.15 after the frozen modifier and the height adjustment above sea level + 17, rain otherwise.
- [x] **Weather columns** (`Game.drawWeatherColumns`, `WeatherEffectRenderer`): per column the precipitation at the camera's height picks `rain.png` or `snow.png` or nothing; the vanilla column seed (`... ^ ...`), rain speed and tick offset (`& 255`, `% 32`), snow drift (`nextDouble`, `nextGaussian`) and brightened snow light; alpha `lerp(min(d^2/r^2, 1), max, 0.5) * rain level` with max 1 (rain) / 0.8 (snow); the camera's own column is not drawn (its quad width is 0/0 in vanilla).
- [x] **Rain splashes and the rain sound** only where the surface's precipitation is rain (`ClientLevel.tickWeatherEffects`); vanilla's integer +-10 block height window.
- [x] **Fire is put out by rain** only where it rains (`Level.isRainingAt`: biome precipitation RAIN), not in snow or dry biomes.
- [x] **Flat worlds** use the preset's biome, plains (`FixedBiomeSource`, `classic_flat.json`), sea level -63 (`FlatLevelSource.getSeaLevel`): tint, atmosphere and precipitation come from `plains.json` (the fixed tints {0.62, 0.75, 0.38} etc. are deleted); the dark disc uses the flat horizon height = min y (-64), as `ClientLevelData.getHorizonHeight`.
- [x] **Icon tints** from the item models: oak / jungle / acacia / dark oak leaves `constant -12012264`, mangrove `-7158200`, grass `minecraft:grass` at temperature 0.5, downfall 1.0 (the colormap pixel); the invented 0x7CBD6B / 0x59AE30 are deleted.
- [x] **F3** shows vanilla's `Biome: minecraft:<id>` and the chunk-generation lines (`Density N: .. T: .. V: .. C: .. E: .. D: .. W: .. PV: .. PS: ..` from the noise settings' `debug_functions`, `Biome builder PV: .. C: .. E: .. T: .. H: ..`); the `state` dump adds the height-adjusted temperature, downfall, precipitation and the probe's colours.
- [x] **Deleted**: `world/Biome`, `world/BiomeColors`, `world/ClimateSample`, `world/SimplexNoise`, `world/Colormap`, `WorldGenerator.climateAt`, `World.climateAt`, `BiomeTest`, `BiomeAtmosphereTest`, `SimplexNoiseTest`; their real-file checks live on in `BiomeAttributesTest`.
- Still approximate / missing: the probe lerps the biome part per tick and the timeline per frame (vanilla lerps the composed value per tick); the shader has one alpha per draw, so weather columns are drawn in six alpha groups (each at its mean alpha); smoke instead of rain splashes over lava / magma / campfires; the rain sound is still this engine's looping clip (vanilla plays one-shot `weather.rain` / `weather.rain.above` at the splash position); ambient particles, music and ambient sounds from the biome attributes; powder snow, blindness and darkness fog; the F3 overlay shows only the biome lines.
- Tests: `BiomeAttributesTest` (new: real biome-file values, Gaussian weights, border blends, precipitation, the flat world), `RainAndStackTest` (vanilla column alpha, rain and snow). Full suite: 920 passing.

## 31. Feature decoration (ores, discs, springs) (Phase C1)

Regular worlds now run the 26.3 feature decoration (`worldgen/feature/BiomeDecoration`, verified against the real classes by `FeatureOracleTest`) inside the engine.

- [x] **Chunk statuses** (`world/VanillaChunkGenerator`, `world/World`): a chunk is first generated to TERRAIN (a `TerrainProto`: the worldgen `TerrainChunk` with biomes, blocks and heightmaps; invisible to the engine, `World.getBlock` answers air for it as for an unloaded chunk; not lit, not meshed), then decorated with `applyBiomeDecoration` on a `GenRegion` (3 x 3 writable, distance 8 readable), then copied into the engine `Chunk` and made FULL (`markGenerated`: lit, meshed, ticking). TERRAIN chunks are never saved.
- [x] **The 5 x 5 rule (design decision).** Vanilla's FEATURES step only guarantees TERRAIN at distance 1 (`ChunkPyramid`: TERRAIN radius 1, STRUCTURE_STARTS radius 8), so what a feature reads at distance 2..8 depends on what the server happened to generate there. Before decorating chunk C the engine makes every chunk within chessboard distance 2 of C at least TERRAIN (`WorldGenerator.dependencyRadius` = 2), so those reads always see terrain - one of the outcomes vanilla allows. A region chunk is the engine chunk when FULL (`EngineFeatureChunk`), else its TERRAIN proto, else its save (loaded into the world), else (beyond distance 2 only) an empty STRUCTURE_STARTS chunk (`EmptyFeatureChunk`: air, empty heightmaps; no ported feature reads that far).
- [x] **Order.** As in vanilla, a chunk's blocks depend on the order its neighbours are decorated in (a decoration writes into the 3 x 3 around it and reads what earlier decorations wrote). Nothing else matters: the result is the same whatever order the TERRAIN chunks were made in, whatever else is loaded, and through the streaming's step-by-step path or a synchronous `generateChunk`; chunks 4 apart come out the same in any order and the same as alone (`WorldGenerationTest`, `FeatureDecorationEngineTest`).
- [x] **Writes into FULL chunks.** Vanilla never decorates next to a FULL chunk (LIGHT needs the neighbours past FEATURES); here a decorated chunk becomes FULL at once, so a later neighbour's decoration (and a new chunk next to chunks loaded from disk) writes into FULL chunks: `World.setBlockFromFeature` sets the block, updates the engine light (`LightEngine.onBlockChanged`, sky blocker included) and marks the chunk's and the bordering neighbour's meshes dirty; there are no neighbour updates (no `BlockSupport`, no fluid scheduling), as `WorldGenRegion.setBlock` makes none. Post-processing marks into a FULL chunk wait with its own marks for its 3 x 3 neighbourhood; fluid ticks are scheduled at once (`FluidSim.schedule`, delay 0).
- [x] **Heightmaps.** TERRAIN protos keep the four final heightmaps of the port (`TerrainChunk`, `Heightmap.update`). An engine chunk keeps none: `EngineFeatureChunk` scans a column the first time it is asked for and then maintains it by `Heightmap.update`'s rules on `setBlockState` (the raw section write of `BulkSectionAccess` leaves it alone); `WORLD_SURFACE_WG` / `OCEAN_FLOOR_WG` are primed by the scan and not updated, as `ChunkAccess.getHeight` primes a missing heightmap. This equals the maintained heightmap as long as no raw write changed a predicate at the column top; every decorated chunk's four heightmaps are checked against the scan when it becomes FULL (generation stops on a difference) and `FeatureDecorationEngineTest` compares them with the worldgen side.
- [x] **Post-processing and ticks.** The decorated chunk's marks (terrain and features, in `ProtoChunk` order) run when its 3 x 3 neighbourhood is FULL, as before; the fluid ticks the features scheduled (`ProtoChunkTicks`: springs) go to `FluidSim` when the chunk becomes FULL. A scheduled block tick stops generation (the engine has none; no engine-enabled feature schedules one).
- [x] **Journals instead of saved TERRAIN chunks.** The terrain of a proto is a function of the seed; the writes a neighbour's decoration made into it are not. Every write into a proto is journaled (`TerrainProto`: setBlock, section write, mark, fluid / block tick, in order). An evicted proto (streaming drops protos farther than render distance + 4) keeps its journal in memory, or in `decoration/<x>_<z>.journal` for a saved world (also written on save); a proto made again generates its terrain and replays the journal, giving the identical chunk. The journal is deleted when the chunk becomes FULL.
- [x] **Saves** (`WorldIO`): chunk format version 3 adds the status after the range (`minecraft:full`; only FULL chunks are saved). Version 2 chunks (vanilla terrain without decoration) are legacy: reported once and generated again, not migrated. Chunks loaded from disk are FULL; a new chunk next to them still decorates and may write into them (saved again when unloaded).
- [x] **Streaming** (`Game.updateChunkStreaming`, `World.generateStep`): nearest chunks first, one unit per step (a restore, one saved neighbour, one TERRAIN chunk, or the decoration), the 4 ms budget checked between units. Unload radius render distance + 3 (saved neighbours a decoration loaded reach 2 chunks past the render distance). Measured (seed 12345, render distance 6): 16-18 ms per FULL chunk amortised (TERRAIN neighbours included), ~1000 chunks in 16.5 s of generation time; streaming frames 20-26 ms (one TERRAIN chunk is ~15 ms, so a frame can exceed the budget by one unit, as before); a controller teleport (3 x 3 FULL synchronously, 49 TERRAIN chunks) 680 ms. All on the main thread (the generation state is shared and not thread-safe).
- [x] **Engine features** (`VanillaChunkGenerator.ENGINE_FEATURE_TYPES`): `ore` (30 top-level placed features), `disk` (5), `spring_feature` (3), `underwater_magma` (1). Every other top-level placed feature is skipped exactly as `UnportedFeature` is (its feature seed is still drawn; nothing is placed), also those the worldgen package has ported since (selectors, `simple_block`, `block_column`, `bamboo`, `vines`, ...) until the engine has their blocks. Skipped in game (top-level counts of the 171 in the 56 overworld biomes): bamboo 2, block_blob 1, block_column 9, blue_ice 1, fossil 2, freeze_top_layer 1, geode 1, iceberg 2, lake 2, large_dripstone 1, monster_room 2, multiface_growth 2, overlay 1, random_boolean_selector 2, random_selector 23, root_system 2, sequence 2, simple_block 52, simple_random_selector 5, speleothem_cluster 2, spike 1, tree 2, vegetation_patch 3, vines 2, weighted_random_selector 9. Structures are not generated.
- [x] **New blocks (ids 278-292)**, values from the 26.3 jar (`Blocks` static initializer, `InfestedBlock`, `loot_table/blocks/*.json`, `tags/block/mineable/pickaxe`, `needs_stone_tool`, `needs_iron_tool`, `models/block/*.json`, `lang/en_us.json`): andesite and diorite (1.5 / 6, drop themselves), deepslate coal / copper / diamond / emerald / gold / lapis / redstone ore (`ofLegacyCopy(ore).strength(4.5, 3)`), emerald, lapis and redstone ore (3 / 3), infested stone (0.75 / 0.75) and infested deepslate (1.5 / 0.75; `InfestedBlock`: host destroyTime / 2, resistance 0.75; mineable with a pickaxe but no `requiresCorrectToolForDrops`; drop nothing without Silk Touch), magma block (0.5, light 3, animated `magma.png`). Drops without Silk Touch / Fortune: coal; raw gold; diamond; emerald; raw copper 2-5; lapis lazuli 4-9; redstone dust 4-5. Tool tiers: lapis ores and deepslate copper need stone, gold / diamond / emerald / redstone (deepslate) ores need iron. Redstone ore exists only as `lit=false` (a `lit=true` state throws); infested deepslate only as `axis=y`. Sounds: SoundType.STONE where vanilla uses it; the deepslate ores and infested deepslate (SoundType.DEEPSLATE) are silent like deepslate (files not in `sounds/`).
- [x] **New items (ids 293-295)**: lapis lazuli, redstone dust, emerald, inventory items only (their real `textures/item/` icons).
- Missing behaviour (not substituted): Fortune and Silk Touch; the ores' experience drops (`DropExperienceBlock`: coal 0-2, lapis 2-5, diamond / emerald 3-7, redstone 1-5); smelting the new ores; redstone ore lighting up when touched (and its random ticks / particles); silverfish from infested blocks (and their faster break with the wrong tool rules beyond the destroy time); magma's damage to entities, bubble columns, `isValidSpawn` and emissive (full-bright) rendering; redstone dust placing wire; the random mirrored / rotated model variants of infested stone / deepslate (as for stone and deepslate).
- Deviations: a decorated chunk is visible and ticking before its neighbours are decorated (vanilla waits), so fluids flowing or player edits in it can change what a later neighbour's decoration reads; chunks beyond distance 2 that exist nowhere read as empty; leaves read back from an engine chunk with their default `distance` / `persistent` (the engine does not keep them); an engine-internal sapling stage or an item read by a feature stops generation.
- Verification: `FeatureDecorationEngineTest` (engine chunks equal the worldgen decoration on TerrainChunks in the same order, block for block and heightmap for heightmap, with ores, granite, andesite, diorite and tuff present; order independence; evicted journals; save / journal round trip; v3 header and v2 legacy; relight and remesh of writes into FULL chunks; TERRAIN invisible and the 5 x 5 rule), `FeatureBlocksTest` (jar values), `WorldGenerationTest` (rewritten order test). Live (seed 12345): copper and iron ore in a stone wall at y 4-10, deepslate gold ore at y -44, ore depth counts over 64 chunks (deepslate redstone highest at the bottom, diamonds only below 16, copper peaking around 48, coal up to 127, lapis around 0) agree with the minecraft.wiki ore pages as a sanity check; magma, clay discs and infested stone found by a block scan.
- Tests: `FeatureDecorationEngineTest` (7), `FeatureBlocksTest` (5). Full suite: see the C1 report.
