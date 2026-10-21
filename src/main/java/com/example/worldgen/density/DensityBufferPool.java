package com.example.worldgen.density;

import com.example.worldgen.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool} (26.3): reuses released
 * buffers whose capacity (rounded up to a multiple of 16) is at most twice the request, preferring an exact
 * capacity match, then the smallest larger one; {@link #garbageCollect()} drops buffers older than
 * {@code maxAge} collections.
 */
public class DensityBufferPool implements DensityBufferArena {

    private static final int BUFFER_SIZE_INCREMENT = 16;
    private static final int MAX_REUSE_SIZE_FACTOR = 2;
    private final int maxAge;
    private final List<ScopedDensityBuffer> buffers = new ArrayList<>();

    public DensityBufferPool(int maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public ScopedDensityBuffer acquire(int size) {
        int roundedMinSize = Mth.roundToward(size, BUFFER_SIZE_INCREMENT);
        ScopedDensityBuffer buffer = tryTakeBest(roundedMinSize, roundedMinSize * MAX_REUSE_SIZE_FACTOR);
        if (buffer == null) {
            return new ScopedDensityBuffer(this, roundedMinSize, size);
        }
        buffer.restore(size);
        return buffer;
    }

    private ScopedDensityBuffer tryTakeBest(int minCapacity, int maxCapacity) {
        int bestIndex = -1;
        int bestCapacity = maxCapacity + 1;
        for (int i = buffers.size() - 1; i >= 0; --i) {
            int capacity = buffers.get(i).capacity();
            if (capacity == minCapacity) {
                return buffers.remove(i);
            }
            if (capacity > minCapacity && capacity < bestCapacity) {
                bestIndex = i;
                bestCapacity = capacity;
            }
        }
        return bestIndex == -1 ? null : buffers.remove(bestIndex);
    }

    @Override
    public void release(ScopedDensityBuffer buffer) {
        buffers.add(buffer);
    }

    public void garbageCollect() {
        buffers.removeIf(buffer -> buffer.incrementAge() > maxAge);
    }

    public void clear() {
        buffers.clear();
    }

    int size() {
        return buffers.size();
    }

    public boolean isEmpty() {
        return buffers.isEmpty();
    }
}
