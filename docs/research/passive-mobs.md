# Passive mobs (pig, cow, chicken) - research notes

Source: Perplexity deep research (Oct 2026) citing minecraft.wiki (Pig, Cow, Chicken, Mob_spawning, Hitbox,
Attribute), Forge/Fabric mapping javadocs for the goal registrations.

| | Pig | Cow | Chicken |
|---|---|---|---|
| Max health | 10 | 10 | 4 |
| Hitbox (w x h), eye | 0.9 x 0.9, 0.765 | 0.9 x 1.4, 1.3 | 0.4 x 0.7, 0.644 |
| Movement attribute | 0.25 | 0.20 | 0.25 |
| Drops | 1-3 raw porkchop (cooked if burning) | 1-3 raw beef (steak if burning) + 0-2 leather | 1 raw chicken (cooked if burning) + 0-2 feather |
| XP (player kill) | 1-3 | 1-3 | 1-3 |
| Panic speed multiplier | 1.25 | 1.25 | 1.4 |
| Natural spawn weight / pack | 5 / 4 | 4 / 4 | 5 / 4 |

Drop count rule: "1-3" and "0-2" are uniform picks of the count (each value 1/3), ignoring Looting.

## AI goals (priority: behavior)
- Pig: 0 float, 1 panic x1.25, 4 tempt x1.2 (carrot/potato/beetroot), 6 water-avoiding stroll x1.0,
  7 look at player (6 blocks), 8 random look around.
- Cow: 0 float, 1 panic x1.25, 3 tempt (wheat), 5 water-avoiding stroll, 6 look at player (6), 7 random look.
- Chicken: 0 float, 1 panic x1.4, 3 tempt (seeds), 5 water-avoiding stroll, 6 look at player (6), 7 random look.
- Panic has no fixed duration: flee along a path until it ends or is interrupted (re-roll a flee target).
- Random stroll: default interval 120, the effective start chance is ~1 in 60 goal checks (not every tick).
  Stroll target: random point within ~10 blocks horizontal / 7 vertical, speed multiplier 1.0.
- Breeding/tempting out of scope here (no breeding system, no wheat/seeds items).

## Spawning
- Light >= 9 (not sky-light only), grass block below, room above. Creature cap 10 per 17x17-chunk area.
- Animals persist (no natural despawn). Initial chunk generation: ~1 in 10 new chunks gets a pack of 1-4 of one
  species, without the grass/light check.

## Chicken specifics
- Lays one egg every 6000-12000 ticks (5-10 minutes) as an item; wings flap, slow fall (no fall damage).

## Damage / environment
- Fall damage ~ceil(max(0, fall distance - 3)) HP (chickens immune). Air 15 s then 2 HP/s drowning. Fire 1 HP/s.

## Animation
Four-legged walk: one diagonal leg pair rotates `cos(limbSwing*0.6662) * 1.4 * limbSwingAmount`, the other
diagonal pair adds pi inside the cosine.
