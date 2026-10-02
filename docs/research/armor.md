# Armor mechanics - research notes

Source: Perplexity deep research (Oct 2026) citing minecraft.wiki (Armor, Durability, Armor_materials,
Smithing), minecraft.fandom Armor/Anvil mechanics, and the 1.21.4 `CombatRules` javadoc.

## Per-piece stats (defense points / toughness / max durability)
| Material | Helmet | Chestplate | Leggings | Boots | Toughness |
|---|---|---|---|---|---|
| Leather | 1 / 55 | 3 / 80 | 2 / 75 | 1 / 65 | 0 |
| Chainmail | 2 / 165 | 5 / 240 | 4 / 225 | 1 / 195 | 0 |
| Iron | 2 / 165 | 6 / 240 | 5 / 225 | 2 / 195 | 0 |
| Gold | 2 / 77 | 5 / 112 | 3 / 105 | 1 / 91 | 0 |
| Diamond | 3 / 363 | 8 / 528 | 6 / 495 | 3 / 429 | 2 per piece |
| Netherite | 3 / 407 | 8 / 592 | 6 / 555 | 3 / 481 | 3 per piece (+0.1 knockback resistance each) |

Durability = piece constant (helmet 11, chest 16, legs 15, boots 13) x material multiplier
(leather 5, chain 15, iron 15, gold 7, diamond 33, netherite 37). Totals are additive: full iron = 15
points, full diamond = 20 points + 8 toughness.

## Damage reduction (Java)
```
effective = min(20, max(A / 5, A - D / (2 + T / 4)))
taken     = D * (1 - effective / 25)
```
A = total defense points, T = total toughness, D = incoming damage. Armor alone removes at most 80%.
Worked examples: full iron vs 3.0 -> effective 13.5 -> 54% -> 1.38 HP; full iron vs 10 -> 6 HP;
full diamond vs 10 -> 3 HP.

Armor applies to: melee/mob hits, projectiles, explosions, lightning, initial fire/lava contact.
Armor does NOT apply to: fall, starvation, drowning, void, magic, suffocation, burning ticks.

## Durability loss
Each worn piece loses `max(1, floor(D / 4))` using the INCOMING damage D (not the reduced amount),
only for armor-reducible hits. A piece at 0 durability breaks (removed).

## HUD
Armor bar sits above the hearts on the left (only shown when armor > 0), 10 icons of 9x9, each icon =
2 points (empty / half / full). Sprites: `gui/sprites/hud/armor_empty|armor_half|armor_full.png`.

## Recipes (X = leather / gold ingot / iron ingot / diamond; chainmail has no recipe)
| Piece | Grid | Units |
|---|---|---|
| Helmet | `XXX / X.X / ...` | 5 |
| Chestplate | `X.X / XXX / XXX` | 8 |
| Leggings | `XXX / X.X / X.X` | 7 |
| Boots | `... / X.X / X.X` | 4 |

Repair (anvil) and netherite smithing are out of scope for this clone.

## Equipping
Slots: head, chest, legs, feet. Right-click a piece held in the hand equips it (swapping with any worn
piece); shift-click in the inventory also equips.
