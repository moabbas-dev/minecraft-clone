# Day/night cycle and weather (Perplexity research, Java 1.20+)

- Day = 24000 ticks = 20 minutes. Tick 0 sunrise, 6000 noon, 12000 sunset, 13000 night, 18000 midnight, 23000 sunrise.
- Celestial angle: `f = (t % 24000)/24000 - 0.25` (wrapped to [0,1)); `f2 = 1 - (cos(pi f) + 1)/2`; `angle = f + (f2 - f)/3`.
- Sky light reduction ("darkening"): `11 * (1 - clamp(cos(angle * 2 pi) * 2 + 0.5, 0, 1))`.
- Moon: 8 phases, index `dayNumber % 8` (full, waning gibbous, third quarter, waning crescent, new, waxing crescent,
  first quarter, waxing gibbous). The moon phase texture is one 4x2 sheet (textures/environment/moon_phases.png).
- Weather durations (ticks): clear 12000-180000, rain 12000-24000, thunder 3600-15600.
- Beds usable from 12542 to 23459 (earlier in rain/thunder) - no beds exist yet.
- Lightning: 5 damage on normal, can ignite. Light: torch 14, glowstone 15, lava 15.

Project status: our day is 600 s (10 min) -> change to 1200 s; weather timers and the moon texture/phases are
to be checked against this table.
