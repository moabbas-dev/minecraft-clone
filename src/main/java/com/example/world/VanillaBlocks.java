package com.example.world;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Strict mapping between vanilla 26.3 block states (a namespaced block id plus its properties, as worldgen JSON writes them:
 * {@code {"Name": "minecraft:...", "Properties": {...}}} or a bare id for the default state) and this engine's
 * {@link BlockType}s. An engine type stands for one vanilla block in its default (or one representative) state; a state the
 * engine cannot represent throws {@link UnsupportedBlockStateException} - nothing is ever substituted silently.
 *
 * <p>Property names, legal values and defaults are the ones the 26.3 client registers (the block classes' state definitions
 * and registerDefaultState calls: RotatedPillarBlock axis=y, LeavesBlock distance=7 persistent=false waterlogged=false,
 * SnowyBlock snowy=false, AbstractFurnaceBlock facing=north lit=false, ChestBlock facing=north type=single, TntBlock
 * unstable=false, FarmlandBlock moisture=0, SaplingBlock stage=0, LiquidBlock level=0, WallTorchBlock facing=north).
 * Properties that change nothing observable in this engine are accepted with any legal value: leaves' {@code distance} and
 * {@code persistent} (there is no leaf decay here). Everything else must be a value the engine has a block for.
 *
 * <p>Items ({@link BlockType}s that are tools, food, ingredients, buckets...) are not blocks and have no entry; neither do
 * the sapling's engine-internal growth stages 1-3 (vanilla's sapling has stage 0 and 1 only).
 */
public final class VanillaBlocks {
    private VanillaBlocks() {
    }

    /** A vanilla block state this engine has no block for. */
    public static final class UnsupportedBlockStateException extends RuntimeException {
        private final String id;
        private final Map<String, String> properties;

        public UnsupportedBlockStateException(String id, Map<String, String> properties, String reason) {
            super("Unsupported vanilla block state " + id + properties + ": " + reason);
            this.id = id;
            this.properties = properties;
        }

        public String id() {
            return id;
        }

        public Map<String, String> properties() {
            return properties;
        }
    }

    /** One vanilla block: its legal properties (value lists, the first value being the default) and how a full state maps to an engine type. */
    private record Spec(Map<String, List<String>> legal, Function<Map<String, String>, BlockType> resolve) {
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final Map<BlockType, String> IDS = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, Map<String, String>> STATES = new EnumMap<>(BlockType.class);

    private static final List<String> BOOL_FALSE_FIRST = List.of("false", "true");
    private static final List<String> HORIZONTAL_NORTH_FIRST = List.of("north", "south", "west", "east");

