package com.example.worldgen;

/**
 * The block-position long packing of {@code net.minecraft.core.BlockPos} (26.3), as read from its
 * static initialiser: {@code PACKED_HORIZONTAL_LENGTH = 1 + log2(smallestEncompassingPowerOfTwo(30000000)) = 26},
 * {@code PACKED_Y_LENGTH = 64 - 2 * 26 = 12}, masks {@code (1L << length) - 1}, {@code Z_OFFSET = PACKED_Y_LENGTH}
 * and {@code X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH}. Only {@code asLong(int, int, int)} is ported
 * (the density sampler cache keys its single-value slot with it).
 */
public final class BlockPos {

    public static final int PACKED_HORIZONTAL_LENGTH = 26;
    public static final int PACKED_Y_LENGTH = 64 - 2 * PACKED_HORIZONTAL_LENGTH;
    private static final long PACKED_X_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;

    private BlockPos() {
    }

    /** {@code BlockPos.asLong(int, int, int)}. */
    public static long asLong(int x, int y, int z) {
        long node = 0L;
        node |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        node |= ((long) y & PACKED_Y_MASK) << 0;
        node |= ((long) z & PACKED_Z_MASK) << Z_OFFSET;
        return node;
    }
}
