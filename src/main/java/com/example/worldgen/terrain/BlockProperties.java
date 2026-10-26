package com.example.worldgen.terrain;

import com.example.worldgen.level.StateRef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The block-state facts the worldgen stages ask vanilla's {@code BlockState} for, per block id, with values read from
 * the 26.3 client jar:
 * <ul>
 *     <li>{@code isAir()}: {@code BlockStateBase.isAir} is copied from {@code BlockBehaviour.Properties.isAir}, which
 *     only {@code Properties.air()} sets; in {@code Blocks.<clinit>} that is called for {@code AIR}, {@code VOID_AIR}
 *     and {@code CAVE_AIR} only;</li>
 *     <li>whether {@code getFluidState()} is non-empty: {@code BlockStateBase.fluidState = owner.getFluidState(state)};
 *     the {@code BlockBehaviour} default is {@code Fluids.EMPTY}; {@code LiquidBlock.getFluidState} returns
 *     {@code stateCache.get(min(level, 8))}, a source or flowing fluid for every level (so never empty);</li>
 *     <li>the default state's properties ({@code registerDefaultState}); {@code LiquidBlock}: {@code LEVEL = 0}.</li>
 * </ul>
 * Tag membership (heightmap predicates, {@code uncarvable}, ...) is data, see {@link BlockTags}.
 *
 * <p>Every query for a block that is not registered here throws: later stages add their blocks with one
 * {@link #register} line each, naming the jar class/method the values come from. Nothing is assumed for unknown
 * blocks.
 */
public final class BlockProperties {

    /** Fluid of a block's states, as far as the stages need it. */
    public enum Fluid {
        /** {@code getFluidState()} is {@code Fluids.EMPTY} for every state (the {@code BlockBehaviour} default). */
        NONE,
        /** {@code LiquidBlock(Fluids.WATER, ...)}: never empty. */
        WATER,
        /** {@code LiquidBlock(Fluids.LAVA, ...)}: never empty. */
        LAVA,
        /**
         * {@code getFluidState} returns {@code Fluids.WATER.getSource(false)} when {@code WATERLOGGED} is true, else
         * {@code Fluids.EMPTY} (the {@code SimpleWaterloggedBlock} pattern of the class's {@code getFluidState} override).
         */
        WATERLOGGED
    }

    /** One registered block: air flag, fluid, the properties of its default state. */
    public record Entry(String id, boolean air, Fluid fluid, Map<String, String> defaultProperties, StateRef defaultState) {
    }

    private static final Map<String, Entry> BLOCKS = new LinkedHashMap<>();

    // Blocks.<clinit>: AIR, VOID_AIR, CAVE_AIR = register(..., AirBlock::new, Properties.of()...air())
    public static final StateRef AIR = register("minecraft:air", true, Fluid.NONE);
    public static final StateRef VOID_AIR = register("minecraft:void_air", true, Fluid.NONE);
    public static final StateRef CAVE_AIR = register("minecraft:cave_air", true, Fluid.NONE);
    // Blocks.<clinit>: STONE = register(BlockItemIds.STONE, Properties.of()...) (plain Block, no properties)
    public static final StateRef STONE = register("minecraft:stone", false, Fluid.NONE);
    // Blocks.<clinit>: NETHERRACK = register(..., NetherrackBlock::new, ...) (no state properties; default_block of nether)
    public static final StateRef NETHERRACK = register("minecraft:netherrack", false, Fluid.NONE);
    // Blocks.<clinit>: END_STONE = register(BlockItemIds.END_STONE, Properties.of()...) (default_block of end)
    public static final StateRef END_STONE = register("minecraft:end_stone", false, Fluid.NONE);
    // Blocks.<clinit>: WATER/LAVA = new LiquidBlock(Fluids.WATER/LAVA, ...); LiquidBlock registers LEVEL = 0 by default
    public static final StateRef WATER = register("minecraft:water", false, Fluid.WATER, "level", "0");
    public static final StateRef LAVA = register("minecraft:lava", false, Fluid.LAVA, "level", "0");

    // The blocks the overworld material rules (data/minecraft/worldgen/material_rule/**: result_state, ore_block,
    // raw_ore_block, filler_block) and MaterialSystem's own constants (terracotta bands, packed_ice, snow_block) write.
    // None holds a fluid (no LiquidBlock, no waterlogged property). Blocks.<clinit>: plain Block / ColoredFallingBlock /
    // IceBlock / MudBlock / PowderSnowBlock without state properties, except: GRASS_BLOCK (GrassBlock), MYCELIUM
    // (MyceliumBlock) and PODZOL (SnowyBlock) carry SnowyBlock.SNOWY, default false; DEEPSLATE is a RotatedPillarBlock,
    // AXIS default y.
    public static final StateRef GRASS_BLOCK = register("minecraft:grass_block", false, Fluid.NONE, "snowy", "false");
    public static final StateRef MYCELIUM = register("minecraft:mycelium", false, Fluid.NONE, "snowy", "false");
    public static final StateRef PODZOL = register("minecraft:podzol", false, Fluid.NONE, "snowy", "false");
    public static final StateRef DEEPSLATE = register("minecraft:deepslate", false, Fluid.NONE, "axis", "y");
    public static final StateRef DIRT = register("minecraft:dirt", false, Fluid.NONE);

    static {
        for (String id : new String[]{"bedrock", "calcite", "cinnabar", "coarse_dirt", "copper_ore", "deepslate_iron_ore",
                "granite", "gravel", "ice", "mud", "packed_ice", "powder_snow", "raw_copper_block", "raw_iron_block", "red_sand",
                "red_sandstone", "sand", "sandstone", "snow_block", "sulfur", "tuff", "terracotta", "white_terracotta",
                "orange_terracotta", "yellow_terracotta", "brown_terracotta", "red_terracotta", "light_gray_terracotta"}) {
            register("minecraft:" + id, false, Fluid.NONE);
        }
    }

    // Blocks the ported features (worldgen/feature: ore, disk, spring_feature, underwater_magma) write and that are not
    // registered above. Blocks.<clinit> (javap): COAL/IRON/GOLD/LAPIS/DIAMOND/EMERALD_ORE, their DEEPSLATE_ variants and
    // DEEPSLATE_COPPER_ORE are lambdas p -> new DropExperienceBlock(...) (no state properties); CLAY, ANDESITE, DIORITE
    // are plain Block; INFESTED_STONE is InfestedBlock (no properties); MAGMA_BLOCK is MagmaBlock (no properties);
    // REDSTONE_ORE and DEEPSLATE_REDSTONE_ORE are RedStoneOreBlock (constructor: LIT default false);
    // INFESTED_DEEPSLATE is InfestedRotatedPillarBlock (constructor: RotatedPillarBlock.AXIS default y).
    static {
        for (String id : new String[]{"coal_ore", "deepslate_coal_ore", "iron_ore", "gold_ore", "deepslate_gold_ore",
                "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
                "deepslate_copper_ore", "clay", "andesite", "diorite", "infested_stone", "magma_block"}) {
            register("minecraft:" + id, false, Fluid.NONE);
        }
        register("minecraft:redstone_ore", false, Fluid.NONE, "lit", "false");
        register("minecraft:deepslate_redstone_ore", false, Fluid.NONE, "lit", "false");
        register("minecraft:infested_deepslate", false, Fluid.NONE, "axis", "y");
    }

    // Blocks the vegetation features (worldgen/feature: simple_block, block_column, bamboo, vines and the selectors) write.
    // Default states from each class's constructor registerDefaultState chain and createBlockStateDefinition (javap);
    // fluids from the getFluidState overrides. Classes per Blocks.<clinit>:
    // FlowerBlock / EyeblossomBlock / MushroomBlock / BushBlock / DryVegetationBlock / TallGrassBlock / ShortDryGrassBlock /
    // TallDryGrassBlock / FireflyBushBlock / AzaleaBlock / CactusFlowerBlock / SporeBlossomBlock / LilyPadBlock /
    // CarpetBlock (MOSS_CARPET) / plain Block (MELON) / PumpkinBlock: no state properties, no fluid.
    static {
        for (String id : new String[]{"dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "closed_eyeblossom", "brown_mushroom",
                "red_mushroom", "bush", "red_shrub", "dead_bush", "short_grass", "fern", "short_dry_grass", "tall_dry_grass",
                "firefly_bush", "azalea", "flowering_azalea", "cactus_flower", "spore_blossom", "lily_pad", "moss_carpet", "melon",
                "pumpkin"}) {
            register("minecraft:" + id, false, Fluid.NONE);
        }
        // SaplingBlock (TreeGrower lambdas): STAGE = 0
        for (String id : new String[]{"oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling",
                "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "poplar_sapling"}) {
            register("minecraft:" + id, false, Fluid.NONE, "stage", "0");
        }
        // DoublePlantBlock (TALL_GRASS, LARGE_FERN) and TallFlowerBlock: HALF = LOWER
        for (String id : new String[]{"tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony"}) {
            register("minecraft:" + id, false, Fluid.NONE, "half", "lower");
        }
        // FlowerBedBlock (PINK_PETALS, WILDFLOWERS): FACING = NORTH, AMOUNT = 1
        for (String id : new String[]{"pink_petals", "wildflowers"}) {
            register("minecraft:" + id, false, Fluid.NONE, "facing", "north", "flower_amount", "1");
        }
    }
    // Coral (the warm-ocean overlay features coral/*_block): CoralBlock (no properties, no fluid); CoralPlantBlock and
    // CoralFanBlock (BaseCoralPlantTypeBlock: WATERLOGGED true; getFluidState water source if waterlogged); CoralWallFanBlock
    // (BaseCoralWallFanBlock: FACING (HorizontalDirectionalBlock.FACING) NORTH, WATERLOGGED true).
    static {
        for (String c : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {
            register("minecraft:" + c + "_coral_block", false, Fluid.NONE);
            register("minecraft:" + c + "_coral", false, Fluid.WATERLOGGED, "waterlogged", "true");
            register("minecraft:" + c + "_coral_fan", false, Fluid.WATERLOGGED, "waterlogged", "true");
            register("minecraft:" + c + "_coral_wall_fan", false, Fluid.WATERLOGGED, "facing", "north", "waterlogged", "true");
        }
    }
    // MangrovePropaguleBlock: STAGE 0, AGE 0, WATERLOGGED false, HANGING false; getFluidState: water source if waterlogged
    public static final StateRef MANGROVE_PROPAGULE = register("minecraft:mangrove_propagule", false, Fluid.WATERLOGGED, "stage", "0", "age", "0", "waterlogged", "false", "hanging", "false");
    // TallSeagrassBlock (DoublePlantBlock): HALF = LOWER; getFluidState: Fluids.WATER.getSource(false)
    public static final StateRef TALL_SEAGRASS = register("minecraft:tall_seagrass", false, Fluid.WATER, "half", "lower");
    // SeagrassBlock: no properties; getFluidState: Fluids.WATER.getSource(false)
    public static final StateRef SEAGRASS = register("minecraft:seagrass", false, Fluid.WATER);
    // SmallDripleafBlock: HALF LOWER, WATERLOGGED false, FACING NORTH; getFluidState: water source if waterlogged
    public static final StateRef SMALL_DRIPLEAF = register("minecraft:small_dripleaf", false, Fluid.WATERLOGGED, "half", "lower", "waterlogged", "false", "facing", "north");
    // CactusBlock: AGE (AGE_15) = 0
    public static final StateRef CACTUS = register("minecraft:cactus", false, Fluid.NONE, "age", "0");
    // SugarCaneBlock: AGE (AGE_15) = 0
    public static final StateRef SUGAR_CANE = register("minecraft:sugar_cane", false, Fluid.NONE, "age", "0");
    // SweetBerryBushBlock: AGE (AGE_3) = 0
    public static final StateRef SWEET_BERRY_BUSH = register("minecraft:sweet_berry_bush", false, Fluid.NONE, "age", "0");
    // LeafLitterBlock: FACING NORTH, getSegmentAmountProperty() (SEGMENT_AMOUNT) = 1
    public static final StateRef LEAF_LITTER = register("minecraft:leaf_litter", false, Fluid.NONE, "facing", "north", "segment_amount", "1");
    // SeaPickleBlock: PICKLES 1, WATERLOGGED true; getFluidState: water source if waterlogged
    public static final StateRef SEA_PICKLE = register("minecraft:sea_pickle", false, Fluid.WATERLOGGED, "pickles", "1", "waterlogged", "true");
    // KelpBlock (GrowingPlantHeadBlock: AGE (AGE_25) = 0), KelpPlantBlock: getFluidState Fluids.WATER.getSource(false)
    public static final StateRef KELP = register("minecraft:kelp", false, Fluid.WATER, "age", "0");
    public static final StateRef KELP_PLANT = register("minecraft:kelp_plant", false, Fluid.WATER);
    // CaveVinesBlock: AGE 0, BERRIES false; CaveVinesPlantBlock: BERRIES false
    public static final StateRef CAVE_VINES = register("minecraft:cave_vines", false, Fluid.NONE, "age", "0", "berries", "false");
    public static final StateRef CAVE_VINES_PLANT = register("minecraft:cave_vines_plant", false, Fluid.NONE, "berries", "false");
    // BigDripleafBlock: WATERLOGGED false, FACING NORTH, TILT NONE; BigDripleafStemBlock: WATERLOGGED false, FACING NORTH
    public static final StateRef BIG_DRIPLEAF = register("minecraft:big_dripleaf", false, Fluid.WATERLOGGED, "waterlogged", "false", "facing", "north", "tilt", "none");
    public static final StateRef BIG_DRIPLEAF_STEM = register("minecraft:big_dripleaf_stem", false, Fluid.WATERLOGGED, "waterlogged", "false", "facing", "north");
    // BambooStalkBlock: AGE (AGE_1) 0, LEAVES NONE, STAGE 0
    public static final StateRef BAMBOO = register("minecraft:bamboo", false, Fluid.NONE, "age", "0", "leaves", "none", "stage", "0");
    // VineBlock: UP, NORTH, EAST, SOUTH, WEST = false
    public static final StateRef VINE = register("minecraft:vine", false, Fluid.NONE, "up", "false", "north", "false", "east", "false", "south", "false", "west", "false");
    // MossyCarpetBlock: BASE (BOTTOM) true, NORTH/EAST/SOUTH/WEST (WallSide) NONE
    public static final StateRef PALE_MOSS_CARPET = register("minecraft:pale_moss_carpet", false, Fluid.NONE, "bottom", "true", "north", "none", "east", "none", "south", "none", "west", "none");
    // SculkCatalystBlock: PULSE (BLOOM) false
    public static final StateRef SCULK_CATALYST = register("minecraft:sculk_catalyst", false, Fluid.NONE, "bloom", "false");
    // PotentSulfurBlock: STATE (POTENT_SULFUR_STATE) DRY
    public static final StateRef POTENT_SULFUR = register("minecraft:potent_sulfur", false, Fluid.NONE, "potent_sulfur_state", "dry");

    private BlockProperties() {
    }

    /**
     * Registers a block. {@code defaultKeyValues} are the default state's properties as name, value pairs.
     *
     * @return the default state
     */
    public static synchronized StateRef register(String id, boolean air, Fluid fluid, String... defaultKeyValues) {
        if (BLOCKS.containsKey(id)) throw new IllegalStateException("block registered twice: " + id);
        StateRef def = StateRef.of(id, defaultKeyValues);
        BLOCKS.put(id, new Entry(id, air, fluid, def.properties(), def));
        return def;
    }

    public static Entry entry(String id) {
        Entry e = BLOCKS.get(id);
        if (e == null) throw new IllegalArgumentException("block not registered in BlockProperties (values unverified): " + id);
        return e;
    }

    public static boolean isRegistered(String id) {
        return BLOCKS.containsKey(id);
    }

    public static Map<String, Entry> all() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    /** {@code Block.defaultBlockState()}. */
    public static StateRef defaultState(String id) {
        return entry(id).defaultState();
    }

    /** {@code BlockStateBase.isAir()}. */
    public static boolean isAir(StateRef state) {
        return entry(state.id()).air();
    }

    /** {@code !state.getFluidState().isEmpty()}. */
    public static boolean hasFluid(StateRef state) {
        Fluid f = entry(state.id()).fluid();
        if (f == Fluid.WATERLOGGED) return "true".equals(state.properties().get("waterlogged"));
        return f != Fluid.NONE;
    }

    /** {@code state.is(block)}. */
    public static boolean is(StateRef state, StateRef blockDefault) {
        return state.id().equals(blockDefault.id());
    }

    /**
     * Decodes a block state as the data files write it (the shape {@code NoiseGeneratorSettings} keeps raw): a string
     * {@code "namespace:id"} gives the default state; an object {@code {"Name": ..., "Properties": {...}}} gives the
     * default state with the listed properties replaced (vanilla {@code BlockState.CODEC}; unknown property names are
     * rejected).
     */
    @SuppressWarnings("unchecked")
    public static StateRef decodeState(Object json) {
        if (json instanceof String s) {
            return defaultState(normalize(s));
        }
        if (json instanceof Map<?, ?> m) {
            Entry e = entry(normalize((String) m.get("Name")));
            Map<String, String> props = new TreeMap<>(e.defaultProperties());
            Object p = m.get("Properties");
            if (p instanceof Map<?, ?> pm) {
                for (Map.Entry<String, Object> kv : ((Map<String, Object>) pm).entrySet()) {
                    if (!props.containsKey(kv.getKey())) {
                        throw new IllegalArgumentException("unknown property " + kv.getKey() + " of " + e.id());
                    }
                    props.put(kv.getKey(), String.valueOf(kv.getValue()));
                }
            }
            return new StateRef(e.id(), props);
        }
        throw new IllegalArgumentException("not a block state: " + json);
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
