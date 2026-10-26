package com.example.worldgen.feature;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.feature.placement.PlacementModifiers;
import com.example.worldgen.feature.predicate.BlockPredicates;
import com.example.worldgen.feature.predicate.RuleTest;
import com.example.worldgen.feature.stateprovider.BlockStateProviders;
import com.example.worldgen.feature.level.BlockSurvival;
import com.example.worldgen.feature.types.BambooFeature;
import com.example.worldgen.feature.types.BlockColumnFeature;
import com.example.worldgen.feature.types.CompositeFeatures;
import com.example.worldgen.feature.types.DiskFeature;
import com.example.worldgen.feature.types.SimpleBlockFeature;
import com.example.worldgen.feature.types.VinesFeature;
import com.example.worldgen.provider.WeightedList;
import com.example.worldgen.feature.types.OreFeature;
import com.example.worldgen.feature.types.SpringFeature;
import com.example.worldgen.feature.types.UnderwaterMagmaFeature;
import com.example.worldgen.json.Json;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<ns>/worldgen/feature/*.json} and {@code worldgen/placed_feature/*.json} (26.3 codecs):
 * {@code Feature.CODEC} is {@code RegistryCodecs.holder(FEATURE, DIRECT_CODEC)} (a string references a named feature, an
 * object is an inline body dispatched on {@code "type"}); {@code PlacedFeature.CODEC} likewise ({@code feature} +
 * {@code placement}). Named entries are decoded once and shared, as vanilla's registry holders are (the feature sorter and
 * {@code BiomeFilter} work on these objects). Decode errors throw.
 *
 * <p>Feature types: all 58 ids of {@code FeatureTypes.bootstrap} are known ({@link #REGISTERED_TYPES}); the ported ones
 * ({@link #PORTED_TYPES}) decode to their classes, every other one to an {@link UnportedFeature}. Placement modifiers are
 * decoded for every placed feature (a nested unported feature still runs its modifiers before it is reached).
 */
public final class FeatureLoader {

    /** {@code FeatureTypes.bootstrap} (26.3), in registration order. */
    public static final List<String> REGISTERED_TYPES = List.of("bamboo", "block_blob", "block_column", "block_pile", "blue_ice",
            "bonus_chest", "chorus_plant", "coral_claw", "coral_tree", "delta_feature", "disk", "end_gateway", "end_island",
            "end_platform", "end_podium", "end_spike", "fallen_tree", "fill_layer", "fossil", "freeze_top_layer", "geode",
            "huge_brown_mushroom", "huge_fungus", "huge_red_mushroom", "iceberg", "lake", "large_dripstone", "monster_room",
            "multiface_growth", "netherrack_replace_blobs", "no_op", "ore", "overlay", "projected_random_patchy_square",
            "random_boolean_selector", "random_neighbor_spread", "random_selector", "replace_single_block", "root_system",
            "scattered_ore", "sculk_patch", "sequence", "simple_block", "simple_random_selector", "single_block_pillar",
            "speleothem", "speleothem_cluster", "spike", "spring_feature", "stepped_column_cluster", "template", "tree",
            "underwater_magma", "vegetation_patch", "vines", "void_start_platform", "waterlogged_vegetation_patch",
            "weighted_random_selector");

    /** The feature types ported so far. */
    public static final Set<String> PORTED_TYPES = Set.of("ore", "disk", "spring_feature", "underwater_magma", "no_op",
            "simple_block", "block_column", "bamboo", "vines", "random_selector", "simple_random_selector",
            "weighted_random_selector", "random_boolean_selector", "sequence", "overlay");

    private final Path dataRoot;
    private final BlockTags tags;
    private final BiomeDefinition.Noises noises;
    private final BlockStateProviders providers;
    private final BlockSurvival survival;
    private final Map<String, Feature> features = new HashMap<>();
    private final Map<String, PlacedFeature> placedFeatures = new HashMap<>();

    public FeatureLoader(Path dataRoot, BlockTags tags, BiomeDefinition.Noises noises) {
        this.dataRoot = dataRoot;
        this.tags = tags;
        this.noises = noises;
        this.providers = new BlockStateProviders(dataRoot, tags);
        this.survival = BlockSurvival.of(tags);
    }

    public BlockSurvival survival() {
        return survival;
    }

    public BlockStateProviders providers() {
        return providers;
    }

    public BlockTags tags() {
        return tags;
    }

    public BiomeDefinition.Noises noises() {
        return noises;
    }

