# Asset manifest

What the game expects on disk and where it loads it from. Derived from the source (not from memory);
`scripts/restore-assets.py` re-derives the same lists from the source every time it runs.

All paths are **relative to the working directory**, which is the repository root (the game is started with `mvn exec:java` from there).
None of these files are committed: `textures/`, `texts/`, `sounds/`, `minecraft-sounds/`, `data/` and `options.txt` are in `.gitignore`.

## Folder layout

| Folder / file | Read by | Required? | Behaviour when missing |
|---|---|---|---|
| `textures/block/*.png` | `engine/TextureAtlas` (`REAL_TEXTURE_PATHS`), `engine/CrackOverlay` | no | magenta-and-black "missing texture" tile (logged); no cracks are drawn when `destroy_stage_*` are missing |
| `textures/item/*.png` | `engine/TextureAtlas` (`REAL_TEXTURE_PATHS`) | no | "missing texture" tile |
| `textures/gui/sprites/hud/**` | `engine/HudTextures` | no | the 8 core sprites are all-or-nothing, else **no hotbar, hearts or hunger are drawn** (no stand-in art); the rest are optional per group |
| `textures/gui/container/{inventory,crafting_table,furnace,blast_furnace,smoker}.png`, `textures/gui/sprites/container/<station>/{lit,burn}_progress.png` | `game/Game` -> `ui/GuiPainter.background`, `engine/HudTextures.loadFile` | no | plain grey panel and slots; no progress sprites |
| `textures/font/ascii.png` | `engine/PixelFont` (glyph widths and the atlas `engine/Font` draws) | no | no text is drawn (every character advances 6, spaces 4) |
| `textures/gui/sprites/widget/{button*,slider*,slider_handle*,text_field*,scroller*}.png`, `textures/gui/sprites/tooltip/{background,frame}.png`, `textures/gui/sprites/container/slot_highlight_{back,front}.png` | `ui/UiSprites` (9-slice) via `ui/GuiContext`, `ui/GuiPainter` | no | plain flat boxes (buttons, fields), a dark tooltip box, a white 50% hover square |
| `textures/gui/{menu_background,menu_list_background,header_separator,footer_separator}.png`, `textures/gui/title/{minecraft,edition}.png` | `ui/GuiContext`, `ui/TitleScreen` | no | a dark plain fill; the title is drawn as the word MINECRAFT |
| `textures/gui/sprites/hud/crosshair.png` | `game/Game.renderHud` | no | no crosshair is drawn |
| `texts/splashes.txt` | `ui/Splashes` | no | no splash text |
| `textures/entity/experience/experience_orb.png` | `game/Game.renderXpOrbs` | no | orbs are not drawn |
| `textures/environment/clouds.png` | `game/Game.renderClouds` (`game/SkyDetails`) | no | no clouds |
| `textures/particle/splash_{0,1,2,3}.png` | `game/Game.drawRainSplashes` (`fx/RainSplashes`) | no | no rain splashes |
| `textures/particle/{critical_hit,damage}.png` | `game/Game` -> `fx/SpriteParticles` | no | those particles are not drawn |
| `textures/environment/{rain.png,snow.png,celestial/sun.png,celestial/moon/full_moon.png}` | `game/Game` (`renderRain` / `drawWeatherColumns`, `renderSunAndMoon`) | no | no rain or snow columns / sun / moon |
| `textures/entity/player/wide/steve.png` | `game/Game.renderHeldItem` (bare hand), `renderPlayerPreview` | no | the bare hand and the inventory player preview are not drawn |
| `textures/entity/equipment/{humanoid,humanoid_leggings}/<material>.png` (+ `leather_overlay.png`) | `game/Game.renderPlayerPreview` | no | that armor layer is not drawn in the inventory preview |
| `textures/gui/sprites/hud/hotbar_offhand_left.png` | `game/Game.renderRealHud` | no | the offhand item is drawn without its slot frame |
| `textures/gui/sprites/hud/heart/{poisoned,absorbing}_{full,half}.png`, `textures/gui/sprites/hud/effect_background.png`, `textures/mob_effect/*.png` | `game/Game` (`drawHealthAndFood`, `drawEffectIcons`) | no | that heart variant / the effect icon or its background is not drawn (`mob_effect/` is a bulk folder of `restore-assets.py`) |
| `textures/block/fire_1.png` | `game/Game.renderFireOverlay` (a tile of the atlas) | no | no burning overlay |
| `textures/block/{water_still,water_flow,lava_still,lava_flow,fire_1}.png` and their `.png.mcmeta` | `engine/TextureAtlas` + `engine/TextureAnimation` (animated) | no | "missing texture" tile; without the `.mcmeta` the first frame stays |
| `textures/item/{bucket,water_bucket,lava_bucket,bow,bow_pulling_0,bow_pulling_1,bow_pulling_2,golden_apple,wheat,wheat_seeds,carrot,beetroot,beetroot_seeds,copper_*,*_spear}.png`, `textures/block/{torch,farmland,farmland_moist}.png` | `engine/TextureAtlas` (`REAL_TEXTURE_PATHS`) | no | "missing texture" tile |
| `textures/entity/zombie/zombie_baby.png` | `game/Game` (`EntityTexture.load`) | no | flat placeholder colour on the baby zombie model |
| `textures/entity/{pig,cow,chicken}/*_temperate_baby.png` | `game/Game` (`EntityTexture.loadFirst`) | no | flat placeholder colour on the baby model |
| `textures/environment/celestial/moon/<phase>.png` (eight phases) | `game/Game.renderSunAndMoon` | no | no moon |
| `textures/item/leather_<piece>_overlay.png` | `engine/TextureAtlas.dyeLeather` | no | the leather icon without its overlay |
| `textures/entity/chest/normal.png` | `engine/TextureAtlas.chestFace` | no | "missing texture" chest faces |
| `textures/colormap/{grass,foliage}.png` | `world/BiomeTints.computeTint` (vanilla `GrassColor`/`FoliageColor` pixels, regular and flat worlds), `ChunkMeshBuilder.grassIconTint` | **yes** for every world | generating a world fails with a message naming the file (no stand-in tint); grass icons get `GrassColor`'s default |
| `data/minecraft/**` (worldgen: `noise_settings`, `density_function`, `noise`, `material_rule`, `material_condition`, `carver`, `biome`, `tags/block`, ...) | `worldgen/chunk/OverworldGenerator` via `world/VanillaChunkGenerator` | **yes** for regular worlds (flat worlds do not need it) | creating or loading a regular world throws `MissingWorldgenDataException` telling to run `python scripts/restore-worldgen-data.py` (it extracts `data/` from the 26.3 client jar); there is no fallback generator. `data/` is gitignored |
| `textures/entity/**` | `game/Game` -> `engine/EntityTexture.load(path, w, h, fallbackRGB)` | no | flat colored placeholder skin |
| `sounds/**.ogg` | `audio/SoundRegistry` (`SOUNDS_ROOT = new File("sounds")`) -> `audio/SoundBuffer` | no | variation dropped; a group with none is silent (one warning) |
| `options.txt` | `ui/GameOptions` (`DEFAULT_FILE = new File("options.txt")`), `ui/KeyBindings` | no | all defaults |