    static {
        // Blocks without properties.
        simple(BlockType.AIR, "air");
        for (BlockType t : new BlockType[]{
                BlockType.STONE, BlockType.DIRT, BlockType.COBBLESTONE, BlockType.OAK_PLANKS, BlockType.BEDROCK, BlockType.SAND,
                BlockType.GRAVEL, BlockType.GOLD_ORE, BlockType.IRON_ORE, BlockType.COAL_ORE, BlockType.GLASS, BlockType.SANDSTONE,
                BlockType.GOLD_BLOCK, BlockType.IRON_BLOCK, BlockType.BRICKS, BlockType.BOOKSHELF, BlockType.MOSSY_COBBLESTONE,
                BlockType.OBSIDIAN, BlockType.DIAMOND_ORE, BlockType.DIAMOND_BLOCK, BlockType.CRAFTING_TABLE, BlockType.ICE,
                BlockType.SNOW_BLOCK, BlockType.CLAY, BlockType.PUMPKIN, BlockType.NETHERRACK, BlockType.GLOWSTONE,
                BlockType.TERRACOTTA, BlockType.RED_SAND, BlockType.MUD, BlockType.COPPER_ORE, BlockType.SMOOTH_STONE, BlockType.TORCH,
                BlockType.CALCITE, BlockType.CINNABAR, BlockType.COARSE_DIRT, BlockType.COBBLED_DEEPSLATE, BlockType.DEEPSLATE_IRON_ORE,
                BlockType.GRANITE, BlockType.POWDER_SNOW, BlockType.PACKED_ICE, BlockType.RAW_COPPER_BLOCK, BlockType.RAW_IRON_BLOCK,
                BlockType.RED_SANDSTONE, BlockType.SULFUR, BlockType.TUFF, BlockType.WHITE_TERRACOTTA, BlockType.ORANGE_TERRACOTTA,
                BlockType.YELLOW_TERRACOTTA, BlockType.BROWN_TERRACOTTA, BlockType.RED_TERRACOTTA, BlockType.LIGHT_GRAY_TERRACOTTA}) {
            simple(t, t.name().toLowerCase(java.util.Locale.ROOT));
        }

        // SnowyBlock: snowy=true swaps the model for grass_block_snow, which the engine does not draw.
        for (BlockType t : new BlockType[]{BlockType.GRASS_BLOCK, BlockType.MYCELIUM, BlockType.PODZOL}) {
            onlyDefaults(t, t.name().toLowerCase(java.util.Locale.ROOT), props("snowy", BOOL_FALSE_FIRST));
        }

        // RotatedPillarBlock: only the upright (axis=y) log/deepslate exists here.
        for (BlockType t : new BlockType[]{BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG, BlockType.JUNGLE_LOG,
                BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG, BlockType.CHERRY_LOG, BlockType.DEEPSLATE}) {
            onlyDefaults(t, t.name().toLowerCase(java.util.Locale.ROOT), props("axis", List.of("y", "x", "z")));
        }

        // LeavesBlock: distance and persistent only drive leaf decay, which this engine does not have; waterlogged must be false.
        List<String> distances = List.of("7", "1", "2", "3", "4", "5", "6");
        for (BlockType t : new BlockType[]{BlockType.OAK_LEAVES, BlockType.BIRCH_LEAVES, BlockType.SPRUCE_LEAVES,
                BlockType.JUNGLE_LEAVES, BlockType.ACACIA_LEAVES, BlockType.MANGROVE_LEAVES, BlockType.DARK_OAK_LEAVES,
                BlockType.CHERRY_LEAVES}) {
            register(t.name().toLowerCase(java.util.Locale.ROOT),
                    props("distance", distances, "persistent", BOOL_FALSE_FIRST, "waterlogged", BOOL_FALSE_FIRST),
                    s -> s.get("waterlogged").equals("false") ? t : null, t);
        }

        onlyDefaults(BlockType.MANGROVE_ROOTS, "mangrove_roots", props("waterlogged", BOOL_FALSE_FIRST));
        onlyDefaults(BlockType.TNT, "tnt", props("unstable", BOOL_FALSE_FIRST));
        // The furnaces' lit look is world state here (see LightProperties.emission), not a block id; facing is not drawn.
        for (BlockType t : new BlockType[]{BlockType.FURNACE, BlockType.BLAST_FURNACE, BlockType.SMOKER}) {
            onlyDefaults(t, t.name().toLowerCase(java.util.Locale.ROOT), props("facing", HORIZONTAL_NORTH_FIRST, "lit", BOOL_FALSE_FIRST));
        }
        onlyDefaults(BlockType.CHEST, "chest", props("facing", HORIZONTAL_NORTH_FIRST, "type", List.of("single", "left", "right"),
                "waterlogged", BOOL_FALSE_FIRST));

        // SaplingBlock stage 0 is the placeable sapling; vanilla's stage 1 has no engine block (the engine's stages 1-3 are its own).
        register("oak_sapling", props("stage", List.of("0", "1")), s -> s.get("stage").equals("0") ? BlockType.OAK_SAPLING_STAGE_0 : null,
                BlockType.OAK_SAPLING_STAGE_0);

        // WallTorchBlock: facing points away from the wall, as the engine's WALL_TORCH_<facing> does (BlockSupport.facingOf).
        Map<String, BlockType> wallTorches = Map.of("north", BlockType.WALL_TORCH_NORTH, "south", BlockType.WALL_TORCH_SOUTH,
                "west", BlockType.WALL_TORCH_WEST, "east", BlockType.WALL_TORCH_EAST);
        SPECS.put("minecraft:wall_torch", new Spec(props("facing", HORIZONTAL_NORTH_FIRST), s -> wallTorches.get(s.get("facing"))));
        wallTorches.forEach((facing, t) -> reverse(t, "wall_torch", Map.of("facing", facing)));

        // FarmlandBlock moisture 0-7: FARMLAND is 0, FARMLAND_1..6, FARMLAND_MOIST is 7.
        BlockType[] farmland = {BlockType.FARMLAND, BlockType.FARMLAND_1, BlockType.FARMLAND_2, BlockType.FARMLAND_3,
                BlockType.FARMLAND_4, BlockType.FARMLAND_5, BlockType.FARMLAND_6, BlockType.FARMLAND_MOIST};
        SPECS.put("minecraft:farmland", new Spec(props("moisture", List.of("0", "1", "2", "3", "4", "5", "6", "7")),
                s -> farmland[Integer.parseInt(s.get("moisture"))]));
        for (int m = 0; m < farmland.length; m++) reverse(farmland[m], "farmland", Map.of("moisture", Integer.toString(m)));

        // LiquidBlock level 0-15 (LiquidBlock.getFluidState): 0 is the source, 1-7 flowing with amount 8 - level, 8-15 falling.
        fluid(false);
        fluid(true);
    }

