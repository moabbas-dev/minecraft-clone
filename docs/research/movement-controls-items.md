# Player movement, controls and dropped items (Perplexity research, Java 1.20+)

## Speeds (20 ticks/s)
Walk 4.317 b/s (0.21585/tick), sprint 5.612 b/s (0.2806), sneak 1.295 b/s (0.06475), sprint-jump ~7.13 b/s average,
surface swim ~2.2 b/s, sprint-swim ~5.612 b/s, wading ~1.97 b/s.

## Physics per tick
- Ground: velocity retained x0.546 (0.91 x slipperiness 0.6); input acceleration ~0.098 walking, x1.3 sprinting.
- Air: input accel ~0.02 (sprint 0.026), retention 0.91 per tick; takeoff momentum is kept.
- Jump: vy = +0.42 blocks/tick (height ~1.2522); sprint-jump also adds +0.2 b/tick forward.
- Vertical: `vy' = (vy - 0.08) * 0.98`, terminal -3.92. Step height 0.6.
- Dimensions: standing 0.6 x 1.8, eye 1.62; sneaking 0.6 x 1.5, eye 1.27; swimming 0.6 x 0.6, eye 0.4.
- Sprint start: double-tap W (~7 tick window) or hold Ctrl while moving forward, food > 6, not sneaking/using item.
  Stop: forward input released, food <= 6, sneaking, walking into a wall. FOV: +30% speed modifier -> ~1.15x FOV
  (scaled by FOV Effects option).
- Sneak edge guard: when grounded and sneaking, horizontal movement that would leave no support across a drop >= 0.6
  is blocked (per axis).
- Ladder: horizontal cap 0.15, descend cap -0.15 b/tick, climbing up ~0.1176/tick (2.35 b/s), sneaking holds.
- Lava: moves ~0.784 b/s; water/lava slow separately. Cobweb ~0.49 b/s. Soul sand ~58% of walking. Honey ~58%.
- Fall damage: `max(0, ceil(fallDistance - 3))`; water cancels; hay/honey 20%.

## Default controls
W/A/S/D, Space jump (double-tap = creative fly), Left Shift sneak, Left Ctrl sprint, LMB attack, RMB use,
Q drop one, Ctrl+Q drop stack, middle click pick block, 1-9 and wheel hotbar, E inventory, F swap hands,
T chat, F1 hide HUD, F2 screenshot, F3 debug, F5 perspective (first / third back / third front), F11 fullscreen.
F3 left: version, fps, XYZ, block/chunk coords, facing yaw/pitch, light, biome, difficulty; right: java/memory, GPU,
targeted block id/state. F3+G chunk borders, F3+B hitboxes, F3+H advanced tooltips, F3+Q list shortcuts.

## Dropped item entities
- Q toss: `vx = -0.3 sin(Y) cos(P) + l cos(k)`, `vy = 0.1 - 0.3 sin(P) + 0.1 (U1 - U2)`,
  `vz = 0.3 cos(Y) cos(P) + l sin(k)` with `k in [0, 2pi)`, `l in [0, 0.02)`, `U in [0,1)`. Spawned at eye height - 0.3.
- Gravity 0.04/tick^2, air drag 0.98, ground horizontal 0.98 x 0.6.
- Pickup delay: block drops 10 ticks, player-thrown 40 ticks. Pickup box: player hitbox grown 1 block horizontally,
  0.5 vertically. Merge when within 0.5 x 0.25 x 0.5 expanded box (stack limit respected). Despawn 6000 ticks.