`minecraft-sounds/` is only named in comments/.gitignore (the old local dump `sounds/` was copied from); no code reads it.
Git history holds no copy of any of these (they were always ignored); the only deleted path in history is `.claude/launch.sh`.

Expected layout is the **Java Edition resource-pack layout** (`assets/minecraft/textures/...`) with the leading `assets/minecraft/` dropped, and the **sound-event layout of `assets/minecraft/sounds/...`** with the leading `assets/minecraft/sounds/` dropped.

## options.txt

- Loaded from `./options.txt` (`GameOptions.DEFAULT_FILE`), written by the Options screens (`GameOptions.save`), UTF-8.
- Format: vanilla `key:value`, one per line. Blank lines and `#` comments are skipped. A legacy `key=value` file is still read and rewritten as `key:value` on the next save.
- Each key falls back to its field default independently; a missing or corrupt file means all defaults; numbers are clamped.
- Keys the game reads, with the **code defaults** (`GameOptions` field initialisers):

| Key | Code default | Range / note |
|---|---|---|
| `guiScale` | 0 | 0 Auto, 1-4 |
| `fov` | 0.0 (stored; = 70 deg) | degrees = 70 + 40 x stored, clamped 30-110 |
| `renderDistance` | 6 | 2-16 (vanilla default is 12; code default is 6) |
| `biomeBlendRadius` | 2 | 0-7 (vanilla `Options.biomeBlendRadius`, default 2): the grass/foliage/water tint box radius; read when a world is created, loaded or the options are applied (already tinted chunks keep their tint) |
| `bobView` | true |  |
| `enableVsync` | true |  |
| `fullscreen` | false |  |
| `mouseSensitivity` | 0.5 | 0-1 |
| `invertYMouse` | false |  |
| `gamma` | 0.5 | 0-1 |
| `maxFps` | 120 | 10-250, 260 = unlimited (code clamps to 10-260) |
| `attackIndicator` | 1 | 0 off, 1 crosshair, 2 hotbar |
| `toggleSprint` | false |  |
| `toggleCrouch` | false |  |
| `autoJump` | false |  |
| `fovEffectScale` | 1.0 | 0-1 |
| `soundCategory_master` | 1.0 | 0-1 |
| `soundCategory_music` | 1.0 | 0-1 |
| `soundCategory_record` | 1.0 | 0-1 |
| `soundCategory_weather` | 1.0 | 0-1 |
| `soundCategory_block` | 1.0 | 0-1 |
| `soundCategory_hostile` | 1.0 | 0-1 |
| `soundCategory_neutral` | 1.0 | 0-1 |
| `soundCategory_player` | 1.0 | 0-1 |
| `soundCategory_ambient` | 1.0 | 0-1 |
| `soundCategory_voice` | 1.0 | 0-1 |
| `version` | written as `1` | written, never read |

