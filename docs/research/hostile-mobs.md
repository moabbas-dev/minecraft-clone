# Hostile mobs (Perplexity research, Java 1.18+)

## Natural spawning (all four classic hostiles)
- Overworld light rule: **block light 0 and sky light <= 7** (not "combined <= 7").
- Position: more than 24 blocks from a player and within 128; solid supporting block, 2 blocks of free space,
  no liquid. Packs up to 4. Cap about 70 (single player, full chunks). Peaceful: no spawns.
- Despawn: over 128 blocks from every player -> immediately; otherwise after 30 s without a player within
  32 blocks, 1/800 per tick (in bright light above 12, 10 s). Picked-up items make a mob persistent.

## Zombie
- 20 HP, 2 armor, speed 0.23, follow range 35. Melee damage Easy 2.5 / Normal 3 / Hard 4.5, attack interval 20 ticks.
- 5% baby: half size (0.3 x 0.975), speed x1.5 (0.345), 12 XP, burns in sunlight too.
- Sunlight: ignites in direct daylight (sky visible, not raining, not in water), 160 ticks of fire refreshed while
  exposed, 1 HP/s burn. A helmet protects (and loses durability); water/rain extinguish.
- Drops: rotten flesh 0-2; 2.5% total of one iron ingot / carrot / potato (about 0.83% each) on player kill. XP 5.
- Hard only: reinforcements, door breaking; zombie-villager conversion is out of scope (no villagers).

## Skeleton
- 20 HP; targets within 16; shoots within 15 blocks, every ~3 s (Hard 2 s); arrow damage Easy 2-4, Normal 3-4,
  Hard 4-5; strafes/retreats; burns in daylight; drops 0-2 bones and 0-2 arrows; XP 5.

## Creeper
- 20 HP; chases within 16; within 3 blocks with line of sight it stops and swells; fuse 30 ticks; explosion power 3
  (charged 6); cancels if the target leaves range/sight; drops 0-2 gunpowder; XP 5.

## Spider
- 16 HP, speed 0.3, melee Easy 2 / Normal 2 / Hard 3; sees within 16; hostile at light <= 11, neutral at >= 12
  unless provoked; climbs walls; drops string 0-2, spider eye 1/3 on player kill; XP 5.
