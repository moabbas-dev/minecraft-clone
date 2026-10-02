# Survival mechanics: hunger, health, XP, drowning, death (Perplexity research, Java 1.20+)

20 ticks = 1 s, 2 HP = 1 heart. Source: minecraft.wiki (Hunger, Exhaustion, Experience, Healing, Damage) via Perplexity.

## Hunger
- Start: health 20, food 20, saturation 5, exhaustion 0. Saturation never exceeds food level.
- Exhaustion >= 4.0: subtract 4.0 exhaustion, then 1 saturation (or 1 food if saturation is 0).
- Eating: `food' = min(20, food + restored)`, `saturation' = min(food', saturation + sat)`.
- Exhaustion per action: sprint 0.1/m (no sprint at food <= 6), jump 0.05, sprint-jump 0.2, swim 0.01/m,
  break block 0.005, landed attack 0.1, taking armor-protected damage 0.1 per event, regeneration 6.0 per HP.
- Natural regen: food >= 18 -> 1 HP / 80 ticks. Fast regen at food 20 and saturation > 0: every 10 ticks heal
  `min(saturation, 6) / 6` HP (costs 6 exhaustion per HP).
- Starvation (food 0): 1 HP / 80 ticks; Easy stops at 10 HP, Normal at 1 HP, Hard kills.
- Eating takes 32 ticks (dried kelp 16); cannot eat at full food (except golden apples etc.).
- Raw chicken 30% Hunger I 30 s, rotten flesh 80% Hunger I 30 s (needs a status-effect system).

## Food table (food points / saturation)
Apple 4/2.4, baked potato 5/6, bread 5/6, carrot 3/3.6, cooked chicken 6/7.2, cooked cod 5/6, cooked mutton 6/9.6,
cooked porkchop 8/12.8, cooked rabbit 5/6, cooked salmon 6/9.6, steak 8/12.8, dried kelp 1/0.6, melon 2/1.2,
potato 1/0.6, raw beef 3/1.8, raw chicken 2/1.2, raw cod 2/0.4, raw mutton 2/1.2, raw porkchop 3/1.8,
raw rabbit 3/1.8, raw salmon 2/0.4, rotten flesh 4/0.8, spider eye 2/3.2.

## Damage
- Drowning: air 300 ticks; at -20 air take 2 HP and reset air to 0 (2 HP / s while submerged).
- Fall: `max(0, ceil(fallDistance - 3))`; water resets fall distance.
- Lava 4 HP / 10 ticks + ignites; fire 1 HP / 10 ticks standing in it, burning 1 HP / s; void (y < -64 here) 4 HP / 10 ticks.
- Hurt window 10 ticks: only the excess over the last hit applies (already implemented).

## Experience
- Level cost: `2L+7` (0-15), `5L-38` (16-30), `9L-158` (31+). Total to reach L: `L^2+6L` (<=16),
  `2.5L^2-40.5L+360` (17-31), `4.5L^2-162.5L+2220` (32+). Bar = `(X - T(L)) / (T(L+1) - T(L))`.
- Orbs attract within 7.25 blocks; despawn after 5 min; sizes 2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1.
- Mob XP: passive 1-3, zombie/skeleton/creeper/spider 5, baby zombie 12.

## Death and respawn
- Items drop at the death spot; XP drops as orbs worth `min(7 x level, 100)`; level/progress reset.
- Death screen shows score = total XP collected this life. Respawn at world spawn (or bed) with full stats.
- Dropped items despawn after 6000 ticks.
