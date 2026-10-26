package com.example.worldgen.feature.level;

import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code BlockState.canSurvive(level, pos)} (= {@code block.canSurvive(state, level, pos)}) of the blocks the 26.3 overworld
 * features place or test with {@code would_survive}, ported per block class from the client jar (bytecode of
 * {@code canSurvive} / {@code mayPlaceOn} of each class and its superclasses; the class of each block id is the one
 * {@code Blocks.<clinit>} registers):
 * <ul>
 *     <li>{@code BlockBehaviour.canSurvive}: true (MELON, PUMPKIN, TUFF, SCULK_CATALYST, POTENT_SULFUR: no override);</li>
 *     <li>{@code VegetationBlock.canSurvive}: {@code mayPlaceOn(level.getBlockState(below), level, below)}, default
 *     {@code mayPlaceOn} = {@code is(#supports_vegetation)} (FlowerBlock, EyeblossomBlock, SaplingBlock, BushBlock,
 *     TallGrassBlock, FireflyBushBlock, FlowerBedBlock, SweetBerryBushBlock); overrides: {@code DryVegetationBlock}
 *     {@code #supports_dry_vegetation}, {@code AzaleaBlock} {@code #supports_azalea}, {@code MangrovePropaguleBlock}
 *     {@code #supports_mangrove_propagule}, {@code CactusFlowerBlock} ({@code #support_override_cactus_flower} or
 *     {@code isFaceSturdy(UP, SupportType.CENTER)}), {@code LilyPadBlock}, {@code SeaPickleBlock}, {@code SeagrassBlock},
 *     {@code TallSeagrassBlock}, {@code SmallDripleafBlock} (below);</li>
 *     <li>{@code DoublePlantBlock.canSurvive}: the upper half needs the same block's lower half below; the lower half is
 *     {@code VegetationBlock.canSurvive};</li>
 *     <li>the classes with their own {@code canSurvive}: {@code MangrovePropaguleBlock} (hanging: the block above in
 *     {@code #supports_hanging_mangrove_propagule}), {@code MushroomBlock} ({@code #overrides_mushroom_light_requirement}
 *     below, else a light read, see {@link GenRegion#getRawBrightness}), {@code CactusBlock}, {@code LeafLitterBlock},
 *     {@code CarpetBlock}, {@code MossyCarpetBlock}, {@code SporeBlossomBlock}, {@code SugarCaneBlock},
 *     {@code BambooStalkBlock}, {@code TallSeagrassBlock}, {@code SmallDripleafBlock}, {@code SeaPickleBlock}.</li>
 * </ul>
 * Shape facts come from {@link BlockStateFacts}, block tags from {@link BlockTags}, fluid tags from {@link FluidTags}. A block
 * whose rule is not ported throws.
 */
public final class BlockSurvival {

    /** The ported {@code canSurvive} implementations. */
    enum Rule {
        ALWAYS, VEGETATION, DRY_VEGETATION, AZALEA, MANGROVE_PROPAGULE, MUSHROOM, DOUBLE_PLANT, TALL_SEAGRASS, SMALL_DRIPLEAF,
        CACTUS, CACTUS_FLOWER, LEAF_LITTER, LILY_PAD, CARPET, MOSSY_CARPET, SEA_PICKLE, SEAGRASS, SPORE_BLOSSOM, SUGAR_CANE, BAMBOO,
        CORAL_PLANT, CORAL_WALL_FAN
    }

    static final String SUPPORTS_VEGETATION = "minecraft:supports_vegetation";
    static final String SUPPORTS_DRY_VEGETATION = "minecraft:supports_dry_vegetation";
    static final String SUPPORTS_AZALEA = "minecraft:supports_azalea";
    static final String SUPPORTS_MANGROVE_PROPAGULE = "minecraft:supports_mangrove_propagule";
    static final String SUPPORTS_HANGING_MANGROVE_PROPAGULE = "minecraft:supports_hanging_mangrove_propagule";
    static final String OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT = "minecraft:overrides_mushroom_light_requirement";
    static final String SUPPORTS_CACTUS = "minecraft:supports_cactus";
    static final String SUPPORT_OVERRIDE_CACTUS_FLOWER = "minecraft:support_override_cactus_flower";
    static final String SUPPORTS_LILY_PAD = "minecraft:supports_lily_pad";
    static final String CANNOT_SUPPORT_SEAGRASS = "minecraft:cannot_support_seagrass";
    static final String SUPPORTS_SMALL_DRIPLEAF = "minecraft:supports_small_dripleaf";
    static final String SUPPORTS_SUGAR_CANE = "minecraft:supports_sugar_cane";
    static final String SUPPORTS_SUGAR_CANE_ADJACENTLY = "minecraft:supports_sugar_cane_adjacently";
    static final String SUPPORTS_BAMBOO = "minecraft:supports_bamboo";

    private static final Map<String, Rule> RULES = new HashMap<>();

    private static void rule(Rule rule, String... ids) {
        for (String id : ids) {
            if (RULES.put("minecraft:" + id, rule) != null) throw new IllegalStateException("rule twice: " + id);
        }
    }

    static {
        rule(Rule.ALWAYS, "melon", "pumpkin", "tuff", "sculk_catalyst", "potent_sulfur");
        rule(Rule.VEGETATION, "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "closed_eyeblossom", "oak_sapling",
                "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling",
                "pale_oak_sapling", "poplar_sapling", "bush", "red_shrub", "short_grass", "fern", "firefly_bush", "pink_petals",
                "wildflowers", "sweet_berry_bush");
        rule(Rule.DRY_VEGETATION, "dead_bush", "short_dry_grass", "tall_dry_grass");
        rule(Rule.AZALEA, "azalea", "flowering_azalea");
        rule(Rule.MANGROVE_PROPAGULE, "mangrove_propagule");
        rule(Rule.MUSHROOM, "brown_mushroom", "red_mushroom");
        rule(Rule.DOUBLE_PLANT, "tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony");
        rule(Rule.TALL_SEAGRASS, "tall_seagrass");
        rule(Rule.SMALL_DRIPLEAF, "small_dripleaf");
        rule(Rule.CACTUS, "cactus");
        rule(Rule.CACTUS_FLOWER, "cactus_flower");
        rule(Rule.LEAF_LITTER, "leaf_litter");
        rule(Rule.LILY_PAD, "lily_pad");
        rule(Rule.CARPET, "moss_carpet");
        rule(Rule.MOSSY_CARPET, "pale_moss_carpet");
        rule(Rule.SEA_PICKLE, "sea_pickle");
        rule(Rule.SEAGRASS, "seagrass");
        rule(Rule.SPORE_BLOSSOM, "spore_blossom");
        rule(Rule.SUGAR_CANE, "sugar_cane");
        rule(Rule.BAMBOO, "bamboo");
        for (String c : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {
            // CoralBlock: no canSurvive override; BaseCoralPlantTypeBlock (plants, fans); BaseCoralWallFanBlock
            rule(Rule.ALWAYS, c + "_coral_block");
            rule(Rule.CORAL_PLANT, c + "_coral", c + "_coral_fan");
            rule(Rule.CORAL_WALL_FAN, c + "_coral_wall_fan");
        }
    }

    private final BlockTags tags;
    private final FluidTags fluidTags;

    public BlockSurvival(BlockTags tags, FluidTags fluidTags) {
        this.tags = tags;
        this.fluidTags = fluidTags;
    }

    public BlockSurvival(Path dataRoot, BlockTags tags) {
        this(tags, new FluidTags(dataRoot));
    }

    private static final Map<BlockTags, BlockSurvival> BY_TAGS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** The survival rules over a block tag registry (fluid tags from the same data root); one instance per registry. */
    public static BlockSurvival of(BlockTags tags) {
        return BY_TAGS.computeIfAbsent(tags, t -> new BlockSurvival(t.dataRoot(), t));
    }

    public BlockTags tags() {
        return tags;
    }

    public FluidTags fluidTags() {
        return fluidTags;
    }

    /**
     * {@code state.getBlock() instanceof DoublePlantBlock} for the registered blocks: {@code Blocks.<clinit>} creates
     * DoublePlantBlock (TALL_GRASS, LARGE_FERN), TallFlowerBlock (SUNFLOWER, LILAC, ROSE_BUSH, PEONY), TallSeagrassBlock and
     * SmallDripleafBlock, all subclasses of DoublePlantBlock.
     */
    public static boolean isDoublePlant(String id) {
        return switch (id) {
            case "minecraft:tall_grass", "minecraft:large_fern", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
                    "minecraft:peony", "minecraft:tall_seagrass", "minecraft:small_dripleaf" -> true;
            default -> false;
        };
    }

    /** {@code state.getBlock() instanceof MossyCarpetBlock}: only PALE_MOSS_CARPET is one ({@code Blocks.<clinit>}). */
    public static boolean isMossyCarpet(String id) {
        return id.equals("minecraft:pale_moss_carpet");
    }

    /** Whether the {@code canSurvive} of a block is ported. */
    public static boolean isPorted(String id) {
        return RULES.containsKey(id);
    }

    /** {@code state.canSurvive(level, pos)}. */
    public boolean canSurvive(StateRef state, GenRegion level, Pos pos) {
        Rule rule = RULES.get(state.id());
        if (rule == null) throw new IllegalArgumentException("canSurvive not ported for " + state.id());
        return switch (rule) {
            case ALWAYS -> true;
            case VEGETATION, DRY_VEGETATION, AZALEA, CACTUS_FLOWER, LILY_PAD, SEA_PICKLE, SEAGRASS -> vegetationCanSurvive(rule, level, pos);
            case MANGROVE_PROPAGULE -> {
                if ("true".equals(state.properties().get("hanging"))) {
                    yield tags.is(level.getBlockState(pos.above()), SUPPORTS_HANGING_MANGROVE_PROPAGULE);
                }
                yield vegetationCanSurvive(rule, level, pos);
            }
            case MUSHROOM -> {
                Pos below = pos.below();
                StateRef belowState = level.getBlockState(below);
                if (tags.is(belowState, OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT)) yield true;
                // vanilla continues with level.getRawBrightness(pos, 0) < 13 && mayPlaceOn (isSolidRender); the light read
                // throws UnportedFeature.Reached (not modelled), so the rest is never reached
                level.getRawBrightness(pos, 0);
                throw new IllegalStateException("unreachable: getRawBrightness throws");
            }
            case DOUBLE_PLANT, SMALL_DRIPLEAF -> {
                if (isUpper(state)) yield upperHalfSupported(state, level, pos);
                yield vegetationCanSurvive(rule, level, pos);
            }
            case TALL_SEAGRASS -> {
                if (isUpper(state)) yield upperHalfSupported(state, level, pos);
                StateRef here = level.getBlockState(pos);
                yield vegetationCanSurvive(rule, level, pos) && fluidTags.is(BlockStateFacts.fluidType(here), FluidTags.WATER)
                        && BlockStateFacts.fluidAmount(here) == 8;
            }
            case CACTUS -> {
                for (Direction d : Direction.HORIZONTAL) {
                    StateRef side = level.getBlockState(pos.relative(d));
                    if (BlockStateFacts.isSolid(side) || fluidTags.is(BlockStateFacts.fluidType(side), FluidTags.LAVA)) {
                        yield false;
                    }
                }
                StateRef below = level.getBlockState(pos.below());
                yield (below.id().equals(state.id()) || tags.is(below, SUPPORTS_CACTUS))
                        && !BlockStateFacts.liquid(level.getBlockState(pos.above()));
            }
            case LEAF_LITTER -> BlockStateFacts.isFaceSturdy(level.getBlockState(pos.below()), Direction.UP);
            case CARPET -> !BlockProperties.isAir(level.getBlockState(pos.below()));
            case MOSSY_CARPET -> {
                StateRef below = level.getBlockState(pos.below());
                if ("true".equals(state.properties().get("bottom"))) yield !BlockProperties.isAir(below);
                yield below.id().equals(state.id()) && "true".equals(below.properties().get("bottom"));
            }
            case SPORE_BLOSSOM -> BlockStateFacts.isFaceSturdyCenter(level.getBlockState(pos.above()), Direction.DOWN)
                    && !isWaterAt(level, pos);
            case SUGAR_CANE -> {
                StateRef below = level.getBlockState(pos.below());
                if (below.id().equals(state.id())) yield true;
                if (tags.is(below, SUPPORTS_SUGAR_CANE)) {
                    Pos belowPos = pos.below();
                    for (Direction d : Direction.HORIZONTAL) {
                        StateRef side = level.getBlockState(belowPos.relative(d));
                        String fluid = BlockStateFacts.fluidType(side);
                        if (fluidTags.is(fluid, FluidTags.SUPPORTS_SUGAR_CANE_ADJACENTLY) || tags.is(side, SUPPORTS_SUGAR_CANE_ADJACENTLY)) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case BAMBOO -> tags.is(level.getBlockState(pos.below()), SUPPORTS_BAMBOO);
            // BaseCoralPlantTypeBlock: the block below has a sturdy UP face
            case CORAL_PLANT -> BlockStateFacts.isFaceSturdy(level.getBlockState(pos.below()), Direction.UP);
            // BaseCoralWallFanBlock: the block at pos.relative(facing.getOpposite()) is sturdy on its facing side
            case CORAL_WALL_FAN -> {
                Direction facing = Direction.parse(state.properties().get("facing"));
                yield BlockStateFacts.isFaceSturdy(level.getBlockState(pos.relative(facing.opposite())), facing);
            }
        };
    }

    private static boolean isUpper(StateRef state) {
        return "upper".equals(state.properties().get("half"));
    }

    /** {@code DoublePlantBlock.canSurvive} for the upper half: the block below is this block with {@code HALF = LOWER}. */
    private static boolean upperHalfSupported(StateRef state, GenRegion level, Pos pos) {
        StateRef below = level.getBlockState(pos.below());
        return below.id().equals(state.id()) && "lower".equals(below.properties().get("half"));
    }

    /** {@code LevelReader.isWaterAt(pos)} = {@code getFluidState(pos).is(FluidTags.WATER)}. */
    public boolean isWaterAt(GenRegion level, Pos pos) {
        return fluidTags.is(BlockStateFacts.fluidType(level.getBlockState(pos)), FluidTags.WATER);
    }

    /** {@code VegetationBlock.canSurvive}: {@code mayPlaceOn(level.getBlockState(below), level, below)} of the block's class. */
    private boolean vegetationCanSurvive(Rule rule, GenRegion level, Pos pos) {
        Pos below = pos.below();
        return mayPlaceOn(rule, level.getBlockState(below), level, below);
    }

    private boolean mayPlaceOn(Rule rule, StateRef state, GenRegion level, Pos pos) {
        return switch (rule) {
            case VEGETATION, DOUBLE_PLANT -> tags.is(state, SUPPORTS_VEGETATION);
            case DRY_VEGETATION -> tags.is(state, SUPPORTS_DRY_VEGETATION);
            case AZALEA -> tags.is(state, SUPPORTS_AZALEA);
            case MANGROVE_PROPAGULE -> tags.is(state, SUPPORTS_MANGROVE_PROPAGULE);
            // CactusFlowerBlock: level.getBlockState(pos) (the support) in #support_override_cactus_flower, or its UP face
            // sturdy for SupportType.CENTER
            case CACTUS_FLOWER -> {
                StateRef support = level.getBlockState(pos);
                yield tags.is(support, SUPPORT_OVERRIDE_CACTUS_FLOWER) || BlockStateFacts.isFaceSturdyCenter(support, Direction.UP);
            }
            // LilyPadBlock: (fluid at pos in fluid #supports_lily_pad or state in block #supports_lily_pad) and the fluid
            // above pos is Fluids.EMPTY
            case LILY_PAD -> (fluidTags.is(BlockStateFacts.fluidType(level.getBlockState(pos)), FluidTags.SUPPORTS_LILY_PAD)
                    || tags.is(state, SUPPORTS_LILY_PAD))
                    && BlockStateFacts.fluidType(level.getBlockState(pos.above())).equals("minecraft:empty");
            // SeaPickleBlock: the collision shape's UP face is not empty, or the UP face is sturdy
            case SEA_PICKLE -> !BlockStateFacts.collisionUpFaceEmpty(state) || BlockStateFacts.isFaceSturdy(state, Direction.UP);
            // SeagrassBlock / TallSeagrassBlock: UP face sturdy and not #cannot_support_seagrass
            case SEAGRASS, TALL_SEAGRASS -> BlockStateFacts.isFaceSturdy(state, Direction.UP) && !tags.is(state, CANNOT_SUPPORT_SEAGRASS);
            // SmallDripleafBlock: #supports_small_dripleaf, or a water source above pos and DoublePlantBlock's (vegetation)
            // mayPlaceOn
            case SMALL_DRIPLEAF -> tags.is(state, SUPPORTS_SMALL_DRIPLEAF)
                    || BlockStateFacts.fluidType(level.getBlockState(pos.above())).equals("minecraft:water")
                    && tags.is(state, SUPPORTS_VEGETATION);
            default -> throw new IllegalStateException("no mayPlaceOn for " + rule);
        };
    }
}
