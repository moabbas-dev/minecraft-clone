package com.example.worldgen.feature;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The state definitions ({@code createBlockStateDefinition}, in the order the properties are added, with each property's
 * possible values from {@code BlockStateProperties.<clinit>}) of the blocks the vegetation features write, read from the
 * 26.3 jar. They give {@code BlockState.setValue} / {@code trySetValue} / {@code hasProperty} / {@code getProperties}
 * semantics over {@link StateRef}: a property is identified as vanilla's singleton {@code Property} objects are, by
 * name, kind and value list (no two distinct vanilla properties used here share all three); {@code setValue} with a value
 * outside the property's values throws {@code IllegalArgumentException}, as {@code StateHolder.setValueInternal} does.
 * The default states are registered in {@link BlockProperties} (one line per block).
 */
public final class BlockStateDefinitions {

    public enum Kind {
        INTEGER, BOOLEAN, ENUM
    }

    /** A {@code Property}: name, kind, possible values (serialized names, in {@code getPossibleValues()} order). */
    public record Property(String name, Kind kind, List<String> values) {
    }

    private static Property integer(String name, int min, int max) {
        List<String> v = new ArrayList<>();
        for (int i = min; i <= max; i++) v.add(Integer.toString(i));
        return new Property(name, Kind.INTEGER, List.copyOf(v));
    }

    private static Property bool(String name) {
        return new Property(name, Kind.BOOLEAN, List.of("true", "false"));
    }

    private static Property enumeration(String name, String... values) {
        return new Property(name, Kind.ENUM, List.of(values));
    }

    // BlockStateProperties (26.3 <clinit>)
    public static final Property STAGE = integer("stage", 0, 1);
    public static final Property AGE_1 = integer("age", 0, 1);
    public static final Property AGE_3 = integer("age", 0, 3);
    public static final Property AGE_4 = integer("age", 0, 4);
    public static final Property AGE_15 = integer("age", 0, 15);
    public static final Property AGE_25 = integer("age", 0, 25);
    public static final Property PICKLES = integer("pickles", 1, 4);
    public static final Property FLOWER_AMOUNT = integer("flower_amount", 1, 4);
    public static final Property SEGMENT_AMOUNT = integer("segment_amount", 1, 4);
    public static final Property WATERLOGGED = bool("waterlogged");
    public static final Property HANGING = bool("hanging");
    public static final Property BERRIES = bool("berries");
    public static final Property BLOOM = bool("bloom");
    public static final Property BOTTOM = bool("bottom");
    /** {@code PipeBlock.UP/NORTH/EAST/SOUTH/WEST} ({@code BooleanProperty.create} of the direction names). */
    public static final Property UP = bool("up");
    public static final Property NORTH = bool("north");
    public static final Property EAST = bool("east");
    public static final Property SOUTH = bool("south");
    public static final Property WEST = bool("west");
    /** {@code DOUBLE_BLOCK_HALF}: {@code DoubleBlockHalf} = {@code UPPER, LOWER}. */
    public static final Property HALF = enumeration("half", "upper", "lower");
    /**
     * {@code HORIZONTAL_FACING} = {@code EnumProperty.create("facing", Direction.class, Direction.Plane.HORIZONTAL)}: the
     * {@code Direction} constants the plane predicate accepts, in declaration order ({@code north, south, west, east}).
     */
    public static final Property HORIZONTAL_FACING = enumeration("facing", "north", "south", "west", "east");
    /** {@code FACING}: every direction, {@code Direction.values()} order. */
    public static final Property FACING = enumeration("facing", "down", "up", "north", "south", "west", "east");
    /** {@code AXIS}: {@code Direction$Axis} = {@code x, y, z}. */
    public static final Property AXIS = enumeration("axis", "x", "y", "z");
    /** {@code TILT}: {@code Tilt} = {@code NONE, UNSTABLE, PARTIAL, FULL}. */
    public static final Property TILT = enumeration("tilt", "none", "unstable", "partial", "full");
    /** {@code BAMBOO_LEAVES}: {@code BambooLeaves} = {@code NONE, SMALL, LARGE}. */
    public static final Property BAMBOO_LEAVES = enumeration("leaves", "none", "small", "large");
    /** {@code POTENT_SULFUR_STATE}: {@code PotentSulfurState} = {@code DRY, WET, DORMANT, ERUPTING, CONTINUOUS}. */
    public static final Property POTENT_SULFUR_STATE = enumeration("potent_sulfur_state", "dry", "wet", "dormant", "erupting", "continuous");
    /** {@code NORTH_WALL} etc.: {@code WallSide} = {@code NONE, LOW, TALL}. */
    public static final Property NORTH_WALL = enumeration("north", "none", "low", "tall");
    public static final Property EAST_WALL = enumeration("east", "none", "low", "tall");
    public static final Property SOUTH_WALL = enumeration("south", "none", "low", "tall");
    public static final Property WEST_WALL = enumeration("west", "none", "low", "tall");

