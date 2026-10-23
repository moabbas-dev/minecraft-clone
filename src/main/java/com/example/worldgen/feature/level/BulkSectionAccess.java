package com.example.worldgen.feature.level;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;

/**
 * Port of {@code net.minecraft.world.level.chunk.BulkSectionAccess} (26.3) over a {@link GenRegion}:
 * {@code getSection(pos)} is null when {@code level.getSectionIndex(y)} is outside {@code [0, getSectionsCount())}, otherwise
 * the section of {@code level.getChunk(x >> 4, z >> 4)} (any chunk of the region, no write-radius check);
 * {@code getBlockState(pos)} returns {@code Blocks.AIR.defaultBlockState()} for a null section, else the section's state.
 * Section writes ({@code LevelChunkSection.setBlockState(..., false)}) go straight to the chunk, without heightmap updates.
 * The acquire/release section locking has no effect on the result and is not modelled.
 */
public final class BulkSectionAccess {

    private final GenRegion level;

    public BulkSectionAccess(GenRegion level) {
        this.level = level;
    }

    /** {@code getSection(pos) != null}. */
    public boolean hasSection(int y) {
        int sectionIndex = level.getSectionIndex(y);
        return sectionIndex >= 0 && sectionIndex < level.getSectionsCount();
    }

    /** {@code getBlockState(pos)}. */
    public StateRef getBlockState(int x, int y, int z) {
        if (!hasSection(y)) return BlockProperties.AIR;
        return level.getChunk(x >> 4, z >> 4).getSectionBlockState(x, y, z);
    }

    /** {@code getSection(pos).setBlockState(x & 15, y & 15, z & 15, state, false)} (the section must exist). */
    public void setSectionBlockState(int x, int y, int z, StateRef state) {
        if (!hasSection(y)) throw new IllegalArgumentException("no section at y " + y);
        level.getChunk(x >> 4, z >> 4).setSectionBlockState(x, y, z, state);
        level.listener().sectionWrite(x, y, z, state);
    }
}