Key bindings (`key_<id>` -> vanilla key name; default in brackets):

- `key_key.attack:key.mouse.left`
- `key_key.use:key.mouse.right`
- `key_key.forward:key.keyboard.w`
- `key_key.left:key.keyboard.a`
- `key_key.back:key.keyboard.s`
- `key_key.right:key.keyboard.d`
- `key_key.jump:key.keyboard.space`
- `key_key.sneak:key.keyboard.left.shift`
- `key_key.sprint:key.keyboard.left.control`
- `key_key.drop:key.keyboard.q`
- `key_key.inventory:key.keyboard.e`
- `key_key.pickItem:key.mouse.middle`
- `key_key.screenshot:key.keyboard.f2`
- `key_key.fullscreen:key.keyboard.f11`
- `key_key.hotbar.1:key.keyboard.1`
- `key_key.hotbar.2:key.keyboard.2`
- `key_key.hotbar.3:key.keyboard.3`
- `key_key.hotbar.4:key.keyboard.4`
- `key_key.hotbar.5:key.keyboard.5`
- `key_key.hotbar.6:key.keyboard.6`
- `key_key.hotbar.7:key.keyboard.7`
- `key_key.hotbar.8:key.keyboard.8`
- `key_key.hotbar.9:key.keyboard.9`

Not read by the game (documented in `docs/research/options-and-screens.md` but have no field): `entityShadows`, `discrete_mouse_scroll`, `showSubtitles`, `directionalAudio`, `screenEffectScale`, `simulationDistance`, `entityDistanceScaling`, `particles`, `graphicsMode`, `ao`, `renderClouds`, `mipmapLevels`, chat/playerlist/command/perspective/swapOffhand key binds.

## Textures (187 files)


### `textures/block/`

- `acacia_leaves.png`
- `acacia_log.png`
- `acacia_log_top.png`
- `andesite.png`
- `bedrock.png`
- `birch_leaves.png`
- `birch_log.png`
- `birch_log_top.png`
- `bookshelf.png`
- `bricks.png`
- `brown_terracotta.png`
- `calcite.png`
- `cherry_leaves.png`
- `cherry_log.png`
- `cherry_log_top.png`
- `cinnabar.png`
- `clay.png`
- `coal_ore.png`
- `coarse_dirt.png`
- `cobbled_deepslate.png`
- `cobblestone.png`
- `crafting_table_side.png`
- `crafting_table_top.png`
- `dark_oak_leaves.png`
- `dark_oak_log.png`
- `dark_oak_log_top.png`
- `deepslate.png`
- `deepslate_coal_ore.png`
- `deepslate_copper_ore.png`
- `deepslate_diamond_ore.png`
- `deepslate_emerald_ore.png`
- `deepslate_gold_ore.png`
- `deepslate_iron_ore.png`
- `deepslate_lapis_ore.png`
- `deepslate_redstone_ore.png`
- `deepslate_top.png`
- `destroy_stage_0.png`
- `destroy_stage_1.png`
- `destroy_stage_2.png`
- `destroy_stage_3.png`
- `destroy_stage_4.png`
- `destroy_stage_5.png`
- `destroy_stage_6.png`
- `destroy_stage_7.png`
- `destroy_stage_8.png`
- `destroy_stage_9.png`
- `diamond_block.png`
- `diamond_ore.png`
- `diorite.png`
- `dirt.png`
- `emerald_ore.png`
- `furnace_side.png`
- `furnace_top.png`
- `glass.png`
- `glowstone.png`
- `gold_block.png`
- `gold_ore.png`
- `granite.png`
- `grass_block_side.png`
- `grass_block_top.png`
- `gravel.png`
- `ice.png`
- `iron_block.png`
- `iron_ore.png`
- `jungle_leaves.png`
- `jungle_log.png`
- `jungle_log_top.png`
- `lapis_ore.png`
- `light_gray_terracotta.png`
- `magma.png.mcmeta`
- `magma.png`
- `mangrove_leaves.png`
- `mangrove_log.png`
- `mangrove_log_top.png`
- `mangrove_roots_side.png`
- `mangrove_roots_top.png`
- `mossy_cobblestone.png`
- `mud.png`
- `mycelium_side.png`
- `mycelium_top.png`
- `netherrack.png`
- `oak_leaves.png`
- `oak_log.png`
- `oak_log_top.png`
- `oak_planks.png`
- `oak_sapling.png`
- `obsidian.png`
- `orange_terracotta.png`
- `packed_ice.png`
- `podzol_side.png`
- `podzol_top.png`
- `powder_snow.png`
- `pumpkin_side.png`
- `pumpkin_top.png`
- `raw_copper_block.png`
- `raw_iron_block.png`
- `red_sand.png`
- `red_sandstone.png`
- `red_sandstone_bottom.png`
- `red_sandstone_top.png`
- `red_terracotta.png`
- `redstone_ore.png`
- `sand.png`
- `sandstone.png`
- `sandstone_bottom.png`
- `sandstone_top.png`
- `snow.png`
- `spruce_leaves.png`
- `spruce_log.png`
- `spruce_log_top.png`
- `stone.png`
- `sulfur.png`
- `terracotta.png`
- `tnt_bottom.png`
- `tnt_side.png`
- `tnt_top.png`
- `tuff.png`
- `water_still.png`
- `white_terracotta.png`
- `yellow_terracotta.png`

