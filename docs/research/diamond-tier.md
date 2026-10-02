# Diamond tier (Perplexity research, Java 1.9+ values)

| Item | Damage | Attack speed | Durability | Notes |
|---|---:|---:|---:|---|
| Diamond sword | 7 | 1.6 | 1561 | recipe `.D./.D./.S.` |
| Diamond axe | 9 | 1.0 | 1561 | recipe `DD./DS./.S.` (mirrored also valid) |
| Diamond pickaxe | 5 | 1.2 | 1561 | recipe `DDD/.S./.S.` |
| Diamond shovel | 5.5 | 1.0 | 1561 | recipe `.D./.S./.S.` |

Armor (defense / toughness 2 for every piece; durability = piece base x 33):

| Piece | Defense | Durability | Recipe |
|---|---:|---:|---|
| Helmet | 3 | 363 | `DDD/D.D` (5) |
| Chestplate | 8 | 528 | `D.D/DDD/DDD` (8) |
| Leggings | 6 | 495 | `DDD/D.D/D.D` (7) |
| Boots | 3 | 429 | `D.D/D.D` (4) |

Full set: 20 defense, 8 toughness. Check: a 10-damage hit takes `effective = max(20/5, 20 - 10/(2+8/4)) = 17.5`,
so 10 x (1 - 17.5/25) = 3.0 damage.

Mining: tier speed 8 (iron 6, stone 4, wood 2), harvest level 3. Progress per tick = speed / (30 x hardness)
for the right tool, so diamond breaks things in 3/4 of iron's time. Diamond ore with an iron-or-better pickaxe
drops 1 diamond (the project already does this).

Project decisions: the damage/speed/durability values go in `WeaponStats`/`Durability`; armor in `ArmorStats`;
mining tier in `ToolEffectiveness`; recipes through the existing `armorRecipe` helper and tool recipes.
Source: Perplexity research citing minecraft.wiki and digminecraft.com recipe pages.
