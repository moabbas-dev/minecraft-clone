package com.example.world;

/** Fills a single chunk's blocks. Implementations: normal terrain, superflat, etc. */
public interface WorldGenerator {
    void generate(Chunk chunk, World world);

    /**
     * Fills the chunk's grass/foliage tint cache. Called unconditionally after either
     * {@link #generate} or a chunk restored from disk (see {@link World#generateChunk}) - tint
     * is cheap to (re)compute from noise alone and isn't persisted, so both paths need it.
     */
    void computeTint(Chunk chunk);

    /** Climate at one world column, for the debug overlay - null if this generator has no climate model (e.g. flat worlds). */
    default ClimateSample climateAt(int worldX, int worldZ) {
        return null;
    }
}
