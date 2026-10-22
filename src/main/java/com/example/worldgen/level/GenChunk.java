package com.example.worldgen.level;

/**
 * What the world generation stages (terrain fill, surface rules, carvers, features) need from the chunk they work on, in vanilla's own
 * terms ({@code ChunkAccess}: block state read/write by world coordinates, heightmaps, the vertical range). The stages are written
 * against this interface so they can be compared with the real game state for state; the engine's {@code Chunk} is filled from a
 * finished {@code GenChunk} at the boundary.
 *
 * <p>All x and z are world coordinates inside the chunk (chunkX * 16 .. chunkX * 16 + 15); all y are real world Y (negative included).
 */
public interface GenChunk {
    int chunkX();

    int chunkZ();

    /** Lowest world Y of the chunk (-64 in the overworld). */
    int minY();

    /** Number of Y levels (384 in the overworld). */
    int height();

    default int maxY() {
        return minY() + height() - 1;
    }

    default int minBlockX() {
        return chunkX() * 16;
    }

    default int minBlockZ() {
        return chunkZ() * 16;
    }

    StateRef getState(int x, int y, int z);

    /** Writes a block state and keeps the heightmaps of the chunk up to date as vanilla's {@code setBlockState} does. */
    void setState(int x, int y, int z, StateRef state);

    /**
     * Vanilla {@code ChunkAccess.getHeight(type, x, z)} = {@code heightmap.getFirstAvailable(x & 15, z & 15) - 1}: the Y of the
     * highest block that counts for {@code type} in that column ({@code minY() - 1} when there is none).
     */
    int getHeight(HeightmapType type, int x, int z);

    /** The six heightmap types of vanilla's {@code Heightmap.Types}. */
    enum HeightmapType {
        WORLD_SURFACE_WG, WORLD_SURFACE, OCEAN_FLOOR_WG, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES
    }
}
