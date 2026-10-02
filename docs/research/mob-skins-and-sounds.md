# Original mob skins and sounds (Perplexity research)

## Asset source (personal local use, gitignored `textures/` and `sounds/`)
- Raw files: `https://raw.githubusercontent.com/misode/mcmeta/<ref>/assets/minecraft/<path>`.
- Use ref `1.21.4-assets` (verified HTTP 200): the classic, pre-1.21.5 skin layout. The moving `assets` branch
  only has the 1.21.5+ `temperate_*` variants, whose cow model and texture sizes differ - do NOT mix them with the
  layouts below. Sounds are also in the `assets` branches (`sounds/mob/pig/say1.ogg` verified).
- Mojang's EULA allows personal use but not redistribution, so the files stay out of git (like the existing icons).

## Skin files (classic layout, 1.20-1.21.4)
`textures/entity/pig/pig.png` 64x32, `textures/entity/cow/cow.png` 64x32, `textures/entity/chicken.png` 64x32,
`textures/entity/zombie/zombie.png` 64x64.

## Box UV unwrap (pixels; box texture offset (u,v), size w x h x d)
Faces: Top `[u+d, v, u+d+w, v+d)`, Bottom `[u+d+w, v, u+d+2w, v+d)`, then the four side strips at
`v+d .. v+d+h`: West `[u, ..., u+d)`, North(front, -Z) `[u+d, ..., u+d+w)`, East `[u+d+w, ..., u+2d+w)`,
South(back) `[u+2d+w, ..., u+2d+2w)`.

| Part | Texture offset | Box w x h x d | Pivot (model px, Y down) |
|---|---|---|---|
| Pig head | (0,0) | 8x8x8 | (0,12,-6) |
| Pig snout | (16,16) | 4x3x1 | head |
| Pig body | (28,8) | 10x16x8, rotated 90 deg about X | (0,11,2) |
| Pig legs (x4) | (0,16) | 4x6x4 | hind (+-3,18,7), front (+-3,18,-5) |
| Cow head | (0,0) | 8x8x6 | (0,4,-8) |
| Cow horns (x2) | (22,0) | 1x3x1 | head, at x=-5 / x=4, y=-5, z=-4 |
| Cow body | (18,4) | 12x18x10, rot 90 about X | (0,5,2) |
| Cow udder | (52,0) | 4x6x1 | body |
| Cow legs (x4) | (0,16) | 4x12x4 | hind (+-4,12,7), front (+-4,12,-6) |
| Chicken head | (0,0) | 4x6x3 | (0,15,-4) |
| Chicken beak | (14,0) | 4x2x2 | head |
| Chicken wattle | (14,4) | 2x2x2 | head |
| Chicken body | (0,9) | 6x8x6, rot 90 about X | (0,16,0) |
| Chicken wings (x2) | (24,13) | 1x4x6 | (+-3,13,0) |
| Chicken legs (x2) | (26,0) | 3x5x3 | (-2,19,1) / (1,19,1) |
| Zombie head | (0,0) | 8x8x8 | (0,0,0) |
| Zombie torso | (16,16) | 8x12x4 | |
| Zombie right arm | (40,16) | 4x12x4 | (-5,2,0) |
| Zombie left arm | (32,48) | 4x12x4 mirrored | (5,2,0) |
| Zombie right leg | (0,16) | 4x12x4 | (-1.9,12,0) |
| Zombie left leg | (16,48) | 4x12x4 mirrored | (1.9,12,0) |

Model scale: 16 model pixels = 1 block. The body boxes of pig/cow/chicken are rotated 90 degrees about X, so
their w x h x d box is laid out with h running along the animal's length (z); in world space the body is
`w` wide, `d` tall and `h` long.

## Sounds (`sounds/` relative path; volume x pitch notes)
| Event | Files | Notes |
|---|---|---|
| pig ambient / hurt | `mob/pig/say1-3` | |
| pig death | `mob/pig/death` | |
| pig step | `mob/pig/step1-5` | step volume 0.15 |
| cow ambient | `mob/cow/say1-4` | volume 0.4 |
| cow hurt / death | `mob/cow/hurt1-3` | volume 0.4 |
| cow step | `mob/cow/step1-4` | 0.15 |
| chicken ambient | `mob/chicken/say1-3` | file volume 0.8 |
| chicken hurt / death | `mob/chicken/hurt1-2` | |
| chicken step | `mob/chicken/step1-2` | file volume 0.5 x 0.15 |
| chicken egg | `mob/chicken/plop` | |
| zombie ambient | `mob/zombie/say1-3` | |
| zombie hurt | `mob/zombie/hurt1-2` | |
| zombie death | `mob/zombie/death` | |
| zombie step | `mob/zombie/step1-5` | 0.15 |
| player attack strong | `entity/player/attack/strong1-6` | 0.6-0.7 |
| player attack weak | `entity/player/attack/weak1-4` | 0.7 |
| player attack crit | `entity/player/attack/crit1-3` | 0.7 |
| player attack sweep | `entity/player/attack/sweep1-7` | 0.7 |
| player attack knockback | `entity/player/attack/knockback1-4` | 0.7 |
| player hurt | `damage/hit1-3` | |
| armor equip | `item/armor/equip_{generic,leather,iron,gold,diamond}1-6` | |
| item break | `random/break` | |

Playback: vocalization pitch randomly about 0.8-1.2 (babies 1.3-1.7); step volume 0.15, pitch 1.0.
Ambient: each tick `random.nextInt(1000) < ambientCounter++`; after a sound the counter resets to
`-ambientInterval` (80 ticks), so the sound is probabilistic, roughly one per 4-10 seconds.
