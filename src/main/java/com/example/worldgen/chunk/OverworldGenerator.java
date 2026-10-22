package com.example.worldgen.chunk;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeDefinitionLoader;
import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.biome.MultiNoiseBiomeSourceParameterList;
import com.example.worldgen.carver.CarverLoader;
import com.example.worldgen.carver.CarverStep;
import com.example.worldgen.carver.CarvingMask;
import com.example.worldgen.carver.WorldCarver;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.NoiseGeneratorSettingsLoader;
import com.example.worldgen.levelgen.RandomState;
import com.example.worldgen.material.BiomeAt;
import com.example.worldgen.material.BiomeClimate;
import com.example.worldgen.material.MaterialRuleLoader;
import com.example.worldgen.material.MaterialSystem;
import com.example.worldgen.material.rule.MaterialRule;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.terrain.Aquifer;
import com.example.worldgen.terrain.BiomeManager;
import com.example.worldgen.terrain.BiomeResolver;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;
import com.example.worldgen.terrain.NoiseChunk;
import com.example.worldgen.terrain.TerrainBiomes;
import com.example.worldgen.terrain.TerrainChunk;
import com.example.worldgen.terrain.TerrainFiller;
import com.example.worldgen.terrain.VanillaTerrain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 26.3 overworld chunk generator up to the end of the TERRAIN status, for one world seed: vanilla's
 * {@code NoiseBasedChunkGenerator} over the {@code minecraft:overworld} noise settings and the overworld
 * {@code MultiNoiseBiomeSource} preset, as {@code ChunkStatusTasks} drives it:
 * <ol>
 *     <li>BIOMES ({@code ChunkGenerator.createBiomes}): {@link TerrainBiomes#createBiomeTable}, cached per chunk
 *     position because the surface stage of a chunk reads the stored biomes of its eight neighbours too;</li>
 *     <li>TERRAIN ({@code NoiseBasedChunkGenerator.buildTerrain}, supplier {@code lambda$buildTerrain$0}, read from the
 *     jar): {@code createNoiseChunk}, {@code doFill}, {@code buildSurface} ({@code randomState.surfaceSystem()
 *     .buildSurface(randomState, biomeManager, context, chunk, noiseChunk, materialRule, possibleBiomes)} with the
 *     region's {@code BiomeManager} and {@code collectPossibleBiomes(region, 1)}), {@code generateCarvers} (the carving
 *     mask, then {@code applyCarvingMask}), {@code noiseChunk.close()}; then the TERRAIN completion primes the four
 *     final heightmaps ({@link VanillaTerrain#finishTerrainStatus}).</li>
 * </ol>
 * No stage reads the blocks of another chunk: the neighbours contribute only their biome tables (surface) and their
 * coordinates (the 17 x 17 carver source chunks), so every chunk is generated on its own and the result does not depend
 * on the generation order.
 *
 * <p>Structures, features, the blender (old-chunk blending) and the beardifier do not exist yet: the structure manager
 * is empty, so {@code Beardifier.forStructuresInChunk} is the empty beardifier and {@code Blender.of(region)} is
 * {@code Blender.EMPTY} (its carving filter is {@code null}), exactly as for a new world without structures.
 *
 * <p>Thread safety: the biome-table cache is synchronized; {@link RandomState} hands each {@link NoiseChunk} its own
 * buffer pool. One instance may generate chunks on one thread at a time (callers that use several threads need one
 * instance per thread).
 */
public final class OverworldGenerator {

    /** The noise settings and biome source preset this generator uses. */
    public static final String OVERWORLD = "minecraft:overworld";
    /** The restore script the {@code data/} folder comes from (gitignored; extracted from the user's 26.3 client jar). */
    public static final String RESTORE_HINT = "python scripts/restore-worldgen-data.py";
    /** Biome tables kept for neighbouring chunks (each about 6 KB). */
    private static final int BIOME_CACHE_SIZE = 2048;

    private final long seed;
    private final Path dataRoot;
    private final NoiseGeneratorSettings settings;
    private final RandomState randomState;
    private final MultiNoiseBiomeSource biomeSource;
    private final BlockTags tags;
    private final TerrainFiller filler;
    private final MaterialSystem materialSystem;
    private final MaterialRule materialRule;
    private final BiomeDefinition.Noises biomeNoises;
    private final BiomeDefinitionLoader biomeLoader;
    private final Map<String, BiomeDefinition> biomeDefinitions = new ConcurrentHashMap<>();
    private final BiomeClimate biomeClimate;
    private final List<WorldCarver> carvers;
    private final WorldGenerationContext context;
    private final BiomeResolver uncachedResolver;
    private final long biomeZoomSeed;
    private final int minY;
    private final int height;
    private final Map<Long, String[]> biomeTables = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String[]> eldest) {
            return size() > BIOME_CACHE_SIZE;
        }
    };

    /**
     * Sets the world up from its seed (vanilla: {@code RandomState.create(settings, noises, seed)} and the generator's
     * fields): loads the noise settings, the material rule, the carvers and the tags from {@code dataRoot}.
     *
     * @throws MissingWorldgenDataException when {@code dataRoot} does not hold the 26.3 worldgen data
     */
    public OverworldGenerator(Path dataRoot, long seed) {
        if (!Files.isDirectory(dataRoot.resolve("minecraft").resolve("worldgen").resolve("noise_settings"))
                || !Files.isDirectory(dataRoot.resolve("minecraft").resolve("tags").resolve("block"))) {
            throw new MissingWorldgenDataException(dataRoot);
        }
        this.seed = seed;
        this.dataRoot = dataRoot;
        WorldgenRegistries registries = new WorldgenRegistries(dataRoot);
        this.settings = new NoiseGeneratorSettingsLoader(registries).load(OVERWORLD);
        this.randomState = RandomState.create(registries, seed, settings);
        this.biomeSource = MultiNoiseBiomeSource.createFromPreset(
                new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD));
        this.tags = BlockTags.load(dataRoot);
        this.filler = new TerrainFiller(settings);
        // RandomState's constructor: new MaterialSystem(randomState, settings.defaultBlock(), settings.seaLevel(), ...)
        this.materialSystem = MaterialSystem.create(randomState, filler.defaultBlock(), settings.seaLevel());
        MaterialRuleLoader ruleLoader = new MaterialRuleLoader(registries);
        Optional<String> ruleId = settings.materialRuleId();
        this.materialRule = ruleId.isPresent() ? ruleLoader.rule(ruleId.get()) : ruleLoader.decodeRule(settings.materialRule());
        this.biomeNoises = BiomeDefinition.Noises.vanilla();
        this.biomeLoader = new BiomeDefinitionLoader(dataRoot);
        this.biomeClimate = BiomeClimate.vanilla(this::biome, biomeNoises);
        this.minY = settings.noiseSettings().minY();
        this.height = settings.noiseSettings().height();
        // new WorldGenerationContext(generator, chunk.getHeightAccessorForGeneration()): max(minY, minY), min(height, genDepth)
        this.context = WorldGenerationContext.forGenerator(minY, height, minY, height, settings.seaLevel());
        this.carvers = loadCarvers();
        this.uncachedResolver = TerrainBiomes.createUncachedResolver(randomState, biomeSource);
        this.biomeZoomSeed = BiomeManager.obfuscateSeed(seed);
    }

    /**
     * {@code generateCarvers} looks the carver list up per source chunk ({@code getBiomeGenerationSettingsForCarver}: the
     * biome at quart {@code (minBlockX >> 2, 0, minBlockZ >> 2)} of the uncached resolver). Every biome the overworld
     * source can return carries the same list in the 26.3 data, which is checked here, so the lookup is a constant; a
     * data set where the lists differ is refused instead of being approximated.
     */
    private List<WorldCarver> loadCarvers() {
        List<String> ids = null;
        for (String biome : biomeSource.possibleBiomes()) {
            List<String> list = biome(biome).carvers();
            if (ids == null) {
                ids = list;
            } else if (!ids.equals(list)) {
                throw new IllegalStateException("overworld biomes carry different carver lists (" + ids + " vs " + biome + " " + list
                        + "): the per-source-chunk carver lookup is not implemented");
            }
        }
        if (ids == null) throw new IllegalStateException("the overworld biome source has no biomes");
        try {
            java.util.ArrayList<WorldCarver> out = new java.util.ArrayList<>();
            for (String id : ids) {
                out.add(CarverLoader.load(dataRoot, id.substring(id.indexOf(':') + 1)));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load the overworld carvers", e);
        }
    }

    public long seed() {
        return seed;
    }

    public NoiseGeneratorSettings settings() {
        return settings;
    }

    public RandomState randomState() {
        return randomState;
    }

    public int seaLevel() {
        return settings.seaLevel();
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public BlockTags tags() {
        return tags;
    }

    public BiomeDefinition.Noises biomeNoises() {
        return biomeNoises;
    }

    /** {@code BiomeManager.obfuscateSeed(seed)}: the zoom seed of the level's {@code BiomeManager}. */
    public long biomeZoomSeed() {
        return biomeZoomSeed;
    }

    /** The biome JSON of {@code id} (loaded once). */
    public BiomeDefinition biome(String id) {
        return biomeDefinitions.computeIfAbsent(id, key -> {
            try {
                return biomeLoader.load(key);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot load biome " + key, e);
            }
        });
    }

    // ------------------------------------------------------------------ biomes

    /** The quart biome table the BIOMES status stores in chunk {@code (chunkX, chunkZ)} (computed once, then cached). */
    public String[] biomeTable(int chunkX, int chunkZ) {
        long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
        synchronized (biomeTables) {
            String[] table = biomeTables.get(key);
            if (table != null) return table;
        }
        String[] table = TerrainBiomes.createBiomeTable(randomState, biomeSource, chunkX, chunkZ, minY, height);
        synchronized (biomeTables) {
            biomeTables.put(key, table);
        }
        return table;
    }

    /**
     * {@code ChunkAccess.getNoiseBiome(quartX, quartY, quartZ)} of the chunk containing the quart column, from its
     * BIOMES-status table (quart Y clamped into the chunk, as {@link TerrainChunk#getNoiseBiome} does).
     */
    public String noiseBiome(int quartX, int quartY, int quartZ) {
        return storedBiome(biomeTable(quartX >> 2, quartZ >> 2), quartX, quartY, quartZ, minY, height);
    }

    /** Reads a quart biome table ({@link TerrainChunk#quartBiomesFromNoise} layout) the way {@code ChunkAccess.getNoiseBiome} does. */
    public static String storedBiome(String[] table, int quartX, int quartY, int quartZ, int minY, int height) {
        int quartMinY = minY >> 2;
        int quartMaxY = quartMinY + (height >> 2) - 1;
        int clamped = Math.max(quartMinY, Math.min(quartMaxY, quartY));
        return table[((clamped - quartMinY) * 4 + (quartZ & 3)) * 4 + (quartX & 3)];
    }

    /** The uncached biome source lookup ({@code BiomeSource.createUncachedResolver}): what a level answers for unloaded chunks. */
    public BiomeResolver uncachedResolver() {
        return uncachedResolver;
    }

    // ------------------------------------------------------------------ chunks

    /** BIOMES and TERRAIN of one chunk, in vanilla's order (see the class comment). */
    public TerrainChunk generate(int chunkX, int chunkZ) {
        TerrainChunk chunk = new TerrainChunk(chunkX, chunkZ, minY, height, tags);
        chunk.setQuartBiomes(biomeTable(chunkX, chunkZ));
        chunk.setPersistedStatus(TerrainChunk.Status.BIOMES);
        try (NoiseChunk noiseChunk = VanillaTerrain.beginTerrain(randomState, filler, chunk)) {
            VanillaTerrain.fill(filler, noiseChunk, chunk);
            buildSurface(chunk, noiseChunk);
            generateCarvers(chunk, noiseChunk);
        }
        VanillaTerrain.finishTerrainStatus(chunk);
        return chunk;
    }

    /**
     * {@code NoiseBasedChunkGenerator.buildSurface}: the {@code WorldGenRegion}'s biome manager (stored biomes of the
     * region's chunks; the lookups of one chunk reach at most one quart into its neighbours) and
     * {@code collectPossibleBiomes(region, 1)}. Vanilla's set is the biome palettes of the 3 x 3 chunks; a palette can
     * hold a biome no cell uses any more, but {@code BiomeCondition} only uses the set to replace a lazy test by a
     * constant when the answer is the same at every reachable position, so the set of the biomes actually stored gives
     * the same blocks.
     */
    private void buildSurface(TerrainChunk chunk, NoiseChunk noiseChunk) {
        int cx = chunk.chunkX();
        int cz = chunk.chunkZ();
        Set<String> possible = new HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (String b : biomeTable(cx + dx, cz + dz)) possible.add(b);
            }
        }
        BiomeManager regionBiomes = new BiomeManager(this::noiseBiome, biomeZoomSeed);
        materialSystem.buildSurface(randomState, regionBiomes::getBiome, biomeClimate, context, chunk, noiseChunk.volume(),
                noiseChunk.cachingSamplers(), materialRule, possible);
    }

    /**
     * {@code NoiseBasedChunkGenerator.generateCarvers}: the 17 x 17 source-chunk mask ({@link CarverStep}), then, when the
     * mask is not empty, {@code applyCarvingMask} with {@code biomeManager.withDifferentSource(uncachedResolver)}.
     */
    private void generateCarvers(TerrainChunk chunk, NoiseChunk noiseChunk) {
        CarvingMask mask = CarverStep.computeMask(randomState.seed(), new ChunkPos(chunk.chunkX(), chunk.chunkZ()), context, carvers);
        if (mask.isEmpty()) return;
        BiomeManager carverBiomes = new BiomeManager(uncachedResolver, biomeZoomSeed);
        applyCarvingMask(chunk, mask, noiseChunk, carverBiomes::getBiome);
    }

    /**
     * {@code applyCarvingMask} and its column visitor {@code lambda$applyCarvingMask$0} (26.3 bytecode): per carved column
     * run, {@code y} from the run's top down to its bottom; the "grass or mycelium seen" flag lives for the whole run.
     * <ul>
     *     <li>a block in {@code #minecraft:uncarvable} is kept;</li>
     *     <li>{@code grass_block} or {@code mycelium} sets the flag;</li>
     *     <li>{@code state = aquifer.computeSubstance(x, y, z, 0.0)}; {@code null} keeps the block; otherwise it is set and,
     *     if the aquifer asks for fluid updates and the state has a fluid, marked for post-processing;</li>
     *     <li>with the flag set and {@code dirt} below: {@code topMaterial(rule, randomState, context, biomeGetter, chunk,
     *     cachingSamplers, below, fluidAbove = state has a fluid)} replaces it ({@code lambda$applyCarvingMask$1}: set, and
     *     mark for post-processing when the new state has a fluid).</li>
     * </ul>
     */
    private void applyCarvingMask(TerrainChunk chunk, CarvingMask mask, NoiseChunk noiseChunk, BiomeAt biomeGetter) {
        Aquifer aquifer = noiseChunk.aquifer();
        int minBlockX = chunk.minBlockX();
        int minBlockZ = chunk.minBlockZ();
        mask.visit((localX, localZ, fromY, toY) -> {
            boolean grassAbove = false;
            int x = minBlockX + localX;
            int z = minBlockZ + localZ;
            for (int y = toY; y >= fromY; y--) {
                StateRef current = chunk.getState(x, y, z);
                if (tags.is(current, BlockTags.UNCARVABLE)) continue;
                if (current.is("minecraft:grass_block") || current.is("minecraft:mycelium")) grassAbove = true;
                StateRef state = aquifer.computeSubstance(x, y, z, 0.0);
                if (state == null) continue;
                chunk.setState(x, y, z, state);
                boolean fluid = BlockProperties.hasFluid(state);
                if (aquifer.shouldScheduleFluidUpdate() && fluid) chunk.markPosForPostProcessing(x, y, z);
                if (grassAbove && chunk.getState(x, y - 1, z).is("minecraft:dirt")) {
                    int belowY = y - 1;
                    materialSystem.topMaterial(materialRule, randomState, context, biomeGetter, biomeClimate, chunk,
                            noiseChunk.cachingSamplers(), x, belowY, z, fluid).ifPresent(top -> {
                        chunk.setState(x, belowY, z, top);
                        if (BlockProperties.hasFluid(top)) chunk.markPosForPostProcessing(x, belowY, z);
                    });
                }
            }
        });
    }

    /** Thrown when the gitignored {@code data/} folder (the 26.3 worldgen data) has not been restored. */
    public static final class MissingWorldgenDataException extends IllegalStateException {
        public MissingWorldgenDataException(Path dataRoot) {
            super("The Minecraft 26.3 world generation data is missing from " + dataRoot.toAbsolutePath()
                    + " (data/minecraft/worldgen, data/minecraft/tags). Restore it from your 26.3 client jar with: " + RESTORE_HINT);
        }
    }
}
