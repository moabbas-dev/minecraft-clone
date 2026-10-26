package com.example.world;

import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.TerrainFeatureChunk;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.TerrainChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A chunk at status TERRAIN that is not decorated yet (vanilla's {@code ProtoChunk} between TERRAIN and FEATURES): the worldgen
 * {@link TerrainFeatureChunk} plus a journal of every write the decoration of a neighbouring chunk made into it (block writes,
 * raw section writes, post-processing marks, fluid and block ticks, in call order).
 *
 * <p>The terrain itself is a pure function of the seed and the position, so a proto without journal entries can be dropped and
 * generated again at any time. The journal is what makes a proto that received writes recoverable too: generating the terrain
 * again and replaying the journal through the same {@link FeatureChunk} methods gives the identical chunk (blocks, heightmaps,
 * marks and ticks), which is how the engine keeps such a chunk across eviction and across a save without saving a TERRAIN
 * chunk ({@code VanillaChunkGenerator}, {@code WorldIO.writeDecorationJournal}).
 */
final class TerrainProto implements FeatureChunk {

    private final TerrainFeatureChunk chunk;
    private final List<String> journal = new ArrayList<>();

    TerrainProto(TerrainChunk terrain) {
        this.chunk = new TerrainFeatureChunk(terrain);
    }

    TerrainChunk terrain() {
        return chunk.chunk();
    }

    TerrainFeatureChunk featureChunk() {
        return chunk;
    }

    /** The writes made into this chunk so far, one line per call ({@link #replay} reads them back). */
    List<String> journal() {
        return Collections.unmodifiableList(journal);
    }

    /** Applies journal lines (recorded by an earlier proto of the same chunk) in order; they become part of this journal. */
    void replay(List<String> lines) {
        for (String line : lines) {
            String[] p = line.split(" ", 6);
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            switch (p[0]) {
                case "S" -> setBlockState(x, y, z, StateRef.parse(p[4]));
                case "B" -> setSectionBlockState(x, y, z, StateRef.parse(p[4]));
                case "M" -> markPosForPostProcessing(x, y, z);
                case "T" -> scheduleFluidTick(x, y, z, p[4]);
                case "K" -> scheduleBlockTick(x, y, z, p[4], Integer.parseInt(p[5]));
                default -> throw new IllegalArgumentException("bad decoration journal line: " + line);
            }
        }
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
        return chunk.getBlockState(x, y, z);
    }

    @Override
    public void setBlockState(int x, int y, int z, StateRef state) {
        chunk.setBlockState(x, y, z, state);
        journal.add("S " + x + " " + y + " " + z + " " + state);
    }

    @Override
    public StateRef getSectionBlockState(int x, int y, int z) {
        return chunk.getSectionBlockState(x, y, z);
    }

    @Override
    public void setSectionBlockState(int x, int y, int z, StateRef state) {
        chunk.setSectionBlockState(x, y, z, state);
        journal.add("B " + x + " " + y + " " + z + " " + state);
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        return chunk.getHeight(type, x, z);
    }

    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        chunk.markPosForPostProcessing(x, y, z);
        journal.add("M " + x + " " + y + " " + z);
    }

    @Override
    public void scheduleFluidTick(int x, int y, int z, String fluid) {
        chunk.scheduleFluidTick(x, y, z, fluid);
        journal.add("T " + x + " " + y + " " + z + " " + fluid);
    }

    @Override
    public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        chunk.scheduleBlockTick(x, y, z, block, delay);
        journal.add("K " + x + " " + y + " " + z + " " + block + " " + delay);
    }

    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        return chunk.getNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public void forEachStoredBiome(Consumer<String> consumer) {
        chunk.forEachStoredBiome(consumer);
    }
}
