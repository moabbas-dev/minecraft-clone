# Where this clone differs from Minecraft Java - audit and plan

Method: audited the code (constants, missing systems) against the Perplexity research notes in this folder
(`movement-controls-items.md`, `survival-mechanics.md`, `hostile-mobs.md`, `blocks-and-drops.md`,
`day-night-weather.md`, `options-and-screens.md`). Status column: DONE = implemented in the vanilla-alignment
milestone, TODO = documented backlog (not implemented), N/A = out of scope (no such system yet).

## Options / screens
| Deviation | Status |
|---|---|
| options.txt used `key=value` and invented key names | DONE: vanilla `key:value`, vanilla keys, fov stored -1..1, legacy file migrated |
| Missing options: brightness (gamma), max framerate, attack indicator, toggle sprint/sneak, auto-jump, FOV effects | DONE |
| Sound sliders: no Hostile / Friendly Creatures, music/jukebox/voice | DONE (hostile, neutral active; music/record/voice listed, inactive: nothing plays them) |
| Key Binds screen was read-only | DONE: click-to-rebind, conflicts in red, Reset Keys, stored as `key_key.*` |
| Difficulty cycled 3 values, no Peaceful, no lock | DONE: Peaceful/Easy/Normal/Hard + lock (saved per world) |
| Render distance 2-12, default 6 | DONE range 2-16 (vanilla caps 16 on weak systems), default stays 6 for performance |

## Player
| Deviation | Status |
|---|---|
| Walk 4.3 / sprint x1.6 (vanilla 4.317 / x1.3 = 5.612) | DONE |
| Sprint FOV +5 degrees (vanilla x1.15, scaled by FOV Effects) | DONE |
| No Q / Ctrl+Q drop, no pick block, no F1 / F2 / F11 | DONE (F5 third person: TODO, needs a player model) |
| Dropped-item pickup delay 0.5 s for everything (vanilla: 2 s for thrown) | DONE |
| Auto-jump missing | DONE |
| No drowning / air bubbles | DONE |
| No XP levels or bar, score | DONE (XP orbs as entities: TODO, XP is granted directly) |
| Death keeps the inventory (vanilla drops it, XP drops) | DONE |
| Eating was instant (vanilla 32 ticks held, kelp 16) | DONE |

## World / blocks
| Deviation | Status |
|---|---|
| Day 10 min (vanilla 20 min) | DONE |
| Weather timers shorter than vanilla | DONE |
| Gold ore mined with stone pickaxe, obsidian without diamond | DONE (vanilla tiers) |
| Break time ignores "wrong tool" x5 vs x1.5, airborne /5, underwater /5 | DONE |
| Leaves never drop sapling / stick / apple | DONE |
| Gravel -> flint, clay -> 4 clay balls, snow block -> snowballs, glowstone -> dust, bookshelf -> 3 books | DONE (new items) |
| Sand / gravel do not fall | DONE |
| Water / lava do not flow; no buckets; no fire; TNT inert | TODO (needs fluid levels in the chunk format) |
| Leaf decay | TODO |
| Crops, doors, beds, ladders, slabs, stairs, fences | N/A (no such blocks) |

## Mobs
| Deviation | Status |
|---|---|
| Difficulty did not change mob damage (vanilla zombie 2.5 / 3 / 4.5) | DONE |
| Peaceful: hostile mobs still spawned | DONE |
| Zombie does not burn in daylight, no baby zombies, no rare drops | DONE |
| Hostile spawn rule: combined light <= 7 (vanilla block light 0 and sky <= 7); no despawn rules | DONE |
| Skeleton, creeper, spider missing | TODO (each needs a model, AI, sounds, new drop items) |
| Sheep, villagers, breeding, other animals | N/A / TODO |
