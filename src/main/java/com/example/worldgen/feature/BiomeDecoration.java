package com.example.worldgen.feature;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.random.RandomSupport;
import com.example.worldgen.random.WorldgenRandom;
import com.example.worldgen.random.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Port of the feature half of {@code ChunkGenerator} (26.3): the memoised {@code featuresPerStep}
 * ({@code FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), biome -> settings.features(), true)}),
 * {@code getBiomeGenerationSettings(biome).hasFeature(feature)} and {@code applyBiomeDecoration(level, chunk,
 * structureManager)} (bytecode, 833 bytes):
 * <ol>
 *     <li>{@code origin = SectionPos.of(chunkPos, level.getMinSectionY()).origin()} = (minBlockX, minSectionY * 16, minBlockZ);</li>
 *     <li>{@code random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()))};
 *     {@code decorationSeed = random.setDecorationSeed(level.getSeed(), origin.x, origin.z)};</li>
 *     <li>possible biomes: every biome stored in the 3 x 3 chunks around the centre ({@code ChunkPos.rangeClosed(pos, 1)},
 *     section biome palettes), kept only if the biome source can return it;</li>
 *     <li>for every step {@code 0 .. max(11, featuresPerStep.size()) - 1}: structures first (none are ported, so
 *     {@code structureManager.shouldGenerateStructures()} has nothing to place), then the union of the step's feature indices
 *     over the possible biomes, sorted ascending; per index {@code random.setFeatureSeed(decorationSeed, index, step)} and
 *     {@code placer.placeWithBiomeCheck(feature, random, origin)} with one {@link FeaturePlacer} for the chunk.</li>
 * </ol>
 * A placed feature whose feature is an {@link UnportedFeature} is skipped (see that class for why this leaves the other
 * features' random streams intact).
 */
public final class BiomeDecoration implements DecorationGenerator {

    /** {@code GenerationStep.Decoration.values().length}. */
    public static final int DECORATION_STEPS = 11;

    /** Observer of the decoration loop (tests). */
    public interface Listener {
        /** Called after {@code setFeatureSeed} and before {@code placeWithBiomeCheck}. */
        default void beforeFeature(int step, int index, PlacedFeature feature, WorldgenRandom random) {
        }

        default void afterChunk(WorldgenRandom random) {
        }
    }

    private final List<String> possibleBiomes;
    private final Set<String> possibleBiomeSet;
    private final Map<String, List<List<PlacedFeature>>> biomeFeatures = new java.util.HashMap<>();
    private final Map<String, Set<PlacedFeature>> biomeFeatureSets = new java.util.HashMap<>();
    private final List<FeatureSorter.StepFeatureData<PlacedFeature>> featuresPerStep;
    private final int minY;
    private final int genDepth;
    private final int seaLevel;

    /**
     * @param possibleBiomes {@code biomeSource.possibleBiomes()} in its order (parameter-list order for the overworld)
     * @param biomes         the biome definitions (their {@code features} step lists)
     */
    public BiomeDecoration(List<String> possibleBiomes, Function<String, BiomeDefinition> biomes, FeatureLoader loader,
                           int minY, int genDepth, int seaLevel) {
        this(possibleBiomes, biomes, loader::placedFeature, minY, genDepth, seaLevel);
    }

    /** @param placedFeatures the named placed feature of an id (one shared object per id, as registry holders are) */
    public BiomeDecoration(List<String> possibleBiomes, Function<String, BiomeDefinition> biomes,
                           Function<String, PlacedFeature> placedFeatures, int minY, int genDepth, int seaLevel) {
        this.possibleBiomes = List.copyOf(possibleBiomes);
        this.possibleBiomeSet = new LinkedHashSet<>(possibleBiomes);
        this.minY = minY;
        this.genDepth = genDepth;
        this.seaLevel = seaLevel;
        for (String biome : possibleBiomes) {
            List<List<PlacedFeature>> steps = new ArrayList<>();
            Set<PlacedFeature> set = Collections.newSetFromMap(new IdentityHashMap<>());
            for (List<String> step : biomes.apply(biome).features()) {
                List<PlacedFeature> list = new ArrayList<>();
                for (String id : step) {
                    PlacedFeature f = placedFeatures.apply(id);
                    list.add(f);
                    set.add(f);
                }
                steps.add(List.copyOf(list));
            }
            biomeFeatures.put(biome, List.copyOf(steps));
            biomeFeatureSets.put(biome, set);
        }
        this.featuresPerStep = FeatureSorter.buildFeaturesPerStep(this.possibleBiomes, biomeFeatures::get, true);
    }

    public List<FeatureSorter.StepFeatureData<PlacedFeature>> featuresPerStep() {
        return featuresPerStep;
    }

    /** The step lists of a biome ({@code getBiomeGenerationSettings(biome).features()}). */
    public List<List<PlacedFeature>> biomeFeatures(String biome) {
        List<List<PlacedFeature>> l = biomeFeatures.get(biome);
        if (l == null) throw new IllegalArgumentException("biome not in the source: " + biome);
        return l;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int genDepth() {
        return genDepth;
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    /**
     * {@code BiomeGenerationSettings.hasFeature}: membership in the memoised set of the biome's listed placed features. Vanilla's
     * set is a {@code HashSet} over {@code PlacedFeature} record equality; identity is used here, which is the same as long as
     * no two distinct listed placed features are equal records (checked by the tests for the 26.3 data).
     */
    @Override
    public boolean biomeHasFeature(String biome, PlacedFeature feature) {
        Set<PlacedFeature> set = biomeFeatureSets.get(biome);
        if (set == null) throw new IllegalArgumentException("biome not in the source: " + biome);
        return set.contains(feature);
    }

    /** The possible biomes of the 3 x 3 chunks around the centre, in source order (the order does not affect the result). */
    public Set<String> possibleBiomesAround(GenRegion level, int chunkX, int chunkZ) {
        Set<String> stored = new java.util.HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                FeatureChunk c = level.getChunk(chunkX + dx, chunkZ + dz);
                c.forEachStoredBiome(stored::add);
            }
        }
        Set<String> out = new LinkedHashSet<>();
        for (String b : possibleBiomes) if (stored.contains(b)) out.add(b);
        return out;
    }

    /** {@code applyBiomeDecoration} for the region's centre chunk. */
    public void applyBiomeDecoration(GenRegion level, Listener listener) {
        int chunkX = level.centerChunkX();
        int chunkZ = level.centerChunkZ();
        Pos origin = new Pos(chunkX * 16, level.getMinSectionY() * 16, chunkZ * 16);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.x(), origin.z());
        Set<String> biomesHere = possibleBiomesAround(level, chunkX, chunkZ);
        int featureStepCount = featuresPerStep.size();
        FeaturePlacer placer = new FeaturePlacer(level, this);
        int generationSteps = Math.max(DECORATION_STEPS, featureStepCount);
        for (int stepIndex = 0; stepIndex < generationSteps; ++stepIndex) {
            if (stepIndex < featureStepCount) {
                FeatureSorter.StepFeatureData<PlacedFeature> stepData = featuresPerStep.get(stepIndex);
                TreeSet<Integer> indices = new TreeSet<>();
                for (String biome : biomesHere) {
                    List<List<PlacedFeature>> featuresInBiome = biomeFeatures.get(biome);
                    if (stepIndex < featuresInBiome.size()) {
                        for (PlacedFeature f : featuresInBiome.get(stepIndex)) indices.add(stepData.indexOf(f));
                    }
                }
                int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
                Arrays.sort(indexArray);
                for (int globalIndex : indexArray) {
                    PlacedFeature feature = stepData.features().get(globalIndex);
                    random.setFeatureSeed(decorationSeed, globalIndex, stepIndex);
                    if (listener != null) listener.beforeFeature(stepIndex, globalIndex, feature, random);
                    if (!feature.feature().isPorted()) continue;
                    placer.placeWithBiomeCheck(feature, random, origin);
                }
            }
        }
        if (listener != null) listener.afterChunk(random);
    }

    /** Whether {@code biome} is one of the source's biomes. */
    public boolean isPossibleBiome(String biome) {
        return possibleBiomeSet.contains(biome);
    }
}
