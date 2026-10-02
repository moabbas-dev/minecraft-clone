# Creeper, skeleton, spider: models, AI numbers, explosion and arrow mechanics (Perplexity research)

Companion to `hostile-mobs.md` (stats, spawn rules, drops). Pixel coordinates use the vanilla model frame
(Y down, ground at y = 24, 16 px = 1 block); skins are the classic 64x32 files from the `1.21.4-assets` tag.

## Creeper (`entity/creeper/creeper.png`, hitbox 0.6 x 1.7)
- Head 8x8x8 uv (0,0) pivot (0,6,0); body 8x12x4 uv (16,16) pivot (0,6,0); four legs 4x6x4 uv (0,16) at pivots
  (+-2, 18, +-4) (front z = -4).
- Starts swelling within 3 blocks with line of sight, fuse 30 ticks, explosion power 3; cancels when the player leaves.
- Swell fraction s: scale = ((1 + 0.4 q) w on X/Z, (1 + 0.1 q) / w on Y) with `w = 1 + sin(100 s) s 0.01`,
  `q = s^4`; white overlay while `floor(10 s)` is odd.
- Explosion entity damage: `impact = (1 - d / (2 p)) * exposure`, raw `floor((impact^2 + impact) / 2 * 7 * 2 p + 1)`;
  difficulty Easy min(D/2 + 1, D), Normal D, Hard 1.5 D. Knockback along the impact vector.
- Blocks: rays through a 16x16x16 grid surface (1352), strength `p (0.7..1.3)`, step 0.3, each step loses 0.225 and
  non-air blocks also lose `(blastResistance + 0.3) * 0.3`; destroyed while strength stays positive; each destroyed
  block drops with probability `1 / p` (1/3).
- Sounds: `mob/creeper/say1-4` (hurt), `mob/creeper/death`, `random/fuse`, `random/explode1-4`.

## Skeleton (`entity/skeleton/skeleton.png`, hitbox 0.6 x 1.99)
- Head (0,0) 8x8x8; body (16,16) 8x12x4; arms 2x12x2 uv (40,16) pivots (+-5, 2, 0); legs 2x12x2 uv (0,16) pivots
  (+-2, 12, 0); left limbs mirrored.
- Bow AI: shoots within 15 blocks with line of sight, retreats inside 4 blocks, closes in beyond 14; cooldown 40 ticks on
  Easy/Normal, 20 on Hard (classic values); inaccuracy 14 - 4 x difficultyId (10 / 6 / 2); aims 0.2 blocks higher per
  horizontal block; launch speed 1.6 blocks/tick.
- Arrow: damage `ceil(2 v)`, gravity 0.05 b/tick^2 (20 b/s^2), drag 0.99/tick, sticks in blocks and despawns after 1200
  ticks. Skeletons burn in daylight.
- Sounds: `mob/skeleton/say1-3`, `hurt1-4`, `death`, `step1-4`, `random/bow`, `random/bowhit1-4`.

## Spider (`entity/spider/spider.png`, hitbox 1.4 x 0.9)
- Head 8x8x8 uv (32,4) pivot (0,15,-3); neck 6x6x6 uv (0,0) pivot (0,15,0); abdomen 10x8x12 uv (0,12) pivot (0,15,9);
  eight legs 16x2x2 uv (18,0) at pivots (-+4, 15, z) for z = 2, 1, 0, -1 with neutral (Y, Z) rotations
  (45, -45), (22.5, -33.3), (-22.5, -33.3), (-45, -45) degrees (mirrored on the other side).
- Hostile at light <= 11, neutral at >= 12 unless hurt. Climbs a wall it walks into (not ceilings). Leap attack at about
  2-4 blocks. Melee 2 HP Easy/Normal, 3 Hard.
- Sounds: `mob/spider/say1-4` (also hurt), `death`, `step1-4`.