    private static void fluid(boolean lava) {
        String name = lava ? "lava" : "water";
        List<String> levels = new java.util.ArrayList<>();
        for (int i = 0; i <= 15; i++) levels.add(Integer.toString(i));
        SPECS.put("minecraft:" + name, new Spec(props("level", levels), s -> {
            int level = Integer.parseInt(s.get("level"));
            BlockType t = level == 0 ? BlockType.fluidBlock(lava, 8, false)
                    : level >= 8 ? BlockType.fluidBlock(lava, 8, true)
                    : BlockType.fluidBlock(lava, 8 - level, false);
            // fluidBlock rounds odd lava amounts down: the engine only has lava amounts 2, 4 and 6 (overworld lava drops 2 a block)
            return t.fluidAmount() == (level == 0 || level >= 8 ? 8 : 8 - level) ? t : null;
        }));
        for (BlockType t : BlockType.values()) {
            if (lava ? !t.isLava() : !t.isWater()) continue;
            int level = t.isFluidSource() ? 0 : t.isFluidFalling() ? 8 : 8 - t.fluidAmount();
            reverse(t, name, Map.of("level", Integer.toString(level)));
        }
    }

    private static Map<String, List<String>> props(Object... nameValues) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> values = (List<String>) nameValues[i + 1];
            m.put((String) nameValues[i], values);
        }
        return m;
    }

    private static void simple(BlockType t, String path) {
        register(path, Map.of(), s -> t, t);
    }

    /** A block the engine has in its default state only: any other value of any property is unsupported. */
    private static void onlyDefaults(BlockType t, String path, Map<String, List<String>> legal) {
        register(path, legal, s -> {
            for (Map.Entry<String, List<String>> e : legal.entrySet()) {
                if (!s.get(e.getKey()).equals(e.getValue().get(0))) return null;
            }
            return t;
        }, t);
    }

    private static void register(String path, Map<String, List<String>> legal, Function<Map<String, String>, BlockType> resolve, BlockType t) {
        SPECS.put("minecraft:" + path, new Spec(legal, resolve));
        Map<String, String> defaults = new LinkedHashMap<>();
        legal.forEach((k, v) -> defaults.put(k, v.get(0)));
        reverse(t, path, defaults);
    }

    private static void reverse(BlockType t, String path, Map<String, String> state) {
        if (IDS.put(t, "minecraft:" + path) != null) throw new IllegalStateException(t + " mapped twice");
        STATES.put(t, Collections.unmodifiableMap(new LinkedHashMap<>(state)));
    }

    /**
     * The engine block for a vanilla block state. {@code properties} may be null or partial (missing properties take the
     * vanilla default); an id without a namespace means {@code minecraft:}.
     *
     * @throws UnsupportedBlockStateException for an unknown block, an unknown property or value, or a state the engine has no block for
     */
    public static BlockType byVanilla(String namespacedId, Map<String, String> properties) {
        Map<String, String> given = properties == null ? Map.of() : properties;
        String id = namespacedId == null ? "" : namespacedId.contains(":") ? namespacedId : "minecraft:" + namespacedId;
        Spec spec = SPECS.get(id);
        if (spec == null) throw new UnsupportedBlockStateException(id, given, "no engine block for this vanilla block");
        Map<String, String> full = new HashMap<>();
        spec.legal().forEach((k, v) -> full.put(k, v.get(0)));
        for (Map.Entry<String, String> e : given.entrySet()) {
            List<String> values = spec.legal().get(e.getKey());
            if (values == null) throw new UnsupportedBlockStateException(id, given, "vanilla " + id + " has no property " + e.getKey());
            if (!values.contains(e.getValue())) {
                throw new UnsupportedBlockStateException(id, given, e.getValue() + " is not a legal value of " + e.getKey());
            }
            full.put(e.getKey(), e.getValue());
        }
        BlockType t = spec.resolve().apply(full);
        if (t == null) throw new UnsupportedBlockStateException(id, given, "the engine has no block for this state");
        return t;
    }

    /** {@link #byVanilla(String, Map)} for a state without properties (the block's default state). */
    public static BlockType byVanilla(String namespacedId) {
        return byVanilla(namespacedId, Map.of());
    }

    /** Whether {@code type} is a block with a vanilla equivalent (false for items and engine-internal states). */
    public static boolean isVanillaBlock(BlockType type) {
        return IDS.containsKey(type);
    }

    /** The namespaced vanilla id of an engine block; {@link #vanillaProperties} gives the matching state. */
    public static String vanillaId(BlockType type) {
        String id = IDS.get(type);
        if (id == null) throw new IllegalArgumentException(type + " is not a vanilla block (an item or an engine-internal state)");
        return id;
    }

    /** The full vanilla state (every property of the block) an engine block stands for. */
    public static Map<String, String> vanillaProperties(BlockType type) {
        Map<String, String> state = STATES.get(type);
        if (state == null) throw new IllegalArgumentException(type + " is not a vanilla block (an item or an engine-internal state)");
        return state;
    }
}
