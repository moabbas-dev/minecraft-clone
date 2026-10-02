# options.txt and Options screens (Perplexity research, Java 1.20.x - 1.21.8 layout)

## File format
`key:value`, one per line, in `options.txt` (NOT java-properties `key=value`). Unknown/missing keys keep defaults.

## Keys, defaults, ranges
| Key | Default | Notes |
|---|---|---|
| version | data version | release specific; missing => regenerated |
| autoJump | false | Controls |
| enableVsync | true | |
| entityShadows | true | |
| discrete_mouse_scroll | false | note underscores |
| invertYMouse | false | |
| showSubtitles | false | |
| directionalAudio | false | |
| fullscreen | false | |
| bobView | true | View Bobbing |
| toggleCrouch | false | Sneak: Hold / Toggle |
| toggleSprint | false | Sprint: Hold / Toggle |
| mouseSensitivity | 0.5 | 0-1; slider shows 100% at 0.5 |
| fov | 0.0 | stored -1..1; `degrees = 70 + 40 x stored` (30-110) |
| screenEffectScale | 1.0 | Distortion Effects |
| fovEffectScale | 1.0 | FOV Effects (sprint FOV change scaling) |
| gamma | 0.5 | Brightness 0-1: 0 "Moody", 1 "Bright", else the percentage |
| renderDistance | 12 | 2-32 chunks |
| simulationDistance | 12 | 5-32 |
| entityDistanceScaling | 1.0 | 0.5-5.0 |
| guiScale | 0 | 0 = Auto |
| particles | 0 | 0 All, 1 Decreased, 2 Minimal |
| maxFps | 120 | 10-250; 260 = Unlimited |
| graphicsMode | 1 | 0 Fast, 1 Fancy, 2 Fabulous |
| ao | true | Smooth Lighting |
| renderClouds | true | true Fancy, "fast" Fast, false Off |
| attackIndicator | 1 | 0 Off, 1 Crosshair, 2 Hotbar |
| biomeBlendRadius | 2 | 0-7 (displayed 2r+1 squared) |
| mipmapLevels | 4 | 0-4 |
| soundCategory_master/music/record/weather/block/hostile/neutral/player/ambient/voice | 1.0 | 0-1 |

Key binds: `key_key.attack:key.mouse.left`, `key_key.use:key.mouse.right`, forward/left/back/right `w a s d`,
`key_key.jump:key.keyboard.space`, `key_key.sneak:key.keyboard.left.shift`, `key_key.sprint:key.keyboard.left.control`,
`key_key.drop:key.keyboard.q`, `key_key.inventory:key.keyboard.e`, `key_key.chat:key.keyboard.t`,
`key_key.playerlist:key.keyboard.tab`, `key_key.pickItem:key.mouse.middle`, `key_key.command:key.keyboard.slash`,
`key_key.screenshot:key.keyboard.f2`, `key_key.togglePerspective:key.keyboard.f5`, `key_key.fullscreen:key.keyboard.f11`,
`key_key.swapOffhand:key.keyboard.f`, `key_key.hotbar.1..9:key.keyboard.1..9`.

## Screens
- Options hub: FOV slider ("FOV: Normal" at 70, "Quake Pro" at 110), Difficulty button + lock (in a world), sub-screen buttons.
  Difficulty is stored in the world (`level.dat`), not options.txt; 0 Peaceful, 1 Easy, 2 Normal, 3 Hard.
- Video Settings: Graphics, Render Distance ("12 chunks"), Simulation Distance, Smooth Lighting, Max Framerate
  ("120 fps" / "Unlimited"), VSync, View Bobbing, GUI Scale, Attack Indicator, Brightness (Moody / n / Bright),
  Clouds, Fullscreen, Particles, Mipmap Levels, Entity Shadows, Distortion Effects, Entity Distance, FOV Effects.
- Music & Sounds sliders in order: Master, Music, Jukebox/Noteblocks, Weather, Blocks, Hostile Creatures,
  Friendly Creatures, Players, Ambient/Environment, Voice/Speech; plus Show Subtitles, Directional Audio.
- Controls: Sneak Hold/Toggle, Sprint Hold/Toggle, Auto-Jump; Mouse Settings (Sensitivity, Invert Mouse, Scroll
  Sensitivity, Discrete Scrolling) and Key Binds. Rebinding: click a binding, press a key or mouse button ("> key <"
  while waiting); duplicates highlighted red; Reset Keys.

## Project decisions
- Write/read the vanilla `key:value` format with vanilla key names; still read the old `key=value` file once to migrate.
- Keep options we actually have; add ones that now have meaning: gamma, maxFps, attackIndicator, toggleSprint,
  toggleCrouch, autoJump (if implemented), fovEffectScale, sound categories hostile/neutral (replacing MOBS), key binds.
