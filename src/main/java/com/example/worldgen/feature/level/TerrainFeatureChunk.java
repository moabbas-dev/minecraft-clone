package com.example.worldgen.feature.level;

import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.TerrainChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link FeatureChunk} over a {@link TerrainChunk} (the repo's port of {@code ProtoChunk}): block reads and writes, raw section
 * writes, heightmaps, post-processing marks and the stored biomes come from the chunk; the scheduled fluid ticks of
 * {@code ProtoChunkTicks} are kept here ({@link #fluidTicks()}, in insertion order, one per fluid and position).
 */
public final class TerrainFeatureChunk implements FeatureChunk {

    /** One {@code SavedTick} of {@code ProtoChunkTicks}: the fluid id and the position (delay 0). */
    public record FluidTick(String fluid, int x, int y, int z) {
    }

    private final TerrainChunk chunk;
    /** One scheduled block tick: the block id, the position and the delay. */
    public record BlockTick(String block, int x, int y, int z, int delay) {
    }

    private final List<FluidTick> fluidTicks = new ArrayList<>();
    private final List<BlockTick> blockTicks = new ArrayList<>();
    private final Set<FluidTick> fluidTickSet = new HashSet<>();

    public TerrainFeatureChunk(TerrainChunk chunk) {
        this.chunk = chunk;
    }

    public TerrainChunk chunk() {
        return chunk;
    }

    /** The fluid ticks scheduled during decoration, in scheduling order. */
    public List<FluidTick> fluidTicks() {
        return Collections.unmodifiableList(fluidTicks);
    }

    @Override
    public int chunkX() {
        return chunk.chunkX();
    }

    @Override
    public int chunkZ() {
        return chunk.chunkZ();
    }

    @Override
    public StateRef getBlockState(int x, int y, int z) {
        return chunk.getState(x, y, z);
    }

    @Override
    public void setBlockState(int x, int y, int z, StateRef state) {
        chunk.setState(x, y, z, state);
    }

    @Override
    public StateRef getSectionBlockState(int x, int y, int z) {
        if (chunk.isOutsideBuildHeight(y)) throw new IllegalArgumentException("y outside the chunk sections: " + y);
        return chunk.getState(x, y, z);
    }

    @Override
    public void setSectionBlockState(int x, int y, int z, StateRef state) {
        if (chunk.isOutsideBuildHeight(y)) throw new IllegalArgumentException("y outside the chunk sections: " + y);
        BlockProperties.entry(state.id());
        chunk.setStateInSection(x, y, z, state);
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        return chunk.getHeight(type, x, z);
    }

    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        chunk.markPosForPostProcessing(x, y, z);
    }

    @Override
    public void scheduleFluidTick(int x, int y, int z, String fluid) {
        FluidTick tick = new FluidTick(fluid, x, y, z);
        if (fluidTickSet.add(tick)) fluidTicks.add(tick);
    }

    /** The block ticks scheduled during decoration, in scheduling order. */
    public List<BlockTick> blockTicks() {
        return Collections.unmodifiableList(blockTicks);
    }

    @Override
    public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        blockTicks.add(new BlockTick(block, x, y, z, delay));
    }

    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        return chunk.getNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public void forEachStoredBiome(Consumer<String> consumer) {
        for (String biome : chunk.quartBiomes()) consumer.accept(biome);
    }
}
