package com.example.worldgen;

/**
 * The part of {@code net.minecraft.world.level.ChunkPos} (26.3, a record of {@code x}, {@code z}) that world
 * generation uses: {@code getMinBlockX = SectionPos.sectionToBlockCoord(x) = x << 4},
 * {@code getBlockX(o) = (x << 4) + o}, {@code getMiddleBlockX = getBlockX(8)} (same for z).
 */
public record ChunkPos(int x, int z) {

    public int getMinBlockX() {
        return x << 4;
    }

    public int getMinBlockZ() {
        return z << 4;
    }

    public int getBlockX(int offset) {
        return (x << 4) + offset;
    }

    public int getBlockZ(int offset) {
        return (z << 4) + offset;
    }

    public int getMiddleBlockX() {
        return getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return getBlockZ(8);
    }
}