### `textures/colormap/`

- `foliage.png`
- `grass.png`

### Mob skins that moved in Minecraft 1.21.5 (any one of each pair is enough)

Minecraft 1.21.5 added biome variants and renamed the old skins to the "temperate" variants (source: Mojang's 1.21.5
changelog via Perplexity, https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5). The game
(`EntityTexture.loadFirst`) looks for the new name first and the old one second; `scripts/restore-assets.py` reports a pair
as missing only when neither exists.

- `textures/entity/pig/pig_temperate.png`, `textures/entity/pig/temperate_pig.png` or `textures/entity/pig/pig.png`
- `textures/entity/cow/cow_temperate.png`, `textures/entity/cow/temperate_cow.png` or `textures/entity/cow/cow.png`
- `textures/entity/chicken/chicken_temperate.png`, `textures/entity/chicken/temperate_chicken.png` or `textures/entity/chicken.png`

The new pig and cow files are larger than 64x32 and may use a different UV layout; the box UVs in `MobModelBuilder` are
the old layout divided by the real file size, so the first look at the real files decides whether they need re-mapping.

### `textures/entity/creeper/`

- `creeper.png`

### `textures/entity/projectiles/`

- `arrow.png`

### `textures/entity/skeleton/`

- `skeleton.png`

### `textures/entity/spider/`

- `spider.png`

### `textures/entity/zombie/`

- `zombie.png`

### `textures/gui/sprites/hud/`

- `air.png`
- `air_bursting.png`
- `armor_empty.png`
- `armor_full.png`
- `armor_half.png`
- `crosshair_attack_indicator_background.png`
- `crosshair_attack_indicator_full.png`
- `crosshair_attack_indicator_progress.png`
- `experience_bar_background.png`
- `experience_bar_progress.png`
- `food_empty.png`
- `food_full.png`
- `food_half.png`

### `textures/gui/sprites/hud/heart/`

- `container.png`
- `full.png`
- `half.png`

### `textures/gui/sprites/hud/`

- `hotbar.png`
- `hotbar_attack_indicator_background.png`
- `hotbar_attack_indicator_progress.png`
- `hotbar_selection.png`

### `textures/item/`

