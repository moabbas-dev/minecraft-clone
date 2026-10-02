# Block breaking, drops and block mechanics (Perplexity research, Java 1.20+)

## Break time
speed = tool tier speed if the tool type is correct (hand 1, wood 2, stone 4, iron 6, diamond 8, netherite 9, gold 12),
/5 if head underwater, /5 if not on ground. Progress per tick = `speed / hardness / 30` if the held item can harvest
the block, else `/ 100`. Hardness 0 breaks instantly; otherwise ticks = `ceil(1 / progress)`. 6-tick delay between
non-instant breaks.

## Harvest tiers
Stone/coal ore: any pickaxe; iron ore/block: stone+; gold ore/block, diamond ore/block, redstone: iron+; obsidian: diamond.
Too-low tier: no drop (but still breaks, slowly).

## Hardness (and drops)
stone 1.5 -> cobblestone; cobblestone 2; dirt 0.5; grass block 0.6 -> dirt; sand 0.5; gravel 0.6 -> 10% flint else gravel;
log 2; planks 2; leaves 0.2 -> see below; glass 0.3 -> nothing; obsidian 50; iron/gold/diamond/coal ore 3
(raw iron / raw gold / diamond / coal); furnace 3.5; crafting table 2.5; chest 2.5; bookshelf 1.5 -> 3 books;
TNT 0; ice 0.5 -> nothing (water source if solid/liquid beneath); snow block 0.2 -> 4 snowballs with a shovel;
clay 0.6 -> 4 clay balls; netherrack 0.4; glowstone 0.3 -> 2-4 glowstone dust; bricks 2; sandstone 0.8;
terracotta 1.25; mud 0.5; pumpkin 1; iron block 5; gold block 3; diamond block 5.

Oak leaves: independent rolls - sapling 5%, 1-2 sticks 2%, apple 0.5%. Dark oak: apple 0.5%. Jungle sapling 2.5%.
Natural leaves decay when their distance to a log reaches 7 (player-placed leaves are persistent).

## Falling blocks
Sand/gravel lose support -> falling entity -> place again on landing (or drop as an item if it cannot). No impact damage.

## Fluids
Water: source level 0, flowing 1-7, spreads 7 blocks on flat ground (~1 block / 5 ticks), flows down indefinitely;
two adjacent horizontal sources with solid/source beneath create a new source. Lava (overworld): 3 blocks, 1 / 30 ticks,
damage 4 per hit (0.5 s immunity) + fire. Water on lava source -> obsidian, on flowing lava -> cobblestone; lava flowing
into water -> stone.

## Other
TNT: primed by fire/redstone, 80 tick fuse, power 4; chain-primed by explosion 10-30 ticks. Ice melts at block light >= 12.
Snow layers 1-8, shovel gives 1 snowball per layer. Torches light 14. Farmland/crops/doors/beds/ladders/slabs/stairs/fences
are out of scope for this milestone (no such blocks exist).
