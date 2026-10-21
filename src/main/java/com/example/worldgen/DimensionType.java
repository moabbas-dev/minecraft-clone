package com.example.worldgen;

/**
 * The world-height limits of {@code net.minecraft.world.level.dimension.DimensionType} (26.3) used by the
 * worldgen codecs, computed exactly like its static initialiser: {@code BITS_FOR_Y = BlockPos.PACKED_Y_LENGTH},
 * {@code Y_SIZE = (1 << BITS_FOR_Y) - 32}, {@code MAX_Y = (Y_SIZE >> 1) - 1}, {@code MIN_Y = MAX_Y - Y_SIZE + 1}
 * (that is 4064, 2031 and -2032).
 */
public final class DimensionType {

    public static final int BITS_FOR_Y = BlockPos.PACKED_Y_LENGTH;
    public static final int Y_SIZE = (1 << BITS_FOR_Y) - 32;
    public static final int MAX_Y = (Y_SIZE >> 1) - 1;
    public static final int MIN_Y = MAX_Y - Y_SIZE + 1;

    private DimensionType() {
    }
}
