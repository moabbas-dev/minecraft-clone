package com.example.world;

import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;

import java.util.function.Consumer;

/**
 * A region chunk farther than the 5 x 5 rule's radius that exists nowhere yet (not FULL, no proto, not on disk): what a
 * {@code ProtoChunk} at STRUCTURE_STARTS answers in vanilla, the status the FEATURES step guarantees out to distance 8. Its
 * sections are empty, so every block reads air ({@code void_air} outside the build height) and every heightmap is empty. The
 * FEATURES step can neither write to it (write radius 1) nor read its biomes (radius 1), so those calls are errors.
 */
final class EmptyFeatureChunk implements FeatureChunk {

    private final int chunkX;
    private final int chunkZ;
    private final int minY;

    EmptyFeatureChunk(int chunkX, int chunkZ, int minY) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
    }

    @Override
    public int chunkX() {
        return chunkX;
    }

    @Override
    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public StateRef getBlockState(int x, int y, int z) {
        return Chunk.inWorld(y) ? BlockProperties.AIR : BlockProperties.VOID_AIR;
    }

    @Override
    public StateRef getSectionBlockState(int x, int y, int z) {
        return BlockProperties.AIR;
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        return minY - 1;
    }

    private IllegalStateException notWritable() {
        return new IllegalStateException("chunk " + chunkX + "," + chunkZ + " is at STRUCTURE_STARTS and cannot be written by FEATURES");
    }

    @Override
    public void setBlockState(int x, int y, int z, StateRef state) {
        throw notWritable();
    }

    @Override
    public void setSectionBlockState(int x, int y, int z, StateRef state) {
        throw notWritable();
    }

    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        throw notWritable();
    }

    @Override
    public void scheduleFluidTick(int x, int y, int z, String fluid) {
        throw notWritable();
    }

    @Override
    public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        throw notWritable();
    }

    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        throw new IllegalStateException("chunk " + chunkX + "," + chunkZ + " has no biomes at STRUCTURE_STARTS");
    }

    @Override
    public void forEachStoredBiome(Consumer<String> consumer) {
        throw new IllegalStateException("chunk " + chunkX + "," + chunkZ + " has no biomes at STRUCTURE_STARTS");
    }
}