    private static final Map<String, List<Property>> DEFINITIONS = new HashMap<>();

    private static void define(Property[] props, String... ids) {
        for (String id : ids) DEFINITIONS.put("minecraft:" + id, List.of(props));
    }

    static {
        Property[] none = {};
        define(none, "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
                "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "closed_eyeblossom", "brown_mushroom",
                "red_mushroom", "bush", "red_shrub", "dead_bush", "short_grass", "fern", "short_dry_grass", "tall_dry_grass",
                "firefly_bush", "azalea", "flowering_azalea", "cactus_flower", "spore_blossom", "lily_pad", "melon", "pumpkin",
                "moss_carpet", "seagrass", "kelp_plant", "tuff");
        // SaplingBlock: STAGE
        define(new Property[]{STAGE}, "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling",
                "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "poplar_sapling");
        // MangrovePropaguleBlock: STAGE, AGE_4, WATERLOGGED, HANGING
        define(new Property[]{STAGE, AGE_4, WATERLOGGED, HANGING}, "mangrove_propagule");
        // DoublePlantBlock / TallFlowerBlock / TallSeagrassBlock: HALF
        define(new Property[]{HALF}, "tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony", "tall_seagrass");
        // SmallDripleafBlock: HALF, WATERLOGGED, FACING (HORIZONTAL_FACING)
        define(new Property[]{HALF, WATERLOGGED, HORIZONTAL_FACING}, "small_dripleaf");
        // CactusBlock, SugarCaneBlock: AGE_15; SweetBerryBushBlock: AGE_3
        define(new Property[]{AGE_15}, "cactus", "sugar_cane");
        define(new Property[]{AGE_3}, "sweet_berry_bush");
        // FlowerBedBlock: FACING (HORIZONTAL_FACING), AMOUNT (FLOWER_AMOUNT)
        define(new Property[]{HORIZONTAL_FACING, FLOWER_AMOUNT}, "pink_petals", "wildflowers");
        // LeafLitterBlock: FACING, getSegmentAmountProperty() (SEGMENT_AMOUNT)
        define(new Property[]{HORIZONTAL_FACING, SEGMENT_AMOUNT}, "leaf_litter");
        // SeaPickleBlock: PICKLES, WATERLOGGED
        define(new Property[]{PICKLES, WATERLOGGED}, "sea_pickle");
        // GrowingPlantHeadBlock: AGE_25 (KelpBlock); CaveVinesBlock adds BERRIES; CaveVinesPlantBlock: BERRIES
        define(new Property[]{AGE_25}, "kelp");
        define(new Property[]{AGE_25, BERRIES}, "cave_vines");
        define(new Property[]{BERRIES}, "cave_vines_plant");
        // BigDripleafBlock: WATERLOGGED, FACING, TILT; BigDripleafStemBlock: WATERLOGGED, FACING
        define(new Property[]{WATERLOGGED, HORIZONTAL_FACING, TILT}, "big_dripleaf");
        define(new Property[]{WATERLOGGED, HORIZONTAL_FACING}, "big_dripleaf_stem");
        // BambooStalkBlock: AGE_1, LEAVES, STAGE
        define(new Property[]{AGE_1, BAMBOO_LEAVES, STAGE}, "bamboo");
        // VineBlock: UP, NORTH, EAST, SOUTH, WEST
        define(new Property[]{UP, NORTH, EAST, SOUTH, WEST}, "vine");
        // MossyCarpetBlock: BASE (BOTTOM), NORTH, EAST, SOUTH, WEST (WallSide)
        define(new Property[]{BOTTOM, NORTH_WALL, EAST_WALL, SOUTH_WALL, WEST_WALL}, "pale_moss_carpet");
        // SculkCatalystBlock: PULSE (BLOOM); PotentSulfurBlock: STATE (POTENT_SULFUR_STATE)
        define(new Property[]{BLOOM}, "sculk_catalyst");
        define(new Property[]{POTENT_SULFUR_STATE}, "potent_sulfur");
        // CoralBlock: none; BaseCoralPlantTypeBlock (CoralPlantBlock, CoralFanBlock): WATERLOGGED; BaseCoralWallFanBlock:
        // FACING (HORIZONTAL_FACING), WATERLOGGED
        for (String c : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {
            define(none, c + "_coral_block");
            define(new Property[]{WATERLOGGED}, c + "_coral", c + "_coral_fan");
            define(new Property[]{HORIZONTAL_FACING, WATERLOGGED}, c + "_coral_wall_fan");
        }
    }

    private BlockStateDefinitions() {
    }

    public static boolean isDefined(String id) {
        return DEFINITIONS.containsKey(id);
    }

    /** {@code getProperties()} of the block's state definition, in definition order. */
    public static List<Property> properties(String id) {
        List<Property> l = DEFINITIONS.get(id);
        if (l == null) throw new IllegalArgumentException("state definition not read from the jar for " + id);
        return l;
    }

    public static Map<String, List<Property>> all() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    /** {@code state.hasProperty(property)}. */
    public static boolean hasProperty(StateRef state, Property property) {
        return properties(state.id()).contains(property);
    }

    /** {@code state.setValue(property, value)}: throws when the block lacks the property or the value is not allowed. */
    public static StateRef setValue(StateRef state, Property property, String value) {
        if (!hasProperty(state, property)) {
            throw new IllegalArgumentException("Cannot set property " + property.name() + " as it does not exist in " + state.id());
        }
        if (!property.values().contains(value)) {
            throw new IllegalArgumentException("Cannot set property " + property.name() + " to " + value + " on " + state.id()
                    + ", it is not an allowed value");
        }
        Map<String, String> p = new TreeMap<>(state.properties());
        p.put(property.name(), value);
        return new StateRef(state.id(), p);
    }

    /** {@code state.trySetValue(property, value)}: unchanged when the block lacks the property. */
    public static StateRef trySetValue(StateRef state, Property property, String value) {
        return hasProperty(state, property) ? setValue(state, property, value) : state;
    }

    /** {@code BlockStateBase.withPropertiesOf(source)}: {@code copyProperty} = {@code trySetValue} for each of the source's properties. */
    public static StateRef withPropertiesOf(StateRef state, StateRef source) {
        StateRef s = state;
        for (Property p : properties(source.id())) s = trySetValue(s, p, source.properties().get(p.name()));
        return s;
    }

    /** The integer property named {@code name} of the state's block, or null ({@code RandomizedIntStateProvider.findProperty}). */
    public static Property findIntegerProperty(StateRef state, String name) {
        for (Property p : properties(state.id())) {
            if (p.name().equals(name) && p.kind() == Kind.INTEGER) return p;
        }
        return null;
    }

    /** The definition's default values must match {@link BlockProperties}' registered default state (checked by tests). */
    static Map<String, String> propertyNames(String id) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Property p : properties(id)) m.put(p.name(), p.kind().name());
        return m;
    }
}
