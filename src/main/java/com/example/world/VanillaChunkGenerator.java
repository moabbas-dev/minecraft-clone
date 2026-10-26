package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.chunk.OverworldSpawn;
import com.example.worldgen.feature.BiomeDecoration;
import com.example.worldgen.feature.FeatureLoader;
import com.example.worldgen.feature.PlacedFeature;
import com.example.worldgen.feature.UnportedFeature;
import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.level.TerrainFeatureChunk;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.TerrainChunk;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.biome.OverworldBiomeBuilder;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The engine side of the vanilla 26.3 overworld generator ({@link OverworldGenerator} and the feature decoration of
 * {@link BiomeDecoration}). A chunk goes through two statuses before the engine sees it:
 * <ol>
 *     <li>TERRAIN (BIOMES and TERRAIN: noise fill, aquifers, surface rules, ore veins, carvers): a {@link TerrainProto} kept here,
 *     invisible to the engine ({@code World.getBlock} answers air for it like for any chunk that is not loaded);</li>
 *     <li>FEATURES ({@link #generate}): {@code applyBiomeDecoration} on a {@link GenRegion} centred on the chunk; the 3 x 3 chunks
 *     around it are writable, chunks up to distance 8 readable. The engine (World) makes sure the 5 x 5 chunks around the centre
 *     are at least TERRAIN first ({@link #dependencyRadius}), so every read a feature can make within distance 2 sees a chunk with
 *     its terrain (a valid vanilla outcome: vanilla only guarantees TERRAIN at distance 1). A region chunk is the engine chunk when
 *     it is FULL ({@link EngineFeatureChunk}), else its proto, else a chunk saved on disk (loaded into the world), else within
 *     distance 2 a new proto, beyond that an empty chunk as a STRUCTURE_STARTS chunk would be ({@link EmptyFeatureChunk}). The
 *     decorated proto is then copied into the engine {@link Chunk}, which becomes FULL.</li>
 * </ol>
 * Writes into a neighbouring proto are journaled ({@link TerrainProto}): an evicted proto that received writes keeps its journal
 * in memory, or on disk for a saved world ({@link WorldIO#writeDecorationJournal}), and is rebuilt by generating its terrain
 * again and replaying the journal.
 * <ul>
 *     <li>features: the top-level placed features whose type is in {@link #ENGINE_FEATURE_TYPES} run; every other one is skipped
 *     exactly as {@link UnportedFeature} is (its feature seed is still drawn, nothing else), because the engine has no blocks yet for
 *     what they write;</li>
 *     <li>blocks: each distinct vanilla state is converted once through {@link VanillaBlocks#byVanilla}; a state the engine has no
 *     block for stops generation with {@link VanillaBlocks.UnsupportedBlockStateException} (never substituted);</li>
 *     <li>biomes: the chunk's quart biome table, recomputed from the seed for chunks loaded from disk;</li>
 *     <li>post-processing and fluid ticks: the positions the terrain stages and the features marked, and the fluid ticks the
 *     features scheduled ({@code ProtoChunkTicks}), are handed to {@link World};</li>
 *     <li>tint: vanilla's block tint ({@code ClientLevel.calculateBlockTint}) per column, see {@link BiomeTints};</li>
 *     <li>spawn: {@link #findSpawn} ({@link OverworldSpawn}).</li>
 * </ul>
 * One instance per world, used from the game thread.
 */
public final class VanillaChunkGenerator implements WorldGenerator {

    /** The worldgen data folder (gitignored, restored from the user's client jar by {@code scripts/restore-worldgen-data.py}). */
    public static final Path DATA_ROOT = Path.of("data");

    /** The feature types whose blocks the engine has: the top-level placed features of other types are skipped. */
    public static final Set<String> ENGINE_FEATURE_TYPES = Set.of("minecraft:ore", "minecraft:disk", "minecraft:spring_feature",
            "minecraft:underwater_magma");

    /** The 5 x 5 rule: chunks within this chessboard distance of a chunk are at least TERRAIN before it is decorated. */
    public static final int TERRAIN_RADIUS = 2;

    private final OverworldGenerator overworld;
    private final BiomeDecoration decoration;
    /** Chunks at status TERRAIN (with the writes of neighbouring decorations), keyed by {@link Chunk#key}. */
    private final Map<Long, TerrainProto> protos = new HashMap<>();
    /** Journals of evicted protos of a world that is not saved (a saved world keeps them on disk). */
    private final Map<Long, List<String>> evictedJournals = new HashMap<>();
    private final Map<Long, List<int[]>> postProcessing = new HashMap<>();
    private final Map<Long, List<int[]>> fluidTicks = new HashMap<>();
    private final Map<StateRef, BlockType> blockTypes = new HashMap<>();
    private final BiomeTints tints;
    private final OverworldBiomeBuilder debugBiomeBuilder = new OverworldBiomeBuilder();

    public VanillaChunkGenerator(long seed) {
        this(DATA_ROOT, seed);
    }

    /** @throws OverworldGenerator.MissingWorldgenDataException when the data folder is missing (the message names the restore script) */
    public VanillaChunkGenerator(Path dataRoot, long seed) {
        this.overworld = new OverworldGenerator(dataRoot, seed);
        this.tints = new BiomeTints(overworld::biome, overworld.biomeNoises());
        FeatureLoader loader = new FeatureLoader(dataRoot, overworld.tags(), overworld.biomeNoises());
        Map<String, PlacedFeature> gated = new HashMap<>();
        java.util.function.Function<String, PlacedFeature> placed = id -> gated.computeIfAbsent(id, k -> {
            PlacedFeature p = loader.placedFeature(k);
            if (!p.feature().isPorted() || ENGINE_FEATURE_TYPES.contains(p.feature().type())) return p;
            return new PlacedFeature(p.id(), new UnportedFeature(p.feature().type(), null), List.of(), p.rawPlacement());
        });
        this.decoration = new BiomeDecoration(overworld.possibleBiomes(), overworld::biome, placed, overworld.minY(),
                overworld.height(), overworld.seaLevel());
    }

    public OverworldGenerator overworld() {
        return overworld;
    }

    /** The decoration loop this generator runs (with the {@link #ENGINE_FEATURE_TYPES} gate). */
    public BiomeDecoration decoration() {
        return decoration;
    }

    /** {@code noise_settings.sea_level} of the overworld settings (63). */
    @Override
    public int seaLevel() {
        return overworld.seaLevel();
    }

    // ------------------------------------------------------------------ TERRAIN protos

    private static long key(int cx, int cz) {
        return Chunk.key(cx, cz);
    }

    @Override
    public int dependencyRadius() {
        return TERRAIN_RADIUS;
    }

    @Override
    public boolean hasTerrain(int cx, int cz) {
        return protos.containsKey(key(cx, cz));
    }

    @Override
    public boolean ensureTerrain(int cx, int cz, File saveDir) {
        if (protos.containsKey(key(cx, cz))) return false;
        proto(cx, cz, saveDir);
        return true;
    }

    /** The chunk's TERRAIN proto: generated (and its saved journal replayed) when it does not exist. */
    private TerrainProto proto(int cx, int cz, File saveDir) {
        long k = key(cx, cz);
        TerrainProto p = protos.get(k);
        if (p != null) return p;
        p = new TerrainProto(overworld.generate(cx, cz));
        List<String> journal = evictedJournals.remove(k);
        if (journal == null && saveDir != null) journal = WorldIO.readDecorationJournal(saveDir, cx, cz);
        if (journal != null) p.replay(journal);
        protos.put(k, p);
        return p;
    }

    /** Number of TERRAIN protos held (tests and the debug state). */
    public int terrainProtoCount() {
        return protos.size();
    }

    @Override
    public void evictTerrain(int centerX, int centerZ, int radius, File saveDir) {
        protos.entrySet().removeIf(e -> {
            TerrainProto p = e.getValue();
            if (Math.max(Math.abs(p.chunkX() - centerX), Math.abs(p.chunkZ() - centerZ)) <= radius) return false;
            keepJournal(p, saveDir);
            return true;
        });
    }

    private void keepJournal(TerrainProto p, File saveDir) {
        if (p.journal().isEmpty()) return;
        if (saveDir != null) WorldIO.writeDecorationJournal(saveDir, p.chunkX(), p.chunkZ(), p.journal());
        else evictedJournals.put(key(p.chunkX(), p.chunkZ()), new ArrayList<>(p.journal()));
    }

    @Override
    public void saveGenerationState(File saveDir) {
        if (saveDir == null) return;
        for (TerrainProto p : protos.values()) keepJournal(p, saveDir);
        for (Map.Entry<Long, List<String>> e : evictedJournals.entrySet()) {
            WorldIO.writeDecorationJournal(saveDir, (int) e.getKey().longValue(), (int) (e.getKey() >> 32), e.getValue());
        }
        evictedJournals.clear();
    }

    // ------------------------------------------------------------------ FEATURES

    /**
     * Decorates chunk {@code (chunk.chunkX, chunk.chunkZ)} and copies it into {@code chunk}. The world supplies the FULL neighbours
     * and has normally made the 5 x 5 neighbourhood TERRAIN already ({@code World.generateChunk}).
     */
    @Override
    public void generate(Chunk chunk, World world) {
        int cx = chunk.chunkX, cz = chunk.chunkZ;
        File saveDir = world != null ? world.getSaveDir() : null;
        TerrainProto centre = proto(cx, cz, saveDir);
        Map<Long, FeatureChunk> region = new HashMap<>();
        GenRegion.ChunkLookup lookup = (x, z) -> region.computeIfAbsent(key(x, z), k -> regionChunk(world, x, z, cx, cz, saveDir));
        GenRegion level = new GenRegion(overworld.seed(), cx, cz, lookup, overworld.tags(), overworld.minY(), overworld.height(),
                overworld.seaLevel());
        decoration.applyBiomeDecoration(level, null);

        TerrainChunk terrain = centre.terrain();
        copyBlocks(terrain, chunk);
        chunk.setQuartBiomes(terrain.quartBiomes());
        checkHeightmaps(terrain, chunk);
        List<int[]> marks = terrain.postProcessingPositions();
        if (!marks.isEmpty()) postProcessing.put(key(cx, cz), marks);
        TerrainFeatureChunk features = centre.featureChunk();
        if (!features.blockTicks().isEmpty()) {
            throw new IllegalStateException("decoration of chunk " + cx + "," + cz + " scheduled block ticks " + features.blockTicks()
                    + ": the engine has no scheduled block ticks");
        }
        List<int[]> ticks = new ArrayList<>();
        for (TerrainFeatureChunk.FluidTick t : features.fluidTicks()) ticks.add(new int[]{t.x(), t.y(), t.z()});
        if (!ticks.isEmpty()) fluidTicks.put(key(cx, cz), ticks);
        protos.remove(key(cx, cz));
        evictedJournals.remove(key(cx, cz));
        if (saveDir != null) WorldIO.deleteDecorationJournal(saveDir, cx, cz);
    }

    private FeatureChunk regionChunk(World world, int x, int z, int cx, int cz, File saveDir) {
        if (world != null) {
            Chunk c = world.getChunk(x, z);
            if (c != null && c.isGenerated()) return new EngineFeatureChunk(world, c, overworld.tags(), blockTypes);
        }
        TerrainProto p = protos.get(key(x, z));
        if (p != null) return p;
        if (world != null) {
            Chunk loaded = world.loadSavedChunk(x, z);
            if (loaded != null) return new EngineFeatureChunk(world, loaded, overworld.tags(), blockTypes);
        }
        if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= TERRAIN_RADIUS) return proto(x, z, saveDir);
        return new EmptyFeatureChunk(x, z, overworld.minY());
    }

    /**
     * The four final heightmaps of the decorated proto must equal what {@link EngineFeatureChunk} scans from the copied blocks (the
     * premise of keeping no per-type heightmaps in the engine, see that class); a difference stops generation.
     */
    private void checkHeightmaps(TerrainChunk terrain, Chunk chunk) {
        EngineFeatureChunk scan = new EngineFeatureChunk(null, chunk, overworld.tags(), blockTypes);
        for (HeightmapType type : TerrainChunk.FINAL_HEIGHTMAPS) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int expected = terrain.getHeight(type, lx, lz);
                    int scanned = scan.scanHighest(type, lx, lz);
                    if (expected != scanned) {
                        throw new IllegalStateException("chunk " + chunk.chunkX + "," + chunk.chunkZ + " column " + lx + "," + lz + ": " + type
                                + " heightmap " + expected + " differs from the blocks (" + scanned + ")");
                    }
                }
            }
        }
    }

    private void copyBlocks(TerrainChunk terrain, Chunk chunk) {
        BlockType[] byIndex = new BlockType[terrain.paletteSize()];
        for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    int index = terrain.paletteIndexLocal(x, y, z);
                    BlockType type = byIndex[index];
                    if (type == null) {
                        type = engineBlock(terrain.paletteState(index), chunk);
                        byIndex[index] = type;
                    }
                    if (!type.isAir()) chunk.setLocal(x, y, z, type);
                }
            }
        }
    }

    /** Copies the TERRAIN status of a chunk (no decoration) into an engine chunk: the copy step alone, for tests. */
    void copyTerrainOnly(Chunk chunk) {
        TerrainChunk terrain = overworld.generate(chunk.chunkX, chunk.chunkZ);
        copyBlocks(terrain, chunk);
        chunk.setQuartBiomes(terrain.quartBiomes());
    }

    private BlockType engineBlock(StateRef state, Chunk chunk) {
        BlockType type = blockTypes.get(state);
        if (type != null) return type;
        try {
            type = VanillaBlocks.byVanilla(state.id(), state.properties());
        } catch (VanillaBlocks.UnsupportedBlockStateException e) {
            throw new VanillaBlocks.UnsupportedBlockStateException(state.id(), state.properties(),
                    "generated in chunk " + chunk.chunkX + "," + chunk.chunkZ + " and the engine has no block for it (" + e.getMessage() + ")");
        }
        blockTypes.put(state, type);
        return type;
    }

    @Override
    public List<int[]> takePostProcessing(Chunk chunk) {
        List<int[]> marks = postProcessing.remove(key(chunk.chunkX, chunk.chunkZ));
        return marks == null ? List.of() : marks;
    }

    @Override
    public List<int[]> takeFluidTicks(Chunk chunk) {
        List<int[]> ticks = fluidTicks.remove(key(chunk.chunkX, chunk.chunkZ));
        return ticks == null ? List.of() : ticks;
    }

    // ------------------------------------------------------------------ biomes

    @Override
    public boolean hasBiomes() {
        return true;
    }

    @Override
    public void computeBiomes(Chunk chunk) {
        if (!chunk.hasBiomes()) chunk.setQuartBiomes(overworld.biomeTable(chunk.chunkX, chunk.chunkZ).clone());
    }

    @Override
    public String noiseBiome(int quartX, int quartY, int quartZ) {
        return overworld.noiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public long biomeZoomSeed() {
        return overworld.biomeZoomSeed();
    }

    // ------------------------------------------------------------------ tint

    /** Vanilla's block tint (see {@link BiomeTints#computeTint}) from this generator's biome files. */
    @Override
    public void computeTint(Chunk chunk, World world) {
        tints.computeTint(chunk, world);
    }

    // ------------------------------------------------------------------ biome data

    @Override
    public BiomeDefinition biomeDefinition(String biomeId) {
        return overworld.biome(biomeId);
    }

    @Override
    public BiomeDefinition.Noises biomeNoises() {
        return overworld.biomeNoises();
    }

    /**
     * The F3 "chunk_generation" group of {@code DebugEntryChunkGeneration} at the feet: {@code NoiseBasedChunkGenerator.addDebugScreenInfo}
     * ("Density " + each {@code debug_functions} entry of the noise settings as {@code label: value}, formatted {@code "0.000"} in
     * {@code Locale.ROOT}) and {@code MultiNoiseBiomeSource.addDebugInfo} (the climate at the feet's quart cell, unquantized, through
     * {@code OverworldBiomeBuilder}'s debug strings).
     */
    @Override
    public List<String> chunkGenerationDebugLines(int x, int y, int z) {
        SamplerContext context = SamplerContext.EMPTY_UNCACHED;
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        List<NoiseGeneratorSettings.DebugFunctionEntry> functions = overworld.settings().debugFunctions();
        if (!functions.isEmpty()) {
            DensitySamplerSet samplers = overworld.randomState().samplersWithContext(context);
            StringBuilder builder = new StringBuilder("Density ");
            java.text.DecimalFormat format = new java.text.DecimalFormat("0.000",
                    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
            for (NoiseGeneratorSettings.DebugFunctionEntry entry : functions) {
                builder.append(entry.label()).append(": ");
                builder.append(format.format((double) samplers.sampleValue(entry.function(), x, y, z)));
                builder.append(' ');
            }
            builder.deleteCharAt(builder.length() - 1);
            lines.add(builder.toString());
        }
        Climate.Sampler sampler = overworld.randomState().createClimateSampler(context);
        Climate.TargetPoint p = sampler.sample(x >> 2, y >> 2, z >> 2);
        float continentalness = Climate.unquantizeCoord(p.continentalness());
        float erosion = Climate.unquantizeCoord(p.erosion());
        float temperature = Climate.unquantizeCoord(p.temperature());
        float humidity = Climate.unquantizeCoord(p.humidity());
        float weirdness = Climate.unquantizeCoord(p.weirdness());
        double peaksAndValleys = OverworldBiomeBuilder.peaksAndValleys(weirdness); // NoiseRouterData.peaksAndValleys
        OverworldBiomeBuilder builder = debugBiomeBuilder;
        lines.add("Biome builder PV: " + OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(peaksAndValleys)
                + " C: " + builder.getDebugStringForContinentalness(continentalness)
                + " E: " + builder.getDebugStringForErosion(erosion)
                + " T: " + builder.getDebugStringForTemperature(temperature)
                + " H: " + builder.getDebugStringForHumidity(humidity));
        return lines;
    }

    // ------------------------------------------------------------------ spawn

    /**
     * The spawn of a new world ({@link OverworldSpawn}): the world spawn ({@code MinecraftServer.setInitialSpawn}), then where the
     * player appears around it ({@code PlayerSpawnFinder.findSpawn} with the default {@code respawn_radius} 10 and vanilla's unseeded
     * thread-local random), as {@code {x, y, z}} of the player's feet. The terrain chunks it generates stay as TERRAIN protos for the
     * world to decorate.
     */
    public double[] findSpawn() {
        OverworldSpawn.Chunks chunks = (cx, cz) -> proto(cx, cz, null).terrain();
        OverworldSpawn.BlockPos worldSpawn = OverworldSpawn.worldSpawn(overworld, chunks);
        double[] player = OverworldSpawn.playerSpawn(overworld, chunks, worldSpawn, OverworldSpawn.RESPAWN_RADIUS,
                RandomSource.createThreadLocalInstance());
        // Keep only what the world will load right away (the spawn's neighbourhood); a far spiral search must not pin chunks.
        int pcx = Math.floorDiv((int) Math.floor(player[0]), 16);
        int pcz = Math.floorDiv((int) Math.floor(player[2]), 16);
        evictTerrain(pcx, pcz, 2 * TERRAIN_RADIUS + 1, null);
        return player;
    }
}