- `apple.png`
- `arrow.png`
- `baked_potato.png`
- `beef.png`
- `bone.png`
- `book.png`
- `bread.png`
- `brick.png`
- `charcoal.png`
- `chicken.png`
- `clay_ball.png`
- `coal.png`
- `cod.png`
- `cooked_beef.png`
- `cooked_chicken.png`
- `cooked_cod.png`
- `cooked_mutton.png`
- `cooked_porkchop.png`
- `cooked_rabbit.png`
- `cooked_salmon.png`
- `copper_ingot.png`
- `diamond.png`
- `diamond_axe.png`
- `diamond_boots.png`
- `diamond_chestplate.png`
- `diamond_helmet.png`
- `diamond_leggings.png`
- `diamond_pickaxe.png`
- `diamond_shovel.png`
- `diamond_sword.png`
- `dried_kelp.png`
- `egg.png`
- `emerald.png`
- `feather.png`
- `flint.png`
- `glowstone_dust.png`
- `gold_ingot.png`
- `golden_boots.png`
- `golden_chestplate.png`
- `golden_helmet.png`
- `golden_leggings.png`
- `gunpowder.png`
- `iron_axe.png`
- `iron_boots.png`
- `iron_chestplate.png`
- `iron_helmet.png`
- `iron_ingot.png`
- `iron_leggings.png`
- `iron_nugget.png`
- `iron_pickaxe.png`
- `iron_shovel.png`
- `iron_sword.png`
- `kelp.png`
- `lapis_lazuli.png`
- `leather.png`
- `leather_boots.png`
- `leather_chestplate.png`
- `leather_helmet.png`
- `leather_leggings.png`
- `mutton.png`
- `porkchop.png`
- `potato.png`
- `rabbit.png`
- `raw_copper.png`
- `raw_gold.png`
- `raw_iron.png`
- `redstone.png`
- `rotten_flesh.png`
- `salmon.png`
- `snowball.png`
- `spider_eye.png`
- `stick.png`
- `stone_axe.png`
- `stone_pickaxe.png`
- `stone_shovel.png`
- `stone_sword.png`
- `string.png`
- `wooden_axe.png`
- `wooden_pickaxe.png`
- `wooden_shovel.png`
- `wooden_sword.png`

Notes: `textures/block/destroy_stage_0..9.png` are loaded all-or-nothing by `CrackOverlay`. `water_still.png` is an animated strip, only the first frame is used. HUD sprites required together (else the whole HUD falls back): `hotbar`, `hotbar_selection`, `heart/container`, `heart/full`, `heart/half`, `food_empty`, `food_full`, `food_half`.

## Sounds (228 files in 66 sound groups)

Paths are relative to `sounds/`. Groups are internal IDs from `SoundRegistry` (not Minecraft event names). Several groups share files.

