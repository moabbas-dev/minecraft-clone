package com.example.worldgen.material;

import com.example.worldgen.level.GenChunk;
import com.example.worldgen.level.StateRef;

import java.util.Arrays;

/**
 * Array-backed {@link GenChunk} for the material tests: {@code WORLD_SURFACE_WG} kept as vanilla's
 * {@code Heightmap.update} does after a {@code setBlockState} (26.3 bytecode: ignore {@code y <= first - 2}; a non-air
 * state at or above {@code first} raises it to {@code y + 1}; removing the top block rescans downwards), primed from the
 * initial fill. Post-processing marks are counted.
 */
final class TestChunk implements GenChunk, FluidPostProcessing {
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final StateRef[] blocks;
    private final int[] heights = new int[256];
    int marks;

    TestChunk(int chunkX, int chunkZ, int minY, int height) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.height = height;
        this.blocks = new StateRef[256 * height];
        Arrays.fill(blocks, StateRef.AIR);
        Arrays.fill(heights, minY);
    }

    private int index(int x, int y, int z) {
        return ((x & 15) * 16 + (z & 15)) * height + (y - minY);
    }

    void fillRaw(int x, int y, int z, StateRef state) {
        blocks[index(x, y, z)] = state;
    }

    void primeHeights() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = minY;
                for (int y = minY + height - 1; y >= minY; y--) {
                    if (!blocks[index(x, y, z)].isAir()) {
                        h = y + 1;
                        break;
                    }
                }
                heights[x * 16 + z] = h;
            }
        }
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
    public int minY() {
        return minY;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public StateRef getState(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    @Override
    public void setState(int x, int y, int z, StateRef state) {
        blocks[index(x, y, z)] = state;
        int column = (x & 15) * 16 + (z & 15);
        int first = heights[column];
        if (y <= first - 2) {
            return;
        }
        if (!state.isAir()) {
            if (y >= first) {
                heights[column] = y + 1;
            }
        } else if (first - 1 == y) {
            for (int j = y - 1; j >= minY; j--) {
                if (!blocks[index(x, j, z)].isAir()) {
                    heights[column] = j + 1;
                    return;
                }
            }
            heights[column] = minY;
        }
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        if (type != HeightmapType.WORLD_SURFACE_WG) {
            throw new IllegalArgumentException(type.toString());
        }
        return heights[(x & 15) * 16 + (z & 15)];
    }

    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        marks++;
    }
}
