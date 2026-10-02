# Knockback, hurt/death visuals, mob persistence, attack targeting - research notes

Source: Perplexity deep research (Oct 2026) citing minecraft.wiki (Knockback, Entity_format, Mob_spawning,
Death, Player, Attribute), Fabric/Neoforge `LivingEntity` javadocs.

## Knockback (LivingEntity.knockback)
s = strength, v = victim velocity before the hit, d = normalized HORIZONTAL vector from victim toward attacker.
```
vx' = vx/2 - s*dx
vz' = vz/2 - s*dz
vy' = onGround ? min(0.4, vy/2 + s) : vy
```
- Ordinary damage knockback: s = 0.4. Only the vertical addition needs onGround; airborne victims still get
  horizontal knockback.
- Extra +0.5 strength applies only to a PLAYER's sprinting attack (attacker side). A zombie hitting the player
  uses plain s = 0.4 and pushes the player away from the zombie.
- Knockback resistance (netherite only here) scales s; not needed for this milestone.

## Hurt / death visuals
- hurt flash: 10 ticks = 0.5 s red overlay (already `LivingEntity.hurtTime` 0.3 s in code -> use 0.5 s).
- death: body tips onto its side, ~90 deg eased over 20 ticks (1 s), then removed with particles. Drops and
  experience are spawned at the moment of death, not when the corpse vanishes.
- sounds: hurt/death sound belongs to the entity that is hurt/killed (zombie death on zombie kill; player hurt
  when a zombie hits the player). This project has no zombie/animal sound files, so sounds stay out of scope.

## Persistence (entity NBT)
Per entity: `id`, `Pos[3 doubles]`, `Motion[3 doubles]`, `Rotation[yaw,pitch]`, `Health`, `UUID`, persistence flag,
plus per-type fields (e.g. breedable `Age`, chicken `EggLayTime`). Stored with the chunk (1.17+: separate
`entities/` region files); unloaded mobs make no despawn rolls. For this clone: a single `mobs.dat` per world
with type id, position, motion, yaw, health, and a few type fields (egg timer, hurt/panic not saved).

## Despawning
- Hostile, non-persistent: never within 32 blocks; beyond 32 after 600 ticks inactivity -> 1/800 chance per tick;
  beyond 128 blocks -> immediate. 
- Passive farm animals do not despawn naturally (persist), exceptions are type-specific.

## Attack targeting
- Entity reach 3.0 blocks, block reach 4.5 (survival); keep the CLOSEST hit; a nearer entity wins; a block in front
  occludes the entity; equal distance keeps the block.
- Sword sweep (grounded, >= 84.8% cooldown): secondary targets within hitbox expanded 1 block horizontal / 0.25
  vertical, feet within 3 blocks; 1 HP base sweep damage, 80% knockback.
