package com.example.worldgen.feature.placement;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.PlacedFeature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.BlockPredicate;
import com.example.worldgen.feature.predicate.BlockPredicates;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.HeightProvider;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The 18 placement modifier types of {@code PlacementModifierTypes.bootstrap} (26.3), in registration order:
 * {@code block_predicate_filter, rarity_filter, random_chance, surface_relative_threshold_filter,
 * surface_water_depth_filter, biome, count, noise_based_count, noise_threshold_count, count_on_every_layer, cuboid,
 * environment_scan, heightmap, height_range, in_square, offset, randomly_selected, fixed_placement}. Each nested class
 * names the vanilla class it ports; the bodies (random draw order included) are transcribed from the bytecode, and
 * {@link #parse} mirrors each {@code CODEC} (field names, optional defaults, range checks).
 */
public final class PlacementModifiers {

    private PlacementModifiers() {
    }

    /** {@code PlacementModifier.CODEC} (dispatch on {@code "type"}). */
    public static PlacementModifier parse(Object json, BlockTags tags, BiomeDefinition.Noises noises) {
        Map<String, Object> m = CodecJson.object(json, "placement modifier");
        String type = CodecJson.type(m);
        switch (type) {
            case "block_predicate_filter":
                return new BlockPredicateFilter(BlockPredicates.parse(CodecJson.field(m, "predicate"), tags));
            case "rarity_filter":
                return new RarityFilter(CodecJson.intInRange(CodecJson.field(m, "chance"), 1, Integer.MAX_VALUE, "chance"));
            case "random_chance":
                return new RandomChance(CodecJson.floatInRange(CodecJson.field(m, "chance"), 0.0F, 1.0F, "chance"));
            case "surface_relative_threshold_filter":
                return new SurfaceRelativeThresholdFilter(heightmap(CodecJson.field(m, "heightmap")),
                        optInt(m, "min_inclusive", Integer.MIN_VALUE), optInt(m, "max_inclusive", Integer.MAX_VALUE));
            case "surface_water_depth_filter":
                return new SurfaceWaterDepthFilter(CodecJson.intField(m, "max_water_depth"));
            case "biome":
                return BiomeFilter.INSTANCE;
            case "count":
                return new Count(IntProvider.parse(CodecJson.field(m, "count"), 0, 4096));
            case "noise_based_count": {
                Object offset = m.get("noise_offset");
                return new NoiseBasedCount(CodecJson.intField(m, "noise_to_count_ratio"), doubleValue(CodecJson.field(m, "noise_factor")),
                        offset == null ? 0.0 : doubleValue(offset), noises);
            }
            case "noise_threshold_count":
                return new NoiseThresholdCount(doubleValue(CodecJson.field(m, "noise_level")), CodecJson.intField(m, "below_noise"),
                        CodecJson.intField(m, "above_noise"), noises);
            case "count_on_every_layer":
                return new CountOnEveryLayer(IntProvider.parse(CodecJson.field(m, "count"), 0, 256));
            case "cuboid":
                return new Cuboid(IntProvider.parse(CodecJson.field(m, "xz_size"), 1, 16), IntProvider.parse(CodecJson.field(m, "y_size"), 1, 16),
                        optBool(m, "include_edges", true), optBool(m, "include_interior", true));
            case "environment_scan": {
                Object allowed = m.get("allowed_search_condition");
                return new EnvironmentScan(Direction.parseVertical(CodecJson.field(m, "direction_of_search")),
                        BlockPredicates.parse(CodecJson.field(m, "target_condition"), tags),
                        allowed == null ? BlockPredicates.parse(Map.of("type", "minecraft:true"), tags) : BlockPredicates.parse(allowed, tags),
                        CodecJson.intInRange(CodecJson.field(m, "max_steps"), 1, 32, "max_steps"));
            }
            case "heightmap":
                return new Heightmap(heightmap(CodecJson.field(m, "heightmap")));
            case "height_range":
                return new HeightRange(HeightProvider.parse(CodecJson.field(m, "height")));
            case "in_square":
                return InSquare.INSTANCE;
            case "offset":
                return new Offset(IntProvider.parse(CodecJson.field(m, "x"), -16, 16), IntProvider.parse(CodecJson.field(m, "y"), -16, 16),
                        IntProvider.parse(CodecJson.field(m, "z"), -16, 16));
            case "randomly_selected": {
                List<PlacementModifier> placements = list(CodecJson.field(m, "placements"), tags, noises);
                if (placements.isEmpty()) throw new CodecJson.CodecException("List must have contents");
                return new RandomlySelected(placements);
            }
            case "fixed_placement": {
                List<Pos> positions = new ArrayList<>();
                for (Object o : CodecJson.list(CodecJson.field(m, "positions"), "positions")) positions.add(Pos.parse(o));
                return new Fixed(List.copyOf(positions));
            }
            default:
                throw new CodecJson.CodecException("Unknown placement modifier type: " + type);
        }
    }

    /** {@code PlacementModifier.CODEC.listOf()}. */
    public static List<PlacementModifier> list(Object json, BlockTags tags, BiomeDefinition.Noises noises) {
        List<PlacementModifier> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "placement")) out.add(parse(o, tags, noises));
        return List.copyOf(out);
    }

    private static HeightmapType heightmap(Object json) {
        if (!(json instanceof String s)) throw new CodecJson.CodecException("Not a heightmap type: " + json);
        try {
            return HeightmapType.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new CodecJson.CodecException("Unknown heightmap type: " + s);
        }
    }

    private static int optInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v == null ? def : CodecJson.intValue(v, key);
    }

    private static boolean optBool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v == null ? def : CodecJson.boolValue(v, key);
    }

    private static double doubleValue(Object json) {
        if (json instanceof Number n) return n.doubleValue();
        throw new CodecJson.CodecException("Not a number: " + json);
    }

    // ------------------------------------------------------------------ the modifiers

    /** {@code BlockPredicateFilter}: {@code predicate.test(context.getLevel(), origin)}. */
    public record BlockPredicateFilter(BlockPredicate predicate) implements PlacementModifier.Filter {
        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            return predicate.test(context.getLevel(), origin);
        }
    }

    /** {@code RarityFilter}: {@code random.nextFloat() < 1.0F / (float) chance}. */
    public record RarityFilter(int chance) implements PlacementModifier.Filter {
        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            return random.nextFloat() < 1.0F / (float) chance;
        }
    }

    /** {@code RandomChancePlacement}: {@code random.nextFloat() < chance}. */
    public record RandomChance(float chance) implements PlacementModifier.Filter {
        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            return random.nextFloat() < chance;
        }
    }

    /** {@code SurfaceRelativeThresholdFilter}: in longs, {@code surface + min <= y <= surface + max}. */
    public record SurfaceRelativeThresholdFilter(HeightmapType heightmap, int minInclusive, int maxInclusive)
            implements PlacementModifier.Filter {
        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            long surfaceY = context.getHeight(heightmap, origin.x(), origin.z());
            long minY = surfaceY + (long) minInclusive;
            long maxY = surfaceY + (long) maxInclusive;
            return minY <= (long) origin.y() && (long) origin.y() <= maxY;
        }
    }

    /** {@code SurfaceWaterDepthFilter}: {@code WORLD_SURFACE height - OCEAN_FLOOR height <= maxWaterDepth}. */
    public record SurfaceWaterDepthFilter(int maxWaterDepth) implements PlacementModifier.Filter {
        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            int yOceanFloor = context.getHeight(HeightmapType.OCEAN_FLOOR, origin.x(), origin.z());
            int ySurfaceFloor = context.getHeight(HeightmapType.WORLD_SURFACE, origin.x(), origin.z());
            return ySurfaceFloor - yOceanFloor <= maxWaterDepth;
        }
    }

    /**
     * {@code BiomeFilter}: throws without a top feature; else {@code generator.getBiomeGenerationSettings(level.getBiome(origin))
     * .hasFeature(topFeature)}.
     */
    public static final class BiomeFilter implements PlacementModifier.Filter {
        public static final BiomeFilter INSTANCE = new BiomeFilter();

        private BiomeFilter() {
        }

        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin) {
            PlacedFeature feature = context.topFeature();
            if (feature == null) {
                throw new IllegalStateException("Tried to biome check an unregistered feature, or a feature that should not restrict the biome");
            }
            return context.generator().biomeHasFeature(context.getLevel().getBiome(origin), feature);
        }
    }

    /** {@code CountPlacement}: {@code count.sample(random)}. */
    public record Count(IntProvider count) implements PlacementModifier.Repeating {
        @Override
        public int count(RandomSource random, Pos origin) {
            return count.sample(random);
        }
    }

    /**
     * {@code NoiseBasedCountPlacement}: {@code (int) Math.ceil((BIOME_INFO_NOISE.get(x / factor, z / factor) + offset) *
     * ratio)} (the float noise value widened to double).
     */
    public static final class NoiseBasedCount implements PlacementModifier.Repeating {
        private final int noiseToCountRatio;
        private final double noiseFactor;
        private final double noiseOffset;
        private final BiomeDefinition.Noises noises;

        NoiseBasedCount(int noiseToCountRatio, double noiseFactor, double noiseOffset, BiomeDefinition.Noises noises) {
            this.noiseToCountRatio = noiseToCountRatio;
            this.noiseFactor = noiseFactor;
            this.noiseOffset = noiseOffset;
            this.noises = noises;
        }

        @Override
        public int count(RandomSource random, Pos origin) {
            double flowerNoise = noises.biomeInfoNoise().get((double) origin.x() / noiseFactor, (double) origin.z() / noiseFactor);
            return (int) Math.ceil((flowerNoise + noiseOffset) * (double) noiseToCountRatio);
        }
    }

    /** {@code NoiseThresholdCountPlacement}: {@code BIOME_INFO_NOISE.get(x / 200.0, z / 200.0) < level ? below : above}. */
    public static final class NoiseThresholdCount implements PlacementModifier.Repeating {
        private final double noiseLevel;
        private final int belowNoise;
        private final int aboveNoise;
        private final BiomeDefinition.Noises noises;

        NoiseThresholdCount(double noiseLevel, int belowNoise, int aboveNoise, BiomeDefinition.Noises noises) {
            this.noiseLevel = noiseLevel;
            this.belowNoise = belowNoise;
            this.aboveNoise = aboveNoise;
            this.noises = noises;
        }

        @Override
        public int count(RandomSource random, Pos origin) {
            double flowerNoise = noises.biomeInfoNoise().get((double) origin.x() / 200.0, (double) origin.z() / 200.0);
            return flowerNoise < noiseLevel ? belowNoise : aboveNoise;
        }
    }

    /**
     * {@code CountOnEveryLayerPlacement}: layer by layer while a layer found any position; per layer
     * {@code count.sample(random)} is re-evaluated in the loop condition, each iteration draws x then z, starts at the
     * {@code MOTION_BLOCKING} height and scans down for the {@code layer}-th "non-empty below empty" boundary (empty = air,
     * water or lava; bedrock below does not count).
     */
    public record CountOnEveryLayer(IntProvider count) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int layer = 0;
            boolean foundAny;
            do {
                foundAny = false;
                for (int i = 0; i < count.sample(random); ++i) {
                    int x = random.nextInt(16) + origin.x();
                    int z = random.nextInt(16) + origin.z();
                    int startY = context.getHeight(HeightmapType.MOTION_BLOCKING, x, z);
                    int y = findOnGroundYPosition(context, x, startY, z, layer);
                    if (y != Integer.MAX_VALUE) {
                        output.accept(new Pos(x, y, z));
                        foundAny = true;
                    }
                }
                ++layer;
            } while (foundAny);
        }

        private static int findOnGroundYPosition(PlacementContext context, int x, int yStart, int z, int layerToPlaceOn) {
            int currentLayer = 0;
            StateRef currentBlock = context.getBlockState(new Pos(x, yStart, z));
            for (int y = yStart; y >= context.getMinY() + 1; --y) {
                StateRef belowBlock = context.getBlockState(new Pos(x, y - 1, z));
                if (!isEmpty(belowBlock) && isEmpty(currentBlock) && !belowBlock.is("minecraft:bedrock")) {
                    if (currentLayer == layerToPlaceOn) return y - 1 + 1;
                    ++currentLayer;
                }
                currentBlock = belowBlock;
            }
            return Integer.MAX_VALUE;
        }

        private static boolean isEmpty(StateRef state) {
            return BlockProperties.isAir(state) || state.is("minecraft:water") || state.is("minecraft:lava");
        }
    }

    /**
     * {@code CuboidPlacement} (package {@code levelgen.feature} in 26.3): samples height, width, length (in that order),
     * then x outer, y, z inner from 0 to the size inclusive, emitting each position the edge/interior flags allow.
     */
    public record Cuboid(IntProvider xzSize, IntProvider ySize, boolean includeEdges, boolean includeInterior) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int height = ySize.sample(random);
            int width = xzSize.sample(random);
            int length = xzSize.sample(random);
            for (int x = 0; x <= width; ++x) {
                for (int y = 0; y <= height; ++y) {
                    for (int z = 0; z <= length; ++z) {
                        if ((includeEdges || x != 0 && x != width || y != 0 && y != height)
                                && (includeEdges || z != 0 && z != length || y != 0 && y != height)
                                && (includeEdges || x != 0 && x != width || z != 0 && z != length)
                                && (includeInterior || x == 0 || x == width || y == 0 || y == height || z == 0 || z == length)) {
                            output.accept(new Pos(x + origin.x(), y + origin.y(), z + origin.z()));
                        }
                    }
                }
            }
        }
    }

    /**
     * {@code EnvironmentScanPlacement}: nothing unless the origin passes {@code allowed}; then up to {@code maxSteps} times:
     * emit and stop on {@code target}, else step, stop outside the build height, continue while {@code allowed} holds; when
     * the steps run out or {@code allowed} fails, emit the current position if it passes {@code target}.
     */
    public record EnvironmentScan(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition,
                                  int maxSteps) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            GenRegion level = context.getLevel();
            Pos pos = origin;
            if (!allowedSearchCondition.test(level, pos)) return;
            for (int i = 0; i < maxSteps; ++i) {
                if (targetCondition.test(level, pos)) {
                    output.accept(pos);
                    return;
                }
                pos = pos.relative(directionOfSearch);
                if (level.isOutsideBuildHeight(pos.y())) return;
                if (!allowedSearchCondition.test(level, pos)) break;
            }
            if (targetCondition.test(level, pos)) output.accept(pos);
        }
    }

    /** {@code HeightmapPlacement}: {@code y = getHeight(type, x, z)}, emitted only when {@code y > getMinY()}. */
    public record Heightmap(HeightmapType heightmap) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int height = context.getHeight(heightmap, origin.x(), origin.z());
            if (height > context.getMinY()) output.accept(new Pos(origin.x(), height, origin.z()));
        }
    }

    /** {@code HeightRangePlacement}: {@code origin.atY(height.sample(random, context))}. */
    public record HeightRange(HeightProvider height) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            output.accept(origin.atY(height.sample(random, context.worldGenerationContext())));
        }
    }

    /** {@code InSquarePlacement}: {@code x = nextInt(16) + x} first, then {@code z = nextInt(16) + z}. */
    public static final class InSquare implements PlacementModifier {
        public static final InSquare INSTANCE = new InSquare();

        private InSquare() {
        }

        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int x = random.nextInt(16) + origin.x();
            int z = random.nextInt(16) + origin.z();
            output.accept(new Pos(x, origin.y(), z));
        }
    }

    /** {@code OffsetPlacement}: x, y, z sampled in that order. */
    public record Offset(IntProvider x, IntProvider y, IntProvider z) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int dx = x.sample(random);
            int dy = y.sample(random);
            int dz = z.sample(random);
            output.accept(origin.offset(dx, dy, dz));
        }
    }

    /** {@code RandomlySelectedPlacement}: {@code Util.getRandom(placements, random)} ({@code nextInt(size)}), then its modify. */
    public record RandomlySelected(List<PlacementModifier> placements) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            placements.get(random.nextInt(placements.size())).modify(context, random, origin, output);
        }
    }

    /** {@code FixedPlacement}: the listed positions that lie in the origin's chunk, in list order. */
    public record Fixed(List<Pos> positions) implements PlacementModifier {
        @Override
        public void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int chunkX = origin.x() >> 4;
            int chunkZ = origin.z() >> 4;
            for (Pos p : positions) {
                if (chunkX == p.x() >> 4 && chunkZ == p.z() >> 4) output.accept(p);
            }
        }
    }
}