    private Object read(String kind, String id) {
        String n = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        Path file = dataRoot.resolve(n.substring(0, n.indexOf(':'))).resolve("worldgen").resolve(kind)
                .resolve(n.substring(n.indexOf(':') + 1) + ".json");
        try {
            return Json.parseWithNumberLiterals(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("missing " + kind + " " + n, e);
        }
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** The named placed feature {@code id} (decoded once). */
    public synchronized PlacedFeature placedFeature(String id) {
        String n = normalize(id);
        PlacedFeature p = placedFeatures.get(n);
        if (p == null) {
            p = decodePlaced(n, read("placed_feature", n));
            placedFeatures.put(n, p);
        }
        return p;
    }

    /** {@code PlacedFeature.CODEC}: a reference or an inline object. */
    public PlacedFeature placedFeatureHolder(Object json) {
        if (json instanceof String s) return placedFeature(s);
        return decodePlaced(null, json);
    }

    private PlacedFeature decodePlaced(String id, Object json) {
        Map<String, Object> m = CodecJson.object(json, "placed feature");
        Feature feature = featureHolder(CodecJson.field(m, "feature"));
        Object placement = CodecJson.field(m, "placement");
        CodecJson.list(placement, "placement");
        return new PlacedFeature(id, feature, PlacementModifiers.list(placement, tags, noises), placement);
    }

    /** The named feature {@code id} (decoded once). */
    public synchronized Feature feature(String id) {
        String n = normalize(id);
        Feature f = features.get(n);
        if (f == null) {
            f = decodeFeature(read("feature", n));
            features.put(n, f);
        }
        return f;
    }

    /** {@code Feature.CODEC}: a reference or an inline body. */
    public Feature featureHolder(Object json) {
        if (json instanceof String s) return feature(s);
        return decodeFeature(json);
    }

    /** {@code Feature.DIRECT_CODEC}. */
    public Feature decodeFeature(Object json) {
        Map<String, Object> m = CodecJson.object(json, "feature");
        String type = CodecJson.type(m);
        if (!REGISTERED_TYPES.contains(type)) throw new CodecJson.CodecException("Unknown feature type: " + type);
        switch (type) {
            case "ore": {
                List<OreFeature.BlockReplacement> targets = new ArrayList<>();
                for (Object t : CodecJson.list(CodecJson.field(m, "targets"), "targets")) {
                    Map<String, Object> tm = CodecJson.object(t, "ore target");
                    RuleTest target = RuleTest.parse(CodecJson.field(tm, "target"), tags);
                    StateRef state = BlockStates.decode(CodecJson.field(tm, "state"));
                    targets.add(new OreFeature.BlockReplacement(target, state));
                }
                int size = CodecJson.intInRange(CodecJson.field(m, "size"), 0, 64, "size");
                float discard = CodecJson.floatInRange(CodecJson.field(m, "discard_chance_on_air_exposure"), 0.0F, 1.0F,
                        "discard_chance_on_air_exposure");
                return new OreFeature(List.copyOf(targets), size, discard);
            }
            case "disk":
                return new DiskFeature(providers.parse(CodecJson.field(m, "state_provider")),
                        BlockPredicates.parse(CodecJson.field(m, "target"), tags),
                        IntProvider.parse(CodecJson.field(m, "radius"), 0, 8),
                        CodecJson.intInRange(CodecJson.field(m, "half_height"), 0, 4, "half_height"));
            case "spring_feature": {
                Map<String, Object> state = CodecJson.object(CodecJson.field(m, "state"), "fluid state");
                String fluid = normalize((String) CodecJson.field(state, "id"));
                StateRef legacy = legacyBlock(fluid, state);
                Object rb = m.get("requires_block_below");
                Object rc = m.get("rock_count");
                Object hc = m.get("hole_count");
                return new SpringFeature(fluid, legacy, rb == null || CodecJson.boolValue(rb, "requires_block_below"),
                        rc == null ? 4 : CodecJson.intValue(rc, "rock_count"), hc == null ? 1 : CodecJson.intValue(hc, "hole_count"),
                        BlockSet.parse(CodecJson.field(m, "valid_blocks"), tags));
            }
            case "underwater_magma":
                return new UnderwaterMagmaFeature(CodecJson.intInRange(CodecJson.field(m, "floor_search_range"), 0, 512, "floor_search_range"),
                        CodecJson.intInRange(CodecJson.field(m, "placement_radius_around_floor"), 0, 64, "placement_radius_around_floor"),
                        CodecJson.floatInRange(CodecJson.field(m, "placement_probability_per_valid_position"), 0.0F, 1.0F,
                                "placement_probability_per_valid_position"));
            case "no_op":
                return NoOpFeature.INSTANCE;
            case "simple_block": {
                Object st = m.get("schedule_tick");
                return new SimpleBlockFeature(providers.parse(CodecJson.field(m, "to_place")),
                        st != null && CodecJson.boolValue(st, "schedule_tick"), survival);
            }
            case "block_column": {
                List<BlockColumnFeature.Layer> layers = new ArrayList<>();
                for (Object l : CodecJson.list(CodecJson.field(m, "layers"), "layers")) {
                    Map<String, Object> lm = CodecJson.object(l, "layer");
                    layers.add(new BlockColumnFeature.Layer(IntProvider.parseNonNegative(CodecJson.field(lm, "height")),
                            providers.parse(CodecJson.field(lm, "provider"))));
                }
                return new BlockColumnFeature(List.copyOf(layers), Direction.parse(CodecJson.field(m, "direction")),
                        BlockPredicates.parse(CodecJson.field(m, "allowed_placement"), tags),
                        CodecJson.boolValue(CodecJson.field(m, "prioritize_tip"), "prioritize_tip"));
            }
            case "bamboo":
                return new BambooFeature(CodecJson.floatInRange(CodecJson.field(m, "probability"), 0.0F, 1.0F, "probability"), survival);
            case "vines":
                return new VinesFeature();
            case "random_selector": {
                List<CompositeFeatures.WeightedPlacedFeature> entries = new ArrayList<>();
                for (Object e : CodecJson.list(CodecJson.field(m, "features"), "features")) {
                    Map<String, Object> em = CodecJson.object(e, "weighted placed feature");
                    entries.add(new CompositeFeatures.WeightedPlacedFeature(placedFeatureHolder(CodecJson.field(em, "feature")),
                            CodecJson.floatInRange(CodecJson.field(em, "chance"), 0.0F, 1.0F, "chance")));
                }
                return new CompositeFeatures.RandomSelector(List.copyOf(entries), placedFeatureHolder(CodecJson.field(m, "default")));
            }
            case "simple_random_selector":
                return new CompositeFeatures.SimpleRandomSelector(nonEmptyPlacedList(CodecJson.field(m, "features")));
            case "weighted_random_selector": {
                List<WeightedList.Weighted<PlacedFeature>> entries = new ArrayList<>();
                for (Object e : CodecJson.list(CodecJson.field(m, "features"), "features")) {
                    Map<String, Object> em = CodecJson.object(e, "weighted entry");
                    entries.add(new WeightedList.Weighted<>(placedFeatureHolder(CodecJson.field(em, "data")),
                            CodecJson.intInRange(CodecJson.field(em, "weight"), 0, Integer.MAX_VALUE, "weight")));
                }
                return new CompositeFeatures.WeightedRandomSelector(WeightedList.of(entries));
            }
            case "random_boolean_selector":
                return new CompositeFeatures.RandomBooleanSelector(placedFeatureHolder(CodecJson.field(m, "feature_true")),
                        placedFeatureHolder(CodecJson.field(m, "feature_false")));
            case "sequence":
                return new CompositeFeatures.Sequence(nonEmptyPlacedList(CodecJson.field(m, "features")));
            case "overlay":
                return new CompositeFeatures.Overlay(nonEmptyPlacedList(CodecJson.field(m, "features")));
            default:
                return new UnportedFeature("minecraft:" + type, json);
        }
    }

    /** {@code ExtraCodecs.nonEmptyHolderSet(PlacedFeature.LIST_CODEC)}: a non-empty list of placed feature holders, in order. */
    private List<PlacedFeature> nonEmptyPlacedList(Object json) {
        List<PlacedFeature> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "placed features")) out.add(placedFeatureHolder(o));
        if (out.isEmpty()) throw new CodecJson.CodecException("Holder set must not be empty");
        return List.copyOf(out);
    }

