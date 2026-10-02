# Implementation plan: swords, armor, passive mobs (derived from the research notes in this folder)

Research docs: `melee-combat.md`, `armor.md`, `passive-mobs.md`, `knockback-persistence-targeting.md`.

## Decisions
- Combat model: Java 1.9+ (damage values, attack speed, charge `0.2 + 0.8*q^2`, crit 1.5x, sweep, sprint knockback).
- Tiers that exist in this clone: wood / stone / iron for swords & tools; leather / iron / gold armor (diamond and
  netherite items do not exist; chainmail has no recipe in vanilla). Gold tools are not added.
- Durability is real: `ItemStack` gets a `damage` field (uses spent). Damageable items stack to 1. Persisted by
  packing `damage << 8 | count` into the existing per-slot count int (old saves have damage 0).
- Hurt immunity follows vanilla: 10 ticks, a larger hit during the window applies only the excess.
- Armor: 4 slots in `PlayerArmor`; reduction formula from `armor.md`; armor-bypassing sources: fall, starvation,
  drowning, void, generic-test damage. Pieces lose `max(1, floor(D/4))` durability per reducible hit.
- Knockback: `vx' = vx/2 - s*dx` (s=0.4, +0.5 on a sprinting player attack), vertical `min(0.4, vy/2+s)` if grounded.
- Mobs get armor points (zombie 2) and the same reduction formula; hurt tint 0.5 s; death tilt over 1 s.
- Passive mobs: pig (10 HP), cow (10 HP), chicken (4 HP, no fall damage, egg every 5-10 min); goals: panic,
  stroll, look at player, random look; spawn in packs on newly generated chunks (1 in 10) and via the spawner on
  grass with light >= 9, cap 10; they never despawn.
- Persistence: `mobs.dat` per world (type, pos, motion, yaw, health, per-type timer).
- XP: zombie 5, animals 1-3 on a player kill, through the existing `PlayerSurvival.addExperience`.
- Out of scope (documented): breeding/taming/tempting, enchantments, repair/anvil, shields, sounds (no mob sounds
  exist in this project), real mob skins (procedural colored tiles instead).

## Order
1. Items, durability, recipes, textures.  2. Damage core (hurt excess rule, armor, knockback).  3. Player attack
(cooldown, crit, sweep, reach, durability use) + armor equip/HUD/inventory slots.  4. Mob damage upgrades (armor,
fall damage, hurt tint, death tilt, XP).  5. Passive mobs (models, goals, drops, spawning).  6. Mob persistence.
Each phase compiles and passes tests before the next.