- **dig.stone**: `dig/stone1.ogg`, `dig/stone2.ogg`, `dig/stone3.ogg`, `dig/stone4.ogg`
- **dig.gravel**: `dig/gravel1.ogg`, `dig/gravel2.ogg`, `dig/gravel3.ogg`, `dig/gravel4.ogg`
- **dig.grass**: `dig/grass1.ogg`, `dig/grass2.ogg`, `dig/grass3.ogg`, `dig/grass4.ogg`
- **dig.sand**: `dig/sand1.ogg`, `dig/sand2.ogg`, `dig/sand3.ogg`, `dig/sand4.ogg`
- **dig.snow**: `dig/snow1.ogg`, `dig/snow2.ogg`, `dig/snow3.ogg`, `dig/snow4.ogg`
- **dig.wood**: `dig/wood1.ogg`, `dig/wood2.ogg`, `dig/wood3.ogg`, `dig/wood4.ogg`
- **dig.glass**: `random/glass1.ogg`, `random/glass2.ogg`, `random/glass3.ogg`
- **step.stone**: `step/stone1.ogg`, `step/stone2.ogg`, `step/stone3.ogg`, `step/stone4.ogg`, `step/stone5.ogg`, `step/stone6.ogg`
- **step.gravel**: `step/gravel1.ogg`, `step/gravel2.ogg`, `step/gravel3.ogg`, `step/gravel4.ogg`
- **step.grass**: `step/grass1.ogg`, `step/grass2.ogg`, `step/grass3.ogg`, `step/grass4.ogg`, `step/grass5.ogg`, `step/grass6.ogg`
- **step.sand**: `step/sand1.ogg`, `step/sand2.ogg`, `step/sand3.ogg`, `step/sand4.ogg`, `step/sand5.ogg`
- **step.snow**: `step/snow1.ogg`, `step/snow2.ogg`, `step/snow3.ogg`, `step/snow4.ogg`
- **step.wood**: `step/wood1.ogg`, `step/wood2.ogg`, `step/wood3.ogg`, `step/wood4.ogg`, `step/wood5.ogg`, `step/wood6.ogg`
- **mob.creeper.say**: `mob/creeper/say1.ogg`, `mob/creeper/say2.ogg`, `mob/creeper/say3.ogg`, `mob/creeper/say4.ogg`
- **mob.creeper.hurt**: `mob/creeper/say1.ogg`, `mob/creeper/say2.ogg`, `mob/creeper/say3.ogg`, `mob/creeper/say4.ogg`
- **mob.creeper.death**: `mob/creeper/death.ogg`
- **mob.creeper.step**: `step/grass1.ogg`, `step/grass2.ogg`, `step/grass3.ogg`, `step/grass4.ogg`, `step/grass5.ogg`, `step/grass6.ogg`
- **fuse**: `random/fuse.ogg`
- **explode**: `random/explode1.ogg`, `random/explode2.ogg`, `random/explode3.ogg`, `random/explode4.ogg`
- **arrow.shoot**: `random/bow.ogg`
- **arrow.hit**: `random/bowhit1.ogg`, `random/bowhit2.ogg`, `random/bowhit3.ogg`, `random/bowhit4.ogg`
- **mob.chicken.plop**: `mob/chicken/plop.ogg`
- **attack.strong**: `entity/player/attack/strong1.ogg`, `entity/player/attack/strong2.ogg`, `entity/player/attack/strong3.ogg`, `entity/player/attack/strong4.ogg`, `entity/player/attack/strong5.ogg`, `entity/player/attack/strong6.ogg`
- **attack.weak**: `entity/player/attack/weak1.ogg`, `entity/player/attack/weak2.ogg`, `entity/player/attack/weak3.ogg`, `entity/player/attack/weak4.ogg`
- **attack.crit**: `entity/player/attack/crit1.ogg`, `entity/player/attack/crit2.ogg`, `entity/player/attack/crit3.ogg`
- **attack.sweep**: `entity/player/attack/sweep1.ogg`, `entity/player/attack/sweep2.ogg`, `entity/player/attack/sweep3.ogg`, `entity/player/attack/sweep4.ogg`, `entity/player/attack/sweep5.ogg`, `entity/player/attack/sweep6.ogg`, `entity/player/attack/sweep7.ogg`
- **attack.knockback**: `entity/player/attack/knockback1.ogg`, `entity/player/attack/knockback2.ogg`, `entity/player/attack/knockback3.ogg`, `entity/player/attack/knockback4.ogg`
- **player.hurt**: `damage/hit1.ogg`, `damage/hit2.ogg`, `damage/hit3.ogg`
- **item.break**: `random/break.ogg`
- **xp.pickup**: `random/orb.ogg`
- **levelup**: `random/levelup.ogg`
- **eat**: `random/eat1.ogg`, `random/eat2.ogg`, `random/eat3.ogg`
- **burp**: `random/burp.ogg`
- **item.pickup**: `random/pop.ogg`
- **ambient.cave**: `ambient/cave/cave1.ogg`, `ambient/cave/cave2.ogg`, `ambient/cave/cave3.ogg`, `ambient/cave/cave4.ogg`, `ambient/cave/cave5.ogg`, `ambient/cave/cave6.ogg`, `ambient/cave/cave7.ogg`, `ambient/cave/cave8.ogg`, `ambient/cave/cave9.ogg`, `ambient/cave/cave10.ogg`, `ambient/cave/cave11.ogg`, `ambient/cave/cave12.ogg`, `ambient/cave/cave13.ogg`, `ambient/cave/cave14.ogg`, `ambient/cave/cave15.ogg`, `ambient/cave/cave16.ogg`, `ambient/cave/cave17.ogg`, `ambient/cave/cave18.ogg`, `ambient/cave/cave19.ogg`
- **ambient.rain**: `ambient/weather/rain1.ogg`, `ambient/weather/rain2.ogg`, `ambient/weather/rain3.ogg`, `ambient/weather/rain4.ogg`, `ambient/weather/rain5.ogg`, `ambient/weather/rain6.ogg`, `ambient/weather/rain7.ogg`, `ambient/weather/rain8.ogg`
- **weather.thunder**: `ambient/weather/thunder1.ogg`, `ambient/weather/thunder2.ogg`, `ambient/weather/thunder3.ogg`
- **armor.equip.generic**: `item/armor/equip_generic1.ogg`, `item/armor/equip_generic2.ogg`, `item/armor/equip_generic3.ogg`, `item/armor/equip_generic4.ogg`, `item/armor/equip_generic5.ogg`, `item/armor/equip_generic6.ogg`
- **armor.equip.leather**: `item/armor/equip_leather1.ogg`, `item/armor/equip_leather2.ogg`, `item/armor/equip_leather3.ogg`, `item/armor/equip_leather4.ogg`, `item/armor/equip_leather5.ogg`, `item/armor/equip_leather6.ogg`
- **armor.equip.iron**: `item/armor/equip_iron1.ogg`, `item/armor/equip_iron2.ogg`, `item/armor/equip_iron3.ogg`, `item/armor/equip_iron4.ogg`, `item/armor/equip_iron5.ogg`, `item/armor/equip_iron6.ogg`
- **armor.equip.gold**: `item/armor/equip_gold1.ogg`, `item/armor/equip_gold2.ogg`, `item/armor/equip_gold3.ogg`, `item/armor/equip_gold4.ogg`, `item/armor/equip_gold5.ogg`, `item/armor/equip_gold6.ogg`
- **armor.equip.diamond**: `item/armor/equip_diamond1.ogg`, `item/armor/equip_diamond2.ogg`, `item/armor/equip_diamond3.ogg`, `item/armor/equip_diamond4.ogg`, `item/armor/equip_diamond5.ogg`, `item/armor/equip_diamond6.ogg`
- **mob.pig.say**: `mob/pig/say1.ogg`, `mob/pig/say2.ogg`, `mob/pig/say3.ogg`
- **mob.pig.hurt**: `mob/pig/say1.ogg`, `mob/pig/say2.ogg`, `mob/pig/say3.ogg`
- **mob.pig.death**: `mob/pig/death.ogg`
- **mob.pig.step**: `mob/pig/step1.ogg`, `mob/pig/step2.ogg`, `mob/pig/step3.ogg`, `mob/pig/step4.ogg`, `mob/pig/step5.ogg`
- **mob.cow.say**: `mob/cow/say1.ogg`, `mob/cow/say2.ogg`, `mob/cow/say3.ogg`, `mob/cow/say4.ogg`
- **mob.cow.hurt**: `mob/cow/hurt1.ogg`, `mob/cow/hurt2.ogg`, `mob/cow/hurt3.ogg`
- **mob.cow.death**: `mob/cow/hurt1.ogg`, `mob/cow/hurt2.ogg`, `mob/cow/hurt3.ogg`
- **mob.cow.step**: `mob/cow/step1.ogg`, `mob/cow/step2.ogg`, `mob/cow/step3.ogg`, `mob/cow/step4.ogg`
- **mob.chicken.say**: `mob/chicken/say1.ogg`, `mob/chicken/say2.ogg`, `mob/chicken/say3.ogg`
- **mob.chicken.hurt**: `mob/chicken/hurt1.ogg`, `mob/chicken/hurt2.ogg`
- **mob.chicken.death**: `mob/chicken/hurt1.ogg`, `mob/chicken/hurt2.ogg`
- **mob.chicken.step**: `mob/chicken/step1.ogg`, `mob/chicken/step2.ogg`
- **mob.zombie.say**: `mob/zombie/say1.ogg`, `mob/zombie/say2.ogg`, `mob/zombie/say3.ogg`
- **mob.zombie.hurt**: `mob/zombie/hurt1.ogg`, `mob/zombie/hurt2.ogg`
- **mob.zombie.death**: `mob/zombie/death.ogg`
- **mob.zombie.step**: `mob/zombie/step1.ogg`, `mob/zombie/step2.ogg`, `mob/zombie/step3.ogg`, `mob/zombie/step4.ogg`, `mob/zombie/step5.ogg`
- **mob.skeleton.say**: `mob/skeleton/say1.ogg`, `mob/skeleton/say2.ogg`, `mob/skeleton/say3.ogg`
- **mob.skeleton.hurt**: `mob/skeleton/hurt1.ogg`, `mob/skeleton/hurt2.ogg`, `mob/skeleton/hurt3.ogg`, `mob/skeleton/hurt4.ogg`
- **mob.skeleton.death**: `mob/skeleton/death.ogg`
- **mob.skeleton.step**: `mob/skeleton/step1.ogg`, `mob/skeleton/step2.ogg`, `mob/skeleton/step3.ogg`, `mob/skeleton/step4.ogg`
- **mob.spider.say**: `mob/spider/say1.ogg`, `mob/spider/say2.ogg`, `mob/spider/say3.ogg`, `mob/spider/say4.ogg`
- **mob.spider.hurt**: `mob/spider/say1.ogg`, `mob/spider/say2.ogg`, `mob/spider/say3.ogg`, `mob/spider/say4.ogg`
- **mob.spider.death**: `mob/spider/death.ogg`
- **mob.spider.step**: `mob/spider/step1.ogg`, `mob/spider/step2.ogg`, `mob/spider/step3.ogg`, `mob/spider/step4.ogg`

