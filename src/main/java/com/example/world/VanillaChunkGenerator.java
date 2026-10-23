package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.chunk.OverworldSpawn;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.TerrainChunk;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.biome.OverworldBiomeBuilder;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The engine side of the vanilla 26.3 overworld generator ({@link OverworldGenerator}): every chunk is generated through the BIOMES
 * and TERRAIN statuses (noise fill, aquifers, surface rules, ore veins, carvers and the carving mask) and copied into the engine
 * {@link Chunk}; features (ores, vegetation, trees), structures and mobs are not generated yet.
 * <ul>
 *     <li>blocks: each distinct vanilla state of a chunk's palette is converted once through {@link VanillaBlocks#byVanilla}; a state
 *     the engine has no block for stops generation with {@link VanillaBlocks.UnsupportedBlockStateException} (never substituted);</li>
 *     <li>biomes: the chunk's quart biome table, recomputed from the seed for chunks loaded from disk;</li>
 *     <li>post-processing: the positions the aquifer and the surface rules marked are handed to {@link World} (fluid ticks once the
 *     chunk's neighbours exist, see {@code World.runPostProcessingIfReady});</li>
 *     <li>tint: vanilla's block tint ({@code ClientLevel.calculateBlockTint}) per column, see {@link BiomeTints};</li>
 *     <li>spawn: {@link #findSpawn} ({@link OverworldSpawn}).</li>
 * </ul>
 * One instance per world, used from the game thread.
 */
public final class VanillaChunkGenerator implements WorldGenerator {

    /** The worldgen data folder (gitignored, restored from the user's client jar by {@code scripts/restore-worldgen-data.py}). */
    public static final Path DATA_ROOT = Path.of("data");

    private final OverworldGenerator overworld;
    /** Generated terrain chunks not copied into the engine yet (the spawn search generates a few before the world streams them). */
    private final Map<Long, TerrainChunk> generatedAhead = new HashMap<>();
    private final Map<Long, List<int[]>> postProcessing = new HashMap<>();
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
    }

    public OverworldGenerator overworld() {
        return overworld;
    }

    /** {@code noise_settings.sea_level} of the overworld settings (63). */
    @Override
    public int seaLevel() {
        return overworld.seaLevel();
    }

    // ------------------------------------------------------------------ blocks

    private static long key(int cx, int cz) {
        return Chunk.key(cx, cz);
    }

    private TerrainChunk terrain(int cx, int cz) {
        TerrainChunk t = generatedAhead.get(key(cx, cz));
        return t != null ? t : generatedAhead.computeIfAbsent(key(cx, cz), k -> overworld.generate(cx, cz));
    }

    @Override
    public void generate(Chunk chunk, World world) {
        TerrainChunk terrain = generatedAhead.remove(key(chunk.chunkX, chunk.chunkZ));
        if (terrain == null) terrain = overworld.generate(chunk.chunkX, chunk.chunkZ);
        copyBlocks(terrain, chunk);
        chunk.setQuartBiomes(terrain.quartBiomes());
        List<int[]> marks = terrain.postProcessingPositions();
        if (!marks.isEmpty()) postProcessing.put(key(chunk.chunkX, chunk.chunkZ), marks);
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
     * thread-local random), as {@code {x, y, z}} of the player's feet. The terrain chunks it generates are kept for the world to use.
     */
    public double[] findSpawn() {
        OverworldSpawn.Chunks chunks = this::terrain;
        OverworldSpawn.BlockPos worldSpawn = OverworldSpawn.worldSpawn(overworld, chunks);
        double[] player = OverworldSpawn.playerSpawn(overworld, chunks, worldSpawn, OverworldSpawn.RESPAWN_RADIUS,
                RandomSource.createThreadLocalInstance());
        // Keep only what the world will load right away (the spawn's neighbourhood); a far spiral search must not pin chunks.
        int pcx = Math.floorDiv((int) Math.floor(player[0]), 16);
        int pcz = Math.floorDiv((int) Math.floor(player[2]), 16);
        generatedAhead.keySet().removeIf(k -> {
            int cx = (int) k.longValue();
            int cz = (int) (k >> 32);
            return Math.abs(cx - pcx) > 2 || Math.abs(cz - pcz) > 2;
        });
        return player;
    }
}
