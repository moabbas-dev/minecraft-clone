package com.example.world;

import java.util.List;

/** Fills a single chunk's blocks. Implementations: the vanilla overworld ({@link VanillaChunkGenerator}), superflat. */
public interface WorldGenerator {
    void generate(Chunk chunk, World world);

    /**
     * Stores the chunk's vanilla quart biomes ({@link Chunk#setQuartBiomes}) - called after {@link #generate} and after a chunk was
     * restored from disk (biomes are not saved; they are recomputed from the seed). Generators without biomes leave them null.
     */
    default void computeBiomes(Chunk chunk) {
    }

    /** Whether this generator has vanilla biomes ({@link #computeBiomes}, {@link #noiseBiome}). */
    default boolean hasBiomes() {
        return false;
    }

    /**
     * The vanilla biome id stored at a quart cell of a chunk that is not loaded (what that chunk's BIOMES status stores), or null for
     * a generator without biomes.
     */
    default String noiseBiome(int quartX, int quartY, int quartZ) {
        return null;
    }

    /** {@code BiomeManager.obfuscateSeed(seed)} of a generator with biomes. */
    default long biomeZoomSeed() {
        return 0L;
    }

    /**
     * Fills the chunk's grass/foliage/water tint cache. Called after {@link #computeBiomes} on both the generate and the load path
     * (see {@link World#generateChunk}) - tint is not persisted.
     */
    void computeTint(Chunk chunk, World world);

    /**
     * Positions (world x, y, z) the last {@link #generate} of {@code chunk} marked for post-processing (vanilla
     * {@code ProtoChunk.markPosForPostProcessing}), handed over once; empty when there are none.
     */
    default List<int[]> takePostProcessing(Chunk chunk) {
        return List.of();
    }

    /** Climate at one world column, for the debug overlay - null if this generator has no climate model (flat and vanilla worlds). */
    default ClimateSample climateAt(int worldX, int worldZ) {
        return null;
    }
}
