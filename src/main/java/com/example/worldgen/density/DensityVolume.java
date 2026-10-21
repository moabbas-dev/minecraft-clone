package com.example.worldgen.density;

import com.example.worldgen.Mth;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityVolume} (26.3): a box of sample
 * points {@code (minBlock + index * stepBlock)} per axis. Buffers are laid out y-fastest, then x, then z:
 * {@code index = y + (x + z * sizeX) * sizeY}. {@code intersects(BoundingBox)} is not ported (no bounding box
 * type yet).
 */
public record DensityVolume(int sizeX, int sizeY, int sizeZ, int minBlockX, int minBlockY, int minBlockZ,
                            int stepBlockX, int stepBlockY, int stepBlockZ) {

    public static final int NO_BLOCK = -1;

    public DensityVolume {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Size must be positive, was: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (stepBlockX <= 0 || stepBlockY <= 0 || stepBlockZ <= 0) {
            throw new IllegalArgumentException("Step must be positive, was: " + stepBlockX + "; " + stepBlockY + "; " + stepBlockZ);
        }
    }

    public DensityVolume(int sizeX, int sizeY, int sizeZ, int minBlockX, int minBlockY, int minBlockZ) {
        this(sizeX, sizeY, sizeZ, minBlockX, minBlockY, minBlockZ, 1, 1, 1);
    }

    public int indexUnchecked(int indexX, int indexY, int indexZ) {
        return indexY + (indexX + indexZ * sizeX) * sizeY;
    }

    public int blockX(int x) {
        return minBlockX + x * stepBlockX;
    }

    public int blockY(int y) {
        return minBlockY + y * stepBlockY;
    }

    public int blockZ(int z) {
        return minBlockZ + z * stepBlockZ;
    }

    public int maxBlockX() {
        return minBlockX + sizeX * stepBlockX - 1;
    }

    public int maxBlockY() {
        return minBlockY + sizeY * stepBlockY - 1;
    }

    public int maxBlockZ() {
        return minBlockZ + sizeZ * stepBlockZ - 1;
    }

    public int size() {
        return sizeX * sizeY * sizeZ;
    }

    private boolean containsBlockRelative(int relativeX, int relativeY, int relativeZ) {
        return relativeX >= 0 && relativeY >= 0 && relativeZ >= 0
                && relativeX < sizeX * stepBlockX && relativeY < sizeY * stepBlockY && relativeZ < sizeZ * stepBlockZ
                && Math.floorMod(relativeX, stepBlockX) == 0 && Math.floorMod(relativeY, stepBlockY) == 0
                && Math.floorMod(relativeZ, stepBlockZ) == 0;
    }

    /** The buffer index of a block on this volume's sample grid, or {@link #NO_BLOCK}. */
    public int indexOfBlock(int blockX, int blockY, int blockZ) {
        int relativeX = blockX - minBlockX;
        int relativeY = blockY - minBlockY;
        int relativeZ = blockZ - minBlockZ;
        if (stepBlockX == 1 && stepBlockY == 1 && stepBlockZ == 1) {
            if (relativeX >= 0 && relativeY >= 0 && relativeZ >= 0 && relativeX < sizeX && relativeY < sizeY && relativeZ < sizeZ) {
                return indexUnchecked(relativeX, relativeY, relativeZ);
            }
        } else if (containsBlockRelative(relativeX, relativeY, relativeZ)) {
            return indexUnchecked(Mth.floorDiv(relativeX, stepBlockX), Mth.floorDiv(relativeY, stepBlockY),
                    Mth.floorDiv(relativeZ, stepBlockZ));
        }
        return NO_BLOCK;
    }
}
