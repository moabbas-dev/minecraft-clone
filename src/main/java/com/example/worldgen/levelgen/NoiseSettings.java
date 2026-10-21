package com.example.worldgen.levelgen;

import com.example.worldgen.DimensionType;

/**
 * Port of {@code net.minecraft.world.level.levelgen.NoiseSettings} (26.3): only {@code min_y} (in
 * [{@code DimensionType.MIN_Y}, {@code MAX_Y}]) and {@code height} (in [0, {@code Y_SIZE}]); in 26.3 the cell size
 * fields are gone (cell sizes are on {@code minecraft:interpolated}). {@code guardY}: {@code min_y + height} at most
 * {@code MAX_Y + 1}, both multiples of 16. {@code clampToHeightAccessor} is not ported (no level height accessor
 * here).
 */
public record NoiseSettings(int minY, int height) {

    /** Validates like vanilla's codec ({@code intRange} checks, then {@code guardY}). */
    public static NoiseSettings create(int minY, int height) {
        if (minY < DimensionType.MIN_Y || minY > DimensionType.MAX_Y) {
            throw new IllegalArgumentException("min_y: value " + minY + " outside of range [" + DimensionType.MIN_Y + ":" + DimensionType.MAX_Y + "]");
        }
        if (height < 0 || height > DimensionType.Y_SIZE) {
            throw new IllegalArgumentException("height: value " + height + " outside of range [0:" + DimensionType.Y_SIZE + "]");
        }
        if (minY + height > DimensionType.MAX_Y + 1) {
            throw new IllegalArgumentException("min_y + height cannot be higher than: " + (DimensionType.MAX_Y + 1));
        }
        if (height % 16 != 0) {
            throw new IllegalArgumentException("height has to be a multiple of 16");
        }
        if (minY % 16 != 0) {
            throw new IllegalArgumentException("min_y has to be a multiple of 16");
        }
        return new NoiseSettings(minY, height);
    }
}
