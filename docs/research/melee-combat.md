# Melee combat, tools, durability - research notes

Source: Perplexity deep research (Oct 2026) citing minecraft.wiki (Damage, Sword, Axe, Pickaxe, Shovel,
Durability, Sweeping_Edge, Knockback, Zombie, Attribute). This clone follows the established Java 1.9+ cooldown
system (not the 1.8 no-cooldown one).

## Attack damage (HP; 2 HP = 1 heart), 1.9+
| Tier | Sword | Axe | Pickaxe | Shovel |
|---|---|---|---|---|
| Wood | 4 | 7 | 2 | 2.5 |
| Stone | 5 | 9 | 3 | 3.5 |
| Iron | 6 | 9 | 4 | 4.5 |
| Gold | 4 | 7 | 2 | 2.5 |
| Diamond | 7 | 9 | 5 | 5.5 |
Bare hand = 1. (Gold/diamond/netherite tools do not exist in this clone.)

## Attack speed (attacks/second) -> cooldown period T = 20 / speed ticks
- Sword 1.6 (T 12.5). Axe: wood/stone 0.8 (T 25), iron 0.9 (T 22.22), gold/diamond 1.0 (T 20). Pickaxe 1.2
  (T 16.67). Shovel 1.0 (T 20). Hand/other 4.0 (T 5).

## Charge scaling
t = ticks since the last attack (or item switch). q = min(1, (t + 0.5) / T). Base-damage multiplier = 0.2 + 0.8 * q^2.
Special attacks (critical, sweep, sprint knockback) require q >= 0.9 (~84.8% damage).

## Critical hit
1.5x damage while falling: airborne (not on ground), moving downward, not in water/flying/riding/blind/slow-falling,
and not a sprint-knockback hit. Applied after the charge scaling.

## Sweep (swords)
Needs q >= 0.9, on ground, not a crit, not a sprint hit. Other entities within the primary target's hitbox expanded
1 block horizontally / 0.25 vertically (feet within 3 blocks of the attacker) take 1 HP; knockback 80% of base.

## Knockback
Base 0.4 strength; sprint hit adds +0.5 strength. (Formula in knockback-persistence-targeting.md.)

## Hurt invulnerability
After damage, 10 ticks (0.5 s) of immunity. During it: a hit <= the recorded last damage does nothing; a larger hit
applies only the EXCESS (new - last) and does not restart the period. A zero-effect hit spends no durability.

## Durability (max uses)
Wood 59, stone 131, iron 250, gold 32, diamond 1561, netherite 2031 for all of sword/axe/pickaxe/shovel.
Cost per action: sword: attack mob 1, break a non-instant block 2, instant block 0; axe/pickaxe/shovel: attack mob 2,
break a non-instant block 1. At 0 the item breaks and disappears.

## Recipes
Swords: two material items in a column above one stick (planks / cobblestone / iron ingot / gold ingot / diamond).
Planks, cobblestone and ingots exist in this clone; repair/anvil/enchant are out of scope.

## Zombie as a target
20 HP and 2 natural armor points (toughness 0): taken = D * (1 - max(0.4, 2 - D/2) / 25) -> a 4 HP hit does 3.936.
Drops: 0-2 rotten flesh (+ rare 0.83% iron ingot / carrot / potato each). XP 5 (adult) on a player kill.
