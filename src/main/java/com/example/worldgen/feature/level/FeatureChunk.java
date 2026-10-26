package com.example.worldgen.feature.level;

import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;

import java.util.function.Consumer;

/**
 * What the FEATURES status reads and writes on one chunk of its region: the parts of vanilla's {@code ChunkAccess} /
 * {@code ProtoChunk} (26.3) that {@code WorldGenRegion}, {@code BulkSectionAccess} and the features call. All coordinates
 * are world coordinates. {@link TerrainFeatureChunk} implements it over a {@code TerrainChunk}; the engine can implement
 * it over its own chunk storage as long as every method keeps the vanilla behaviour stated here.
 */
public interface FeatureChunk {

    int chunkX();

    int chunkZ();

    /** {@code ProtoChunk.getBlockState(pos)}: {@code minecraft:void_air} outside the build height, air in an all-air section. */
    StateRef getBlockState(int x, int y, int z);

    /**
     * {@code ProtoChunk.setBlockState(pos, state, flags)}: outside the build height nothing happens; a write of
     * {@code minecraft:air} into an all-air section is dropped; otherwise the section is written and every heightmap of the
     * persisted status' {@code heightmapsAfter()} is updated (FEATURES runs on chunks at status TERRAIN, which keep
     * {@code OCEAN_FLOOR, WORLD_SURFACE, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES}).
     */
    void setBlockState(int x, int y, int z, StateRef state);

    /**
     * {@code LevelChunkSection.getBlockState(x & 15, y & 15, z & 15)} of the section holding {@code y} (the caller checks
     * that {@code y} is inside the build height, as {@code BulkSectionAccess.getSection} does).
     */
    StateRef getSectionBlockState(int x, int y, int z);

    /**
     * {@code LevelChunkSection.setBlockState(x & 15, y & 15, z & 15, state, false)}: the raw section write
     * {@code OreFeature} makes through {@code BulkSectionAccess} (non-air count kept, no heightmap update).
     */
    void setSectionBlockState(int x, int y, int z, StateRef state);

    /** {@code ChunkAccess.getHeight(type, x & 15, z & 15)}: the highest block of the column that counts for {@code type}. */
    int getHeight(HeightmapType type, int x, int z);

    /** {@code ProtoChunk.markPosForPostProcessing(pos)} (ignored outside the build height). */
    void markPosForPostProcessing(int x, int y, int z);

    /**
     * {@code ProtoChunk.getFluidTicks().schedule(tick)} ({@code ProtoChunkTicks}): a {@code SavedTick(type, pos, 0,
     * priority)} that is added only when no tick of the same type at the same position is stored yet.
     */
    void scheduleFluidTick(int x, int y, int z, String fluid);

    /**
     * {@code ProtoChunk.getBlockTicks().schedule(tick)} ({@code ProtoChunkTicks}): the block id, position and delay of a
     * scheduled block tick (see {@code GenRegion.scheduleBlockTick}). A chunk that does not store block ticks keeps this
     * default, which stops the calling top-level feature as an unported path ({@code UnportedFeature.Reached}) instead of
     * dropping the tick.
     */
    default void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        throw new com.example.worldgen.feature.UnportedFeature.Reached("block ticks are not stored by " + getClass().getSimpleName());
    }

    /** {@code ChunkAccess.getNoiseBiome(quartX, quartY, quartZ)} (quart Y clamped into the chunk). */
    String getNoiseBiome(int quartX, int quartY, int quartZ);

    /** Every biome stored in the chunk's sections ({@code LevelChunkSection.getBiomes().getAll(consumer)} of each section). */
    void forEachStoredBiome(Consumer<String> consumer);
}