    /**
     * {@code FluidState.createLegacyBlock()} for the source fluids ({@code minecraft:water}, {@code minecraft:lava}; only
     * property {@code falling}): {@code Blocks.WATER/LAVA.defaultBlockState().setValue(LEVEL, getLegacyLevel(state))} with
     * {@code getLegacyLevel = isSource() ? 0 : ...}, so {@code level=0}. Flowing fluids are not used by the data.
     */
    private static StateRef legacyBlock(String fluid, Map<String, Object> state) {
        Object props = state.get("properties");
        if (props != null) {
            for (String key : CodecJson.object(props, "fluid properties").keySet()) {
                if (!key.equals("falling")) throw new CodecJson.CodecException("unknown property " + key + " of " + fluid);
            }
        }
        return switch (fluid) {
            case "minecraft:water" -> BlockProperties.WATER;
            case "minecraft:lava" -> BlockProperties.LAVA;
            default -> throw new CodecJson.CodecException("spring fluid not supported: " + fluid);
        };
    }

    /** {@code NoOpFeature}: places nothing and returns true. */
    public static final class NoOpFeature implements Feature {
        public static final NoOpFeature INSTANCE = new NoOpFeature();

        private NoOpFeature() {
        }

        @Override
        public String type() {
            return "minecraft:no_op";
        }

        @Override
        public boolean place(com.example.worldgen.feature.level.GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            return true;
        }
    }
}
