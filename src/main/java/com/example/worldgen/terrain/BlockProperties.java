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
        LAVA
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
        return entry(state.id()).fluid() != Fluid.NONE;
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