### Unique files by folder

- `sounds/ambient/cave/` (19): cave1.ogg, cave10.ogg, cave11.ogg, cave12.ogg, cave13.ogg, cave14.ogg, cave15.ogg, cave16.ogg, cave17.ogg, cave18.ogg, cave19.ogg, cave2.ogg, cave3.ogg, cave4.ogg, cave5.ogg, cave6.ogg, cave7.ogg, cave8.ogg, cave9.ogg
- `sounds/ambient/weather/` (11): rain1.ogg, rain2.ogg, rain3.ogg, rain4.ogg, rain5.ogg, rain6.ogg, rain7.ogg, rain8.ogg, thunder1.ogg, thunder2.ogg, thunder3.ogg
- `sounds/damage/` (3): hit1.ogg, hit2.ogg, hit3.ogg
- `sounds/dig/` (24): grass1.ogg, grass2.ogg, grass3.ogg, grass4.ogg, gravel1.ogg, gravel2.ogg, gravel3.ogg, gravel4.ogg, sand1.ogg, sand2.ogg, sand3.ogg, sand4.ogg, snow1.ogg, snow2.ogg, snow3.ogg, snow4.ogg, stone1.ogg, stone2.ogg, stone3.ogg, stone4.ogg, wood1.ogg, wood2.ogg, wood3.ogg, wood4.ogg
- `sounds/entity/player/attack/` (24): crit1.ogg, crit2.ogg, crit3.ogg, knockback1.ogg, knockback2.ogg, knockback3.ogg, knockback4.ogg, strong1.ogg, strong2.ogg, strong3.ogg, strong4.ogg, strong5.ogg, strong6.ogg, sweep1.ogg, sweep2.ogg, sweep3.ogg, sweep4.ogg, sweep5.ogg, sweep6.ogg, sweep7.ogg, weak1.ogg, weak2.ogg, weak3.ogg, weak4.ogg
- `sounds/item/armor/` (30): equip_diamond1.ogg, equip_diamond2.ogg, equip_diamond3.ogg, equip_diamond4.ogg, equip_diamond5.ogg, equip_diamond6.ogg, equip_generic1.ogg, equip_generic2.ogg, equip_generic3.ogg, equip_generic4.ogg, equip_generic5.ogg, equip_generic6.ogg, equip_gold1.ogg, equip_gold2.ogg, equip_gold3.ogg, equip_gold4.ogg, equip_gold5.ogg, equip_gold6.ogg, equip_iron1.ogg, equip_iron2.ogg, equip_iron3.ogg, equip_iron4.ogg, equip_iron5.ogg, equip_iron6.ogg, equip_leather1.ogg, equip_leather2.ogg, equip_leather3.ogg, equip_leather4.ogg, equip_leather5.ogg, equip_leather6.ogg
- `sounds/mob/chicken/` (8): hurt1.ogg, hurt2.ogg, plop.ogg, say1.ogg, say2.ogg, say3.ogg, step1.ogg, step2.ogg
- `sounds/mob/cow/` (11): hurt1.ogg, hurt2.ogg, hurt3.ogg, say1.ogg, say2.ogg, say3.ogg, say4.ogg, step1.ogg, step2.ogg, step3.ogg, step4.ogg
- `sounds/mob/creeper/` (5): death.ogg, say1.ogg, say2.ogg, say3.ogg, say4.ogg
- `sounds/mob/pig/` (9): death.ogg, say1.ogg, say2.ogg, say3.ogg, step1.ogg, step2.ogg, step3.ogg, step4.ogg, step5.ogg
- `sounds/mob/skeleton/` (12): death.ogg, hurt1.ogg, hurt2.ogg, hurt3.ogg, hurt4.ogg, say1.ogg, say2.ogg, say3.ogg, step1.ogg, step2.ogg, step3.ogg, step4.ogg
- `sounds/mob/spider/` (9): death.ogg, say1.ogg, say2.ogg, say3.ogg, say4.ogg, step1.ogg, step2.ogg, step3.ogg, step4.ogg
- `sounds/mob/zombie/` (11): death.ogg, hurt1.ogg, hurt2.ogg, say1.ogg, say2.ogg, say3.ogg, step1.ogg, step2.ogg, step3.ogg, step4.ogg, step5.ogg
- `sounds/random/` (21): bow.ogg, bowhit1.ogg, bowhit2.ogg, bowhit3.ogg, bowhit4.ogg, break.ogg, burp.ogg, eat1.ogg, eat2.ogg, eat3.ogg, explode1.ogg, explode2.ogg, explode3.ogg, explode4.ogg, fuse.ogg, glass1.ogg, glass2.ogg, glass3.ogg, levelup.ogg, orb.ogg, pop.ogg
- `sounds/step/` (31): grass1.ogg, grass2.ogg, grass3.ogg, grass4.ogg, grass5.ogg, grass6.ogg, gravel1.ogg, gravel2.ogg, gravel3.ogg, gravel4.ogg, sand1.ogg, sand2.ogg, sand3.ogg, sand4.ogg, sand5.ogg, snow1.ogg, snow2.ogg, snow3.ogg, snow4.ogg, stone1.ogg, stone2.ogg, stone3.ogg, stone4.ogg, stone5.ogg, stone6.ogg, wood1.ogg, wood2.ogg, wood3.ogg, wood4.ogg, wood5.ogg, wood6.ogg

## Known risk with the newest Minecraft version

The sound paths use older names for some groups (for example `random/glass1.ogg`). Whether each file still exists in the newest assets is decided by `scripts/restore-assets.py` against Mojang's asset index and reported, never substituted.
