package com.example.worldgen.carver;

import java.util.BitSet;

/**
 * Port of {@code net.minecraft.world.level.chunk.CarvingMask} (26.3): a {@link BitSet} of {@code 256 * height} bits,
 * {@code height = maxY - minY + 1}, index {@code (y - minY) + (z + (x << 4)) * height} (local x, z in 0..15).
 * {@link #visit} walks runs of set bits in index order and reports, per column, {@code visitColumn(x, z, fromY, toY)}
 * with absolute y bounds (inclusive).
 */
public final class CarvingMask implements CarverOutput {

    /** {@code CarvingMask$Visitor}. */
    @FunctionalInterface
    public interface Visitor {
        void visitColumn(int x, int z, int minY, int maxY);
    }

    /** {@code CarvingMask$Filter} (the blender's carving filter, used by {@code applyCarvingMask}). */
    @FunctionalInterface
    public interface Filter {
        boolean test(int x, int y, int z);
    }

    private final int minY;
    private final int maxY;
    private final int height;
    private final BitSet mask;

    public CarvingMask(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY + 1;
        this.mask = new BitSet(256 * height);
    }

    private int getIndex(int x, int y, int z) {
        return y - minY + (z + (x << 4)) * height;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public void carve(int x, int y, int z) {
        mask.set(getIndex(x, y, z));
    }

    /** True when {@code (x, y, z)} (local x, z; absolute y within the range) is carved. */
    public boolean isCarved(int x, int y, int z) {
        return mask.get(getIndex(x, y, z));
    }

    public void visit(Visitor visitor) {
        int start = mask.nextSetBit(0);
        while (start != -1) {
            int end = mask.nextClearBit(start) - 1;
            visitSegment(visitor, start, end);
            start = mask.nextSetBit(end + 1);
        }
    }

    private void visitSegment(Visitor visitor, int start, int end) {
        int firstColumn = start / height;
        int lastColumn = end / height;
        for (int column = firstColumn; column <= lastColumn; column++) {
            int x = column >> 4 & 15;
            int z = column & 15;
            int columnStart = column * height;
            int from = Math.max(start - columnStart, 0) + minY;
            int to = Math.min(end - columnStart, height - 1) + minY;
            visitor.visitColumn(x, z, from, to);
        }
    }

    public boolean isEmpty() {
        return mask.isEmpty();
    }

    /** Number of carved cells (not in vanilla; for tests and statistics). */
    public int cardinality() {
        return mask.cardinality();
    }
}
