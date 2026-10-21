package com.example.worldgen.carver;

/**
 * Port of {@code net.minecraft.world.level.chunk.CarverOutput} (26.3): where carvers record carved cells
 * ({@code carve(localX, y, localZ)}) and the y range they may touch.
 */
public interface CarverOutput {

    void carve(int x, int y, int z);

    int minY();

    int maxY();
}
